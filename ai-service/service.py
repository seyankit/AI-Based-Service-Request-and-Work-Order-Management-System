"""Local, advisory request analysis. No third-party packages or trained model required."""
from __future__ import annotations

from collections import Counter
from datetime import datetime, timezone
from http.server import BaseHTTPRequestHandler, ThreadingHTTPServer
import hmac
import json
import math
import os
import re
import time

VERSION = "1.0.0"
MODEL = "HTC transparent service rules"
MAX_BODY = 2_097_152
PRIORITIES = {"Low", "Medium", "High", "Urgent"}
CLOSED = {"Rejected", "Completed", "Closed", "Cancelled", "Duplicate"}
STOP = {"the", "and", "for", "with", "this", "that", "from", "please", "need", "needs", "was", "are", "has", "have", "our", "not", "but", "can"}
SYNONYMS = {
    "IT_COMPUTER": "computer laptop desktop printer software keyboard mouse monitor account login application toner",
    "INTERNET_NETWORK": "internet network wifi wi-fi router ethernet connectivity lan bandwidth connection",
    "ELECTRICAL": "electrical electricity wiring outlet power lighting bulb circuit voltage spark shock",
    "FACILITIES": "plumbing toilet faucet door window roof ceiling chair desk furniture building leak pipe",
    "EQUIPMENT": "equipment projector aircon air conditioner fan machine compressor appliance",
    "MAINTENANCE": "maintenance preventive cleaning servicing inspection repaint repair",
}


def words(value):
    return {w for w in re.findall(r"[a-z0-9]+", str(value).lower()) if len(w) > 2 and w not in STOP}


def text(value, name, maximum, required=True):
    if not isinstance(value, str) or len(value) > maximum or (required and not value.strip()):
        raise ValueError(f"Invalid {name}.")
    return value.strip()


def positive(value, name):
    if type(value) is not int or not 0 < value <= 9_007_199_254_740_991:
        raise ValueError(f"Invalid {name}.")
    return value


def parsed_date(value):
    if not isinstance(value, str) or len(value) > 40:
        raise ValueError("Invalid timestamp.")
    try:
        result = datetime.fromisoformat(value.replace("Z", "+00:00"))
        return result.replace(tzinfo=timezone.utc) if result.tzinfo is None else result
    except ValueError as error:
        raise ValueError("Invalid timestamp.") from error


def validate_request(request):
    if not isinstance(request, dict):
        raise ValueError("Request must be an object.")
    positive(request.get("requestId"), "request ID")
    for field, maximum in (("title", 150), ("description", 5000), ("location", 120)):
        text(request.get(field), field, maximum)
    if request.get("requestedCategoryId") is not None:
        positive(request["requestedCategoryId"], "requested category")
    parsed_date(request.get("createdAt"))


def validate_payload(payload):
    if not isinstance(payload, dict):
        raise ValueError("The JSON body must be an object.")
    validate_request(payload.get("request"))
    categories = payload.get("categories")
    if not isinstance(categories, list) or not 1 <= len(categories) <= 255:
        raise ValueError("Supply active database categories.")
    ids = set()
    for category in categories:
        if not isinstance(category, dict):
            raise ValueError("Invalid category.")
        category_id = positive(category.get("categoryId"), "category ID")
        if category_id in ids:
            raise ValueError("Duplicate category ID.")
        ids.add(category_id)
        text(category.get("categoryCode"), "category code", 40)
        text(category.get("categoryName"), "category name", 100)
        text(category.get("categoryDescription") or "", "category description", 255, False)
    candidates = payload.get("candidates", [])
    if not isinstance(candidates, list) or len(candidates) > 200:
        raise ValueError("At most 200 duplicate candidates are allowed.")
    seen = set()
    for candidate in candidates:
        validate_request(candidate)
        if candidate["requestId"] in seen:
            raise ValueError("Duplicate candidate ID.")
        seen.add(candidate["requestId"])
        text(candidate.get("status"), "candidate status", 30)


def classify(request, categories):
    source = words(request["title"] + " " + request["description"])
    scores = []
    for category in categories:
        indicators = words(" ".join((category["categoryName"], category.get("categoryDescription") or "", SYNONYMS.get(category["categoryCode"], ""))))
        matches = source & indicators
        score = len(matches) + 0.25 * len(words(request["title"]) & indicators)
        scores.append((score, category["categoryId"] == request.get("requestedCategoryId"), -category["categoryId"], category, sorted(matches)))
    scores.sort(key=lambda row: row[:3], reverse=True)
    score, _, _, category, matches = scores[0]
    if not score:
        return category["categoryId"], 0.15, "No category indicators matched; low-confidence suggestion requires human classification."
    competing = scores[1][0] if len(scores) > 1 else 0
    confidence = round(min(0.90, 0.40 + min(score, 5) * 0.07 + max(0, score - competing) * 0.025), 5)
    return category["categoryId"], confidence, "Matched category indicators: " + ", ".join(matches[:20]) + ". Scores are heuristic, not calibrated probabilities."


def positive_indicators(source, expressions):
    matches = []
    for expression in expressions:
        for match in re.finditer(r"\b(?:" + expression + r")\b", source):
            before = source[max(0, match.start() - 24):match.start()]
            if not re.search(r"\b(?:no|without|not|never)\s+(?:\w+\s+){0,2}$", before):
                matches.append(match.group())
                break
    return matches


def priority(request):
    source = (request["title"] + " " + request["description"] + " " + request["location"]).lower()
    safety = positive_indicators(source, ("fire", "smoke", "sparks?", "electric shock", "exposed wir(?:e|es|ing)", "injur(?:y|ies)", "flood(?:ing)?", "gas leak", "immediate danger"))
    if safety:
        return "Urgent", 0.90, "Potential safety impact: " + ", ".join(safety) + ". Human safety assessment is required."
    interruption = positive_indicators(source, ("outage", "no internet", "cannot access", "not working", "unavailable", "all users", "entire (?:building|campus|department)", "multiple (?:rooms|users|classes)", "exam(?:ination)?", "server down"))
    if interruption:
        return "High", 0.75, "Service interruption or broad impact indicators: " + ", ".join(interruption) + "."
    minor = positive_indicators(source, ("cosmetic", "routine", "preventive", "minor", "scheduled inspection"))
    if minor:
        return "Low", 0.65, "Routine or limited impact indicators: " + ", ".join(minor) + "."
    return "Medium", 0.45, "Insufficient explicit safety or broad interruption indicators; standard review recommended."


def jaccard(left, right):
    return len(left & right) / len(left | right) if left or right else 0.0


def duplicates(request, candidates, category_id):
    source = words(request["title"] + " " + request["description"])
    location = words(request["location"])
    created = parsed_date(request["createdAt"])
    matches = []
    for candidate in candidates:
        if candidate["requestId"] == request["requestId"] or candidate["status"] in CLOSED:
            continue
        age = abs((created - parsed_date(candidate["createdAt"])).total_seconds()) / 86400
        if age > 90:
            continue
        similarity = jaccard(source, words(candidate["title"] + " " + candidate["description"]))
        place = jaccard(location, words(candidate["location"]))
        # Distinct room/equipment numbers should not be treated as the same location.
        incoming_numbers = set(re.findall(r"\d+", request["location"]))
        existing_numbers = set(re.findall(r"\d+", candidate["location"]))
        if incoming_numbers and existing_numbers and incoming_numbers != existing_numbers:
            place = 0.0
        same_category = category_id == candidate.get("requestedCategoryId")
        score = 0.65 * similarity + 0.20 * place + 0.10 * same_category + 0.05 * max(0, 1 - age / 90)
        if similarity >= 0.30 and score >= 0.58 and (place >= 0.5 or similarity >= 0.8):
            matches.append({"requestId": candidate["requestId"], "similarity": round(score, 5), "explanation": f"Text overlap {similarity:.0%}; location overlap {place:.0%}; reported {age:.1f} days apart. Review before linking."})
    return sorted(matches, key=lambda item: (-item["similarity"], item["requestId"]))[:10]


def analyze(payload):
    started = time.perf_counter()
    validate_payload(payload)
    request = payload["request"]
    category_id, category_confidence, category_explanation = classify(request, payload["categories"])
    recommended_priority, priority_confidence, priority_explanation = priority(request)
    candidates = duplicates(request, payload.get("candidates", []), category_id)
    best = candidates[0] if candidates else None
    return {
        "analysisStatus": "Completed", "recommendationMethod": "Rule-Based",
        "recommendedCategoryId": category_id, "categoryConfidence": category_confidence,
        "recommendedPriority": recommended_priority, "priorityConfidence": priority_confidence,
        "possibleDuplicateRequestId": best["requestId"] if best else None,
        "duplicateSimilarity": best["similarity"] if best else None, "duplicateThreshold": 0.58,
        "duplicateCandidates": candidates, "categoryExplanation": category_explanation,
        "priorityExplanation": priority_explanation,
        "duplicateExplanation": best["explanation"] if best else "No candidate exceeded the threshold within the supplied recent open requests.",
        "analysisMessage": "Advisory rule-based analysis; authorized personnel make final decisions.",
        "modelName": MODEL, "modelVersion": VERSION,
        "generatedAt": datetime.now(timezone.utc).isoformat(),
        "processingTimeMs": round((time.perf_counter() - started) * 1000),
    }


def insights(payload):
    """Summarize supplied real records only; this service never fabricates samples."""
    records = payload.get("records") if isinstance(payload, dict) else None
    if not isinstance(records, list) or len(records) > 2000:
        raise ValueError("Supply at most 2000 actual records.")
    for item in records:
        validate_request(item)
        text(item.get("status"), "status", 30)
        if item.get("priority") not in PRIORITIES:
            raise ValueError("Invalid priority.")
    durations = []
    for item in records:
        if item.get("completedAt"):
            hours = (parsed_date(item["completedAt"]) - parsed_date(item["createdAt"])).total_seconds() / 3600
            if hours >= 0:
                durations.append(hours)
    return {"recordCount": len(records), "byCategory": dict(Counter(str(r.get("requestedCategoryId")) for r in records)),
            "byLocation": dict(Counter(r["location"] for r in records).most_common(20)),
            "byPriority": dict(Counter(r["priority"] for r in records)),
            "byMonth": dict(sorted(Counter(parsed_date(r["createdAt"]).strftime("%Y-%m") for r in records).items())),
            "openWorkload": sum(r["status"] not in CLOSED for r in records),
            "averageCompletionHours": round(sum(durations) / len(durations), 2) if durations else None,
            "modelVersion": VERSION, "scope": "Supplied stored records only"}


class Handler(BaseHTTPRequestHandler):
    server_version = "HTC-Advisory/1"

    def setup(self):
        super().setup()
        self.connection.settimeout(5)

    def log_message(self, format, *args):
        # Payloads, request paths, tokens and personal request text are never logged.
        pass

    def reply(self, status, value):
        data = json.dumps(value, allow_nan=False, separators=(",", ":")).encode("utf-8")
        self.send_response(status)
        self.send_header("Content-Type", "application/json; charset=utf-8")
        self.send_header("Content-Length", str(len(data)))
        self.send_header("Cache-Control", "no-store")
        self.send_header("X-Content-Type-Options", "nosniff")
        self.end_headers()
        try:
            self.wfile.write(data)
        except (BrokenPipeError, ConnectionResetError):
            pass

    def do_GET(self):
        self.reply(200, {"success": True, "service": MODEL, "version": VERSION}) if self.path == "/health" else self.reply(404, {"success": False, "message": "Not found."})

    def do_POST(self):
        if not hmac.compare_digest(self.headers.get("Authorization", ""), "Bearer " + self.server.api_token):
            self.reply(401, {"success": False, "message": "Service authentication required."})
            return
        if self.path not in {"/analyze", "/insights"}:
            self.reply(404, {"success": False, "message": "Not found."})
            return
        if self.headers.get_content_type() != "application/json":
            self.reply(415, {"success": False, "message": "Content-Type must be application/json."})
            return
        try:
            length = int(self.headers.get("Content-Length", "0"))
        except ValueError:
            length = 0
        if self.headers.get("Transfer-Encoding") or not 0 < length <= MAX_BODY:
            self.reply(413, {"success": False, "message": "A bounded JSON body is required."})
            return
        try:
            raw = self.rfile.read(length)
            if len(raw) != length:
                raise ValueError("Incomplete request body.")
            payload = json.loads(raw, parse_constant=lambda value: (_ for _ in ()).throw(ValueError("Non-finite JSON number.")))
            result = analyze(payload) if self.path == "/analyze" else insights(payload)
            self.reply(200, {"success": True, "data": result})
        except (ValueError, UnicodeDecodeError, TypeError, OverflowError):
            self.reply(400, {"success": False, "message": "Invalid analysis payload."})
        except TimeoutError:
            self.reply(408, {"success": False, "message": "Request timed out."})


class Server(ThreadingHTTPServer):
    daemon_threads = True
    request_queue_size = 32

    def __init__(self, address, token):
        if len(token) < 32:
            raise ValueError("HTC_AI_TOKEN must contain at least 32 characters.")
        self.api_token = token
        super().__init__(address, Handler)


if __name__ == "__main__":
    host = os.environ.get("HTC_AI_HOST", "127.0.0.1")
    if host not in {"127.0.0.1", "localhost", "::1"}:
        raise SystemExit("The advisory service must bind to a loopback interface.")
    server = Server((host, int(os.environ.get("HTC_AI_PORT", "8091"))), os.environ.get("HTC_AI_TOKEN", ""))
    print(f"HTC advisory service {VERSION} listening on loopback port {server.server_port}", flush=True)
    try:
        server.serve_forever()
    except KeyboardInterrupt:
        pass
    finally:
        server.server_close()
