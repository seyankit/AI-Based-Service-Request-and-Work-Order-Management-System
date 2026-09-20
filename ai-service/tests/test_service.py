import copy
import importlib.util
import pathlib
import threading
import unittest
import urllib.error
import urllib.request


SERVICE_PATH = pathlib.Path(__file__).resolve().parents[1] / "service.py"
SPEC = importlib.util.spec_from_file_location("htc_ai_service", SERVICE_PATH)
SERVICE = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(SERVICE)


def payload():
    return {
        "request": {
            "requestId": 7,
            "title": "Internet outage",
            "description": "The classroom internet is unavailable.",
            "location": "Room 10",
            "requestedCategoryId": 1,
            "createdAt": "2026-09-21T00:00:00Z",
        },
        "categories": [{"categoryId": 1, "categoryCode": "INTERNET_NETWORK",
                        "categoryName": "Internet & Network", "categoryDescription": "Network"}],
        "candidates": [{"requestId": 8, "title": "Internet outage", "description": "Internet unavailable.",
                        "location": "Room 10", "requestedCategoryId": 1,
                        "createdAt": "2026-09-20T00:00:00Z", "status": "Submitted"}],
    }


class AdvisoryServiceTest(unittest.TestCase):
    def test_analysis_is_deterministic_and_advisory(self):
        source = payload()
        before = copy.deepcopy(source)
        first = SERVICE.analyze(source)
        second = SERVICE.analyze(source)
        self.assertEqual(first["recommendedCategoryId"], second["recommendedCategoryId"])
        self.assertIn(first["recommendedPriority"], SERVICE.PRIORITIES)
        self.assertEqual(source, before)

    def test_invalid_payload_is_rejected(self):
        invalid = payload()
        invalid["request"]["title"] = ""
        with self.assertRaises(ValueError):
            SERVICE.analyze(invalid)

    def test_http_analysis_requires_bearer_authentication(self):
        server = SERVICE.Server(("127.0.0.1", 0), "x" * 32)
        worker = threading.Thread(target=server.handle_request)
        worker.start()
        try:
            request = urllib.request.Request(
                f"http://127.0.0.1:{server.server_port}/analyze", data=b"{}",
                headers={"Content-Type": "application/json"}, method="POST")
            with self.assertRaises(urllib.error.HTTPError) as error:
                urllib.request.urlopen(request, timeout=2)
            self.assertEqual(401, error.exception.code)
        finally:
            worker.join(timeout=2)
            server.server_close()


if __name__ == "__main__":
    unittest.main()
