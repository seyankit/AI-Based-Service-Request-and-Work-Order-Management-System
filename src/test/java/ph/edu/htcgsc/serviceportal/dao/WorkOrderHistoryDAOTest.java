package ph.edu.htcgsc.serviceportal.dao;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class WorkOrderHistoryDAOTest {

    @Test
    void normalizesChronologicalTimelineAndSuppressesOnlyKnownLifecycleDuplicates() {
        List<Map<String, Object>> events = WorkOrderHistoryDAO.normalizeWorkOrderEvents(
                List.of(
                        history(1L, "Created", "2026-01-01T00:00:00Z"),
                        history(2L, "Started", "2026-01-01T00:02:00Z"),
                        history(3L, "Assigned", "2026-01-01T00:01:00Z"),
                        history(4L, "On Hold", "2026-01-01T00:04:00Z"),
                        history(5L, "Verified", "2026-01-01T00:05:00Z")),
                List.of(progress(9L, "Work Started", "2026-01-01T00:02:00Z"),
                        progress(10L, "Progress Update", "2026-01-01T00:03:00Z")));

        assertEquals(List.of("Created", "Assigned", "Work Started", "Progress Update", "On Hold", "Verified"),
                events.stream().map(event -> event.get("eventType")).toList());
        assertEquals("WORK_ORDER_PROGRESS", events.get(2).get("source"));
        assertEquals(40, events.get(3).get("percentage"));
    }

    @Test
    void usesSourceOrderThenSourceIdWhenTimestampsMatch() {
        List<Map<String, Object>> events = WorkOrderHistoryDAO.normalizeWorkOrderEvents(
                List.of(history(8L, "Created", "2026-01-01T00:00:00Z"),
                        history(3L, "Assigned", "2026-01-01T00:00:00Z")),
                List.of(progress(1L, "Progress Update", "2026-01-01T00:00:00Z")));

        assertEquals(List.of("Assigned", "Created", "Progress Update"),
                events.stream().map(event -> event.get("eventType")).toList());
        assertTrue(events.stream().noneMatch(event -> event.containsKey("historyId")
                || event.containsKey("progressId")));
    }

    private static Map<String, Object> history(long id, String action, String occurredAt) {
        return Map.of("historyId", id, "actionType", action,
                "previousStatus", "Created", "newStatus", "Assigned",
                "remarks", "Recorded safely.", "actorId", 17L, "occurredAt", occurredAt);
    }

    private static Map<String, Object> progress(long id, String action, String occurredAt) {
        return Map.of("progressId", id, "updateType", action,
                "previousStatus", "Acknowledged", "newStatus", "In Progress",
                "percentage", 40, "remarks", "Work continues safely.",
                "actorId", 15L, "occurredAt", occurredAt);
    }
}
