package ph.edu.htcgsc.serviceportal.util;

import org.junit.jupiter.api.Test;
import ph.edu.htcgsc.serviceportal.dto.CreateServiceRequestRequest;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ServiceRequestValidatorTest {

    @Test
    void draftAllowsPartialValuesButEnforcesEffectiveColumnLimits() {
        CreateServiceRequestRequest draft = new CreateServiceRequestRequest();
        draft.setTitle("x".repeat(151));
        draft.setDescription("x".repeat(5001));
        draft.setLocation("x".repeat(121));

        Map<String, String> errors = ServiceRequestDraftValidator
                .validateAndNormalize(draft);

        assertEquals(3, errors.size());
        assertTrue(errors.containsKey("title"));
        assertTrue(errors.containsKey("description"));
        assertTrue(errors.containsKey("location"));

        CreateServiceRequestRequest partial = new CreateServiceRequestRequest();
        assertTrue(ServiceRequestDraftValidator
                .validateAndNormalize(partial)
                .isEmpty());
    }

    @Test
    void finalSubmissionStillRequiresItsRequiredFields() {
        CreateServiceRequestRequest request = new CreateServiceRequestRequest();

        Map<String, String> errors = ServiceRequestValidator
                .validateAndNormalize(request);

        assertTrue(errors.containsKey("preferredPriority"));
        assertTrue(errors.containsKey("title"));
        assertTrue(errors.containsKey("description"));
        assertTrue(errors.containsKey("location"));
        assertTrue(errors.containsKey("dateReported"));
    }
}