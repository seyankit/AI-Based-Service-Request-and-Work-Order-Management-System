package ph.edu.htcgsc.serviceportal.service;

import com.google.gson.Gson;
import com.google.gson.JsonArray;
import com.google.gson.JsonElement;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Boundary client for the local advisory service.  It deliberately has no
 * access to browser sessions, database credentials, or workflow controls.
 */
public final class AiServiceClient {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration REQUEST_TIMEOUT = Duration.ofSeconds(4);
    private static final int MAXIMUM_PROCESSING_TIME_MS = 60_000;
    private static final int MAXIMUM_DUPLICATE_RESULTS = 10;

    public record Configuration(String baseUrl, String token) {
        public static Configuration fromEnvironment() {
            return new Configuration(
                    setting("htc.ai.baseUrl", "HTC_AI_BASE_URL", "http://127.0.0.1:8091"),
                    setting("htc.ai.token", "HTC_AI_TOKEN", "")
            );
        }
    }

    public record Category(long categoryId, String categoryCode, String categoryName,
                           String categoryDescription) {
    }

    public record Candidate(long requestId, String requestNumber, String title,
                            String description, String location,
                            Integer requestedCategoryId, String createdAt,
                            String status) {
    }

    public record AnalysisInput(long requestId, String title, String description,
                                String location, Integer requestedCategoryId,
                                String createdAt, List<Category> categories,
                                List<Candidate> candidates) {
    }

    public record DuplicateCandidate(long requestId, BigDecimal similarity,
                                     String explanation) {
    }

    public record AnalysisResult(long recommendedCategoryId,
                                 BigDecimal categoryConfidence,
                                 String recommendedPriority,
                                 BigDecimal priorityConfidence,
                                 Long possibleDuplicateRequestId,
                                 BigDecimal duplicateSimilarity,
                                 BigDecimal duplicateThreshold,
                                 List<DuplicateCandidate> duplicateCandidates,
                                 String categoryExplanation,
                                 String priorityExplanation,
                                 String duplicateExplanation,
                                 String analysisMessage,
                                 String recommendationMethod,
                                 String modelName,
                                 String modelVersion,
                                 int processingTimeMs) {
    }

    public record RawResponse(int statusCode, String body) {
    }

    @FunctionalInterface
    public interface Transport {
        RawResponse post(URI uri, String token, String body)
                throws IOException, InterruptedException;
    }

    public static class UnavailableException extends Exception {
        public UnavailableException(String message, Throwable cause) {
            super(message, cause);
        }

        public UnavailableException(String message) {
            super(message);
        }
    }

    public static class InvalidResponseException extends Exception {
        public InvalidResponseException(String message) {
            super(message);
        }
    }

    private final Configuration configuration;
    private final Transport transport;
    private final Gson gson = new Gson();

    public AiServiceClient() {
        this(Configuration.fromEnvironment(), new HttpTransport());
    }

    public AiServiceClient(Configuration configuration, Transport transport) {
        this.configuration = configuration;
        this.transport = transport;
    }

    public AnalysisResult analyze(AnalysisInput input)
            throws UnavailableException, InvalidResponseException {

        if (configuration.token() == null || configuration.token().isBlank()) {
            throw new UnavailableException("Advisory service is not configured.");
        }

        URI endpoint;

        try {
            endpoint = URI.create(normalizeBaseUrl(configuration.baseUrl()) + "/analyze");
        } catch (IllegalArgumentException exception) {
            throw new UnavailableException("Advisory service is not configured.", exception);
        }

        RawResponse response;

        try {
            response = transport.post(endpoint, configuration.token(), gson.toJson(toPayload(input)));
        } catch (IOException exception) {
            throw new UnavailableException("Advisory service is unavailable.", exception);
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            throw new UnavailableException("Advisory service request was interrupted.", exception);
        }

        if (response.statusCode() < 200 || response.statusCode() >= 300) {
            throw new UnavailableException("Advisory service is unavailable.");
        }

        return parseAndValidate(response.body(), input);
    }

    private Map<String, Object> toPayload(AnalysisInput input) {
        Map<String, Object> payload = new HashMap<>();
        Map<String, Object> request = new HashMap<>();
        request.put("requestId", input.requestId());
        request.put("title", input.title());
        request.put("description", input.description());
        request.put("location", input.location());
        request.put("requestedCategoryId", input.requestedCategoryId());
        request.put("createdAt", input.createdAt());
        payload.put("request", request);

        List<Map<String, Object>> categories = new ArrayList<>();
        for (Category category : input.categories()) {
            Map<String, Object> item = new HashMap<>();
            item.put("categoryId", category.categoryId());
            item.put("categoryCode", category.categoryCode());
            item.put("categoryName", category.categoryName());
            item.put("categoryDescription", category.categoryDescription());
            categories.add(item);
        }
        payload.put("categories", categories);

        List<Map<String, Object>> candidates = new ArrayList<>();
        for (Candidate candidate : input.candidates()) {
            Map<String, Object> item = new HashMap<>();
            item.put("requestId", candidate.requestId());
            item.put("title", candidate.title());
            item.put("description", candidate.description());
            item.put("location", candidate.location());
            item.put("requestedCategoryId", candidate.requestedCategoryId());
            item.put("createdAt", candidate.createdAt());
            item.put("status", candidate.status());
            candidates.add(item);
        }
        payload.put("candidates", candidates);
        return payload;
    }

    private AnalysisResult parseAndValidate(String body, AnalysisInput input)
            throws InvalidResponseException {

        try {
            JsonObject root = JsonParser.parseString(body).getAsJsonObject();
            if (!requiredBoolean(root, "success")) {
                throw new InvalidResponseException("Advisory response was unsuccessful.");
            }
            JsonObject data = requiredObject(root, "data");
            if (!"Completed".equals(requiredString(data, "analysisStatus", 20))) {
                throw new InvalidResponseException("Advisory response was incomplete.");
            }

            long categoryId = requiredPositiveLong(data, "recommendedCategoryId");
            Map<Long, Category> categories = new HashMap<>();
            for (Category category : input.categories()) {
                categories.put(category.categoryId(), category);
            }
            if (!categories.containsKey(categoryId)) {
                throw new InvalidResponseException("Advisory response selected an inactive category.");
            }

            String method = requiredString(data, "recommendationMethod", 30);
            if (!"Rule-Based".equals(method)) {
                throw new InvalidResponseException("Advisory response used an unsupported method.");
            }

            String priority = requiredString(data, "recommendedPriority", 20);
            if (!Set.of("Low", "Medium", "High", "Urgent").contains(priority)) {
                throw new InvalidResponseException("Advisory response returned an invalid priority.");
            }

            BigDecimal categoryConfidence = score(data, "categoryConfidence");
            BigDecimal priorityConfidence = score(data, "priorityConfidence");
            BigDecimal threshold = score(data, "duplicateThreshold");
            List<DuplicateCandidate> candidates = duplicateCandidates(data, input, threshold);
            Long duplicateId = nullablePositiveLong(data, "possibleDuplicateRequestId");
            BigDecimal duplicateSimilarity = nullableScore(data, "duplicateSimilarity");

            if (candidates.isEmpty()) {
                if (duplicateId != null || duplicateSimilarity != null) {
                    throw new InvalidResponseException("Advisory response has an inconsistent duplicate result.");
                }
            } else {
                DuplicateCandidate best = candidates.get(0);
                if (duplicateId == null || duplicateSimilarity == null
                        || duplicateId != best.requestId()
                        || duplicateSimilarity.compareTo(best.similarity()) != 0) {
                    throw new InvalidResponseException("Advisory response has an inconsistent duplicate result.");
                }
            }

            String generatedAt = requiredString(data, "generatedAt", 40);
            OffsetDateTime.parse(generatedAt.replace("Z", "+00:00"));

            return new AnalysisResult(
                    categoryId,
                    categoryConfidence,
                    priority,
                    priorityConfidence,
                    duplicateId,
                    duplicateSimilarity,
                    threshold,
                    candidates,
                    requiredString(data, "categoryExplanation", 1000),
                    requiredString(data, "priorityExplanation", 1000),
                    requiredString(data, "duplicateExplanation", 1000),
                    requiredString(data, "analysisMessage", 255),
                    method,
                    requiredString(data, "modelName", 100),
                    requiredString(data, "modelVersion", 50),
                    requiredInt(data, "processingTimeMs", 0, MAXIMUM_PROCESSING_TIME_MS)
            );
        } catch (InvalidResponseException exception) {
            throw exception;
        } catch (Exception exception) {
            throw new InvalidResponseException("Advisory response was invalid.");
        }
    }

    private List<DuplicateCandidate> duplicateCandidates(JsonObject data, AnalysisInput input,
                                                          BigDecimal threshold)
            throws InvalidResponseException {
        JsonArray values = requiredArray(data, "duplicateCandidates");
        if (values.size() > MAXIMUM_DUPLICATE_RESULTS) {
            throw new InvalidResponseException("Advisory response returned too many duplicate candidates.");
        }
        Set<Long> suppliedIds = new HashSet<>();
        for (Candidate candidate : input.candidates()) {
            suppliedIds.add(candidate.requestId());
        }
        Set<Long> seen = new HashSet<>();
        List<DuplicateCandidate> results = new ArrayList<>();
        BigDecimal previous = null;
        for (JsonElement value : values) {
            if (!value.isJsonObject()) {
                throw new InvalidResponseException("Advisory response contains an invalid duplicate candidate.");
            }
            JsonObject item = value.getAsJsonObject();
            long id = requiredPositiveLong(item, "requestId");
            BigDecimal similarity = score(item, "similarity");
            if (id == input.requestId() || !suppliedIds.contains(id) || !seen.add(id)
                    || similarity.compareTo(threshold) < 0
                    || (previous != null && previous.compareTo(similarity) < 0)) {
                throw new InvalidResponseException("Advisory response contains an invalid duplicate candidate.");
            }
            previous = similarity;
            results.add(new DuplicateCandidate(id, similarity, requiredString(item, "explanation", 1000)));
        }
        return List.copyOf(results);
    }

    private static String setting(String propertyName, String environmentName, String defaultValue) {
        String property = System.getProperty(propertyName);
        if (property != null && !property.isBlank()) {
            return property.trim();
        }
        String environment = System.getenv(environmentName);
        return environment == null || environment.isBlank() ? defaultValue : environment.trim();
    }

    private static String normalizeBaseUrl(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Missing base URL");
        }
        String normalized = value.trim().replaceAll("/+$", "");
        URI uri = URI.create(normalized);
        if (!"http".equalsIgnoreCase(uri.getScheme()) && !"https".equalsIgnoreCase(uri.getScheme())) {
            throw new IllegalArgumentException("Unsupported base URL");
        }
        String host = uri.getHost();
        if (host == null || !("127.0.0.1".equals(host)
                || "localhost".equalsIgnoreCase(host)
                || "::1".equals(host))) {
            throw new IllegalArgumentException("Advisory service must use loopback.");
        }
        return normalized;
    }

    private static JsonObject requiredObject(JsonObject object, String name) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonObject()) throw new InvalidResponseException("Missing advisory field.");
        return value.getAsJsonObject();
    }

    private static JsonArray requiredArray(JsonObject object, String name) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonArray()) throw new InvalidResponseException("Missing advisory field.");
        return value.getAsJsonArray();
    }

    private static boolean requiredBoolean(JsonObject object, String name) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isBoolean()) throw new InvalidResponseException("Missing advisory field.");
        return value.getAsBoolean();
    }

    private static String requiredString(JsonObject object, String name, int maximum) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isString()) throw new InvalidResponseException("Missing advisory field.");
        String result = value.getAsString().trim();
        if (result.isEmpty() || result.length() > maximum) throw new InvalidResponseException("Invalid advisory field.");
        return result;
    }

    private static long requiredPositiveLong(JsonObject object, String name) throws InvalidResponseException {
        Long value = nullablePositiveLong(object, name);
        if (value == null) throw new InvalidResponseException("Invalid advisory field.");
        return value;
    }

    private static Long nullablePositiveLong(JsonObject object, String name) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new InvalidResponseException("Invalid advisory field.");
        try {
            long result = value.getAsLong();
            if (result <= 0 || new BigDecimal(value.getAsString()).compareTo(BigDecimal.valueOf(result)) != 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) {
            throw new InvalidResponseException("Invalid advisory field.");
        }
    }

    private static BigDecimal score(JsonObject object, String name) throws InvalidResponseException {
        BigDecimal value = nullableScore(object, name);
        if (value == null) throw new InvalidResponseException("Invalid advisory score.");
        return value;
    }

    private static BigDecimal nullableScore(JsonObject object, String name) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || value.isJsonNull()) return null;
        if (!value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new InvalidResponseException("Invalid advisory score.");
        try {
            BigDecimal result = new BigDecimal(value.getAsString());
            if (result.compareTo(BigDecimal.ZERO) < 0 || result.compareTo(BigDecimal.ONE) > 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) {
            throw new InvalidResponseException("Invalid advisory score.");
        }
    }

    private static int requiredInt(JsonObject object, String name, int minimum, int maximum) throws InvalidResponseException {
        long value = requiredPositiveLongOrZero(object, name);
        if (value < minimum || value > maximum) throw new InvalidResponseException("Invalid advisory field.");
        return (int) value;
    }

    private static long requiredPositiveLongOrZero(JsonObject object, String name) throws InvalidResponseException {
        JsonElement value = object.get(name);
        if (value == null || !value.isJsonPrimitive() || !value.getAsJsonPrimitive().isNumber()) throw new InvalidResponseException("Invalid advisory field.");
        try {
            long result = value.getAsLong();
            if (result < 0 || new BigDecimal(value.getAsString()).compareTo(BigDecimal.valueOf(result)) != 0) throw new NumberFormatException();
            return result;
        } catch (RuntimeException exception) {
            throw new InvalidResponseException("Invalid advisory field.");
        }
    }

    private static final class HttpTransport implements Transport {
        private final HttpClient client = HttpClient.newBuilder().connectTimeout(CONNECT_TIMEOUT).build();

        @Override
        public RawResponse post(URI uri, String token, String body) throws IOException, InterruptedException {
            HttpRequest request = HttpRequest.newBuilder(uri)
                    .timeout(REQUEST_TIMEOUT)
                    .header("Authorization", "Bearer " + token)
                    .header("Content-Type", "application/json")
                    .header("Accept", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            return new RawResponse(response.statusCode(), response.body());
        }
    }
}
