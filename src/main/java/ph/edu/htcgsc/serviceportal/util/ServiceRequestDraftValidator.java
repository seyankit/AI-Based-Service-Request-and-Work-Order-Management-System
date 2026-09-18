package ph.edu.htcgsc.serviceportal.util;

import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;

import java.time.DateTimeException;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.LinkedHashMap;
import java.util.Map;

public final class ServiceRequestDraftValidator {

    private static final ZoneId PORTAL_TIME_ZONE =
            ZoneId.of("Asia/Manila");

    private static final int MAXIMUM_TITLE_LENGTH = 150;
    private static final int MAXIMUM_DESCRIPTION_LENGTH = 5000;
    private static final int MAXIMUM_LOCATION_LENGTH = 120;

    private ServiceRequestDraftValidator() {
    }

    /*
     * Draft validation is intentionally less strict than final submission.
     * Blank fields are allowed, but any supplied values must still be valid.
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
                    "Select a valid service category."
            );
        }

        String rawPriority =
                trimToNull(
                        request.getPreferredPriority()
                );

        if (rawPriority == null) {
            request.setPreferredPriority(null);
        } else {
            String normalizedPriority =
                    ServiceRequestValidator.normalizePriority(
                            rawPriority
                    );

            request.setPreferredPriority(
                    normalizedPriority
            );

            if (normalizedPriority == null) {
                errors.put(
                        "preferredPriority",
                        "Priority must be Low, Medium, High, or Urgent."
                );
            }
        }

        String title =
                trimToNull(request.getTitle());

        request.setTitle(title);

        validateMaximumLength(
                errors,
                "title",
                title,
                "Request title",
                MAXIMUM_TITLE_LENGTH
        );

        String description =
                trimToNull(request.getDescription());

        request.setDescription(description);

        validateMaximumLength(
                errors,
                "description",
                description,
                "Request description",
                MAXIMUM_DESCRIPTION_LENGTH
        );

        String location =
                trimToNull(request.getLocation());

        request.setLocation(location);

        validateMaximumLength(
                errors,
                "location",
                location,
                "Request location",
                MAXIMUM_LOCATION_LENGTH
        );

        String dateReported =
                trimToNull(request.getDateReported());

        request.setDateReported(dateReported);

        if (dateReported != null) {
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

    private static void validateMaximumLength(
            Map<String, String> errors,
            String fieldName,
            String value,
            String displayName,
            int maximumLength
    ) {
        if (
            value != null
            && value.length() > maximumLength
        ) {
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