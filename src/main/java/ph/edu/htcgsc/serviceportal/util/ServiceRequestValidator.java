package ph.edu.htcgsc.serviceportal.util;

import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

public final class ServiceRequestValidator {

    private static final ZoneId PORTAL_TIME_ZONE =
            ZoneId.of("Asia/Manila");

    private static final int MINIMUM_TITLE_LENGTH = 5;
    private static final int MAXIMUM_TITLE_LENGTH = 150;

    private static final int MINIMUM_DESCRIPTION_LENGTH = 15;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 5000;

    private static final int MINIMUM_LOCATION_LENGTH = 2;
    private static final int MAXIMUM_LOCATION_LENGTH = 120;

    private ServiceRequestValidator() {
    }

    /*
     * Normalizes permitted values and returns field-specific errors.
     * An empty result means validation succeeded.
     */
    public static Map<String, String> validateAndNormalize(
            CreateServiceRequestRequest request
    ) {
        Map<String, String> errors =
                new LinkedHashMap<>();

        if (request == null) {
            errors.put(
                    "request",
                    "A JSON request body is required."
            );

            return errors;
        }

        Integer categoryId =
                request.getRequestedCategoryId();

        if (
            categoryId != null
            && (
                categoryId <= 0
                || categoryId > 255
            )
        ) {
            errors.put(
                    "requestedCategoryId",
                    "Select a valid service category or allow the system to recommend one."
            );
        }

        String priority =
                normalizePriority(
                        request.getPreferredPriority()
                );

        request.setPreferredPriority(priority);

        if (priority == null) {
            errors.put(
                    "preferredPriority",
                    "Priority must be Low, Medium, High, or Urgent."
            );
        }

        String title =
                trimToNull(request.getTitle());

        request.setTitle(title);

        validateLength(
                errors,
                "title",
                title,
                "Request title",
                MINIMUM_TITLE_LENGTH,
                MAXIMUM_TITLE_LENGTH
        );

        String description =
                trimToNull(request.getDescription());

        request.setDescription(description);

        validateLength(
                errors,
                "description",
                description,
                "Request description",
                MINIMUM_DESCRIPTION_LENGTH,
                MAXIMUM_DESCRIPTION_LENGTH
        );

        String location =
                trimToNull(request.getLocation());

        request.setLocation(location);

        validateLength(
                errors,
                "location",
                location,
                "Request location",
                MINIMUM_LOCATION_LENGTH,
                MAXIMUM_LOCATION_LENGTH
        );

        String dateReported =
                trimToNull(request.getDateReported());

        request.setDateReported(dateReported);

        if (dateReported == null) {
            errors.put(
                    "dateReported",
                    "Date reported is required."
            );
        } else {
            try {
                LocalDate parsedDate =
                        LocalDate.parse(dateReported);

                LocalDate currentPortalDate =
                        LocalDate.now(
                                PORTAL_TIME_ZONE
                        );

                if (parsedDate.isAfter(currentPortalDate)) {
                    errors.put(
                            "dateReported",
                            "Date reported cannot be in the future."
                    );
                }
            } catch (DateTimeException exception) {
                errors.put(
                        "dateReported",
                        "Date reported must use the YYYY-MM-DD format."
                );
            }
        }

        return errors;
    }

    public static String normalizePriority(
            String value
    ) {
        String normalized =
                trimToNull(value);

        if (normalized == null) {
            return null;
        }

        return switch (
            normalized.toLowerCase(Locale.ROOT)
        ) {
            case "low" -> "Low";
            case "medium" -> "Medium";
            case "high" -> "High";
            case "urgent" -> "Urgent";
            default -> null;
        };
    }

    private static void validateLength(
            Map<String, String> errors,
            String fieldName,
            String value,
            String displayName,
            int minimumLength,
            int maximumLength
    ) {
        if (value == null) {
            errors.put(
                    fieldName,
                    displayName + " is required."
            );

            return;
        }

        int length = value.length();

        if (length < minimumLength) {
            errors.put(
                    fieldName,
                    displayName
                            + " must contain at least "
                            + minimumLength
                            + " characters."
            );

            return;
        }

        if (length > maximumLength) {
            errors.put(
                    fieldName,
                    displayName
                            + " must not exceed "
                            + maximumLength
                            + " characters."
            );
        }
    }

    private static String trimToNull(
            String value
    ) {
        if (value == null) {
            return null;
        }

        String trimmed =
                value.trim();

        return trimmed.isEmpty()
                ? null
                : trimmed;
    }
}