package ph.edu.htcgsc.serviceportal.util;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class WorkOrderProgressPolicyTest {

    @Test
    void technicianLifecycleTransitionsMatchPhase5A() {
        assertEquals(new WorkflowPolicy.Transition("Acknowledged", "Acknowledged", 0),
                WorkflowPolicy.transition("Assigned", "acknowledge", 0));
        assertEquals(new WorkflowPolicy.Transition("In Progress", "Work Started", 0),
                WorkflowPolicy.transition("Acknowledged", "start", 0));
        assertEquals(new WorkflowPolicy.Transition("In Progress", "Progress Update", 40),
                WorkflowPolicy.transition("In Progress", "progress", 40));
        assertEquals(new WorkflowPolicy.Transition("On Hold", "On Hold", 40),
                WorkflowPolicy.transition("In Progress", "hold", 40));
        assertEquals(new WorkflowPolicy.Transition("On Hold", "Progress Update", 60),
                WorkflowPolicy.transition("On Hold", "progress", 60));
        assertEquals(new WorkflowPolicy.Transition("In Progress", "Resumed", 60),
                WorkflowPolicy.transition("On Hold", "resume", 60));
        assertEquals(new WorkflowPolicy.Transition("Completed", "Completed", 100),
                WorkflowPolicy.transition("In Progress", "complete", 0));
    }

    @Test
    void invalidTechnicianTransitionsAndProgressPercentagesAreRejected() {
        assertThrows(IllegalStateException.class,
                () -> WorkflowPolicy.transition("Assigned", "start", 0));
        assertThrows(IllegalStateException.class,
                () -> WorkflowPolicy.transition("On Hold", "complete", 0));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowPolicy.transition("In Progress", "progress", 0));
        assertThrows(IllegalArgumentException.class,
                () -> WorkflowPolicy.transition("In Progress", "progress", 100));
    }
}
