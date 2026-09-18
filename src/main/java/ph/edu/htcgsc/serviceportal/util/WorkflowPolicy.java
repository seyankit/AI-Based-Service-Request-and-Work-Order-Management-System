package ph.edu.htcgsc.serviceportal.util;

import java.util.Set;

/** Lifecycle checks are kept independent from HTTP and JDBC so invalid transitions are testable. */
public final class WorkflowPolicy {
    private WorkflowPolicy() { }

    public record Transition(String status, String updateType, int percentage) { }

    public static Transition transition(String current, String action, int percentage) {
        if (percentage < 0 || percentage > 100) throw new IllegalArgumentException("Progress must be from 0 to 100.");
        return switch (action) {
            case "acknowledge" -> checked(current, Set.of("Assigned"), "Acknowledged", "Acknowledged", 0);
            case "start" -> checked(current, Set.of("Acknowledged"), "In Progress", "Work Started", Math.min(percentage, 99));
            case "progress" -> {
                if (percentage < 1 || percentage > 99) throw new IllegalArgumentException("Progress updates must be from 1 to 99 percent.");
                yield checked(current, Set.of("In Progress", "On Hold"), current, "Progress Update", percentage);
            }
            case "hold" -> checked(current, Set.of("In Progress"), "On Hold", "On Hold", Math.min(percentage, 99));
            case "resume" -> checked(current, Set.of("On Hold"), "In Progress", "Resumed", Math.min(percentage, 99));
            case "complete" -> checked(current, Set.of("In Progress"), "Completed", "Completed", 100);
            case "verify" -> checked(current, Set.of("Completed"), "Verified", "Verified", 100);
            default -> throw new IllegalArgumentException("Unknown work-order action.");
        };
    }

    private static Transition checked(String current, Set<String> allowed, String next, String type, int percentage) {
        if (!allowed.contains(current)) throw new IllegalStateException("This action is not allowed while the work order is " + current + ".");
        return new Transition(next, type, percentage);
    }

    public static void requireApproval(int role, int actorDepartment, int approvalDepartment,
                                       int requesterId, int actorId, String status, String decision) {
        if (role != 3 || actorDepartment != approvalDepartment) throw new SecurityException("Department Head access for this department is required.");
        if (requesterId == actorId) throw new SecurityException("You cannot approve your own service request.");
        if (!"Awaiting Approval".equals(status) || !"Pending".equals(decision))
            throw new IllegalStateException("This request is no longer awaiting a decision.");
    }

    public static String text(String value, String label, int minimum, int maximum) {
        String result = value == null ? "" : value.trim();
        if (result.length() < minimum || result.length() > maximum)
            throw new IllegalArgumentException(label + " must contain " + minimum + " to " + maximum + " characters.");
        return result;
    }
}
