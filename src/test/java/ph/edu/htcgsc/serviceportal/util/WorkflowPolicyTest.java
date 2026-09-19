package ph.edu.htcgsc.serviceportal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkflowPolicyTest {

    @Test
    void departmentHeadMustMatchApprovalDepartment() {
        assertThrows(
                SecurityException.class,
                () -> WorkflowPolicy.requireApproval(3, 2, 3, 10, 11, "Awaiting Approval", "Pending")
        );
    }

    @Test
    void onlyDepartmentHeadMayApprove() {
        assertThrows(
                SecurityException.class,
                () -> WorkflowPolicy.requireApproval(2, 3, 3, 10, 11, "Awaiting Approval", "Pending")
        );
    }

    @Test
    void approverCannotApproveOwnRequest() {
        assertThrows(
                SecurityException.class,
                () -> WorkflowPolicy.requireApproval(3, 3, 3, 11, 11, "Awaiting Approval", "Pending")
        );
    }

    @Test
    void requestMustAwaitPendingDecision() {
        assertThrows(
                IllegalStateException.class,
                () -> WorkflowPolicy.requireApproval(3, 3, 3, 10, 11, "Submitted", "Pending")
        );
        assertThrows(
                IllegalStateException.class,
                () -> WorkflowPolicy.requireApproval(3, 3, 3, 10, 11, "Awaiting Approval", "Approved")
        );
    }

    @Test
    void validApprovalContextIsAccepted() {
        assertDoesNotThrow(
                () -> WorkflowPolicy.requireApproval(3, 3, 3, 10, 11, "Awaiting Approval", "Pending")
        );
    }

    @Test
    void remarksMustBeBetweenThreeAndOneThousandCharacters() {
        assertDoesNotThrow(
                () -> WorkflowPolicy.text("abc", "Decision remarks", 3, 1000)
        );
        assertDoesNotThrow(
                () -> WorkflowPolicy.text("x".repeat(1000), "Decision remarks", 3, 1000)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowPolicy.text("ab", "Decision remarks", 3, 1000)
        );
        assertThrows(
                IllegalArgumentException.class,
                () -> WorkflowPolicy.text("x".repeat(1001), "Decision remarks", 3, 1000)
        );
    }

    @Test
    void initialAssignmentMovesCreatedToAssigned() {
        assertEquals(new WorkflowPolicy.Transition("Assigned", "Assigned", 0),
                WorkflowPolicy.assignment("Created"));
    }

    @Test
    void initialAssignmentRejectsEveryOtherWorkOrderState() {
        for (String status : new String[] {
                "Assigned", "Acknowledged", "In Progress", "On Hold", "Completed", "Verified"
        }) {
            assertThrows(IllegalStateException.class, () -> WorkflowPolicy.assignment(status));
        }
    }
}
