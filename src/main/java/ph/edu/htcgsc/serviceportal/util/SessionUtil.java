package ph.edu.htcgsc.serviceportal.util;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

public final class SessionUtil {

    private static final String PERSONNEL_ID_ATTRIBUTE =
            "personnelId";

    private static final String ROLE_ID_ATTRIBUTE =
            "roleId";

    public static final int REQUESTER_ROLE_ID = 1;
    public static final int SERVICE_ADMINISTRATOR_ROLE_ID = 2;
    public static final int DEPARTMENT_HEAD_ROLE_ID = 3;
    public static final int SERVICE_PERSONNEL_ROLE_ID = 4;

    private SessionUtil() {
    }

    /*
     * Returns the existing session only.
     * This method never creates a new session.
     */
    public static HttpSession getExistingSession(
            HttpServletRequest request
    ) {
        if (request == null) {
            return null;
        }

        return request.getSession(false);
    }

    public static Integer getAuthenticatedPersonnelId(
            HttpServletRequest request
    ) {
        HttpSession session =
                getExistingSession(request);

        return getPositiveIntegerAttribute(
                session,
                PERSONNEL_ID_ATTRIBUTE
        );
    }

    public static Integer getRoleId(
            HttpServletRequest request
    ) {
        HttpSession session =
                getExistingSession(request);

        return getPositiveIntegerAttribute(
                session,
                ROLE_ID_ATTRIBUTE
        );
    }

    public static boolean isAuthenticated(
            HttpServletRequest request
    ) {
        return getAuthenticatedPersonnelId(request) != null
                && getRoleId(request) != null;
    }

    public static boolean hasRole(
            HttpServletRequest request,
            int... allowedRoleIds
    ) {
        Integer currentRoleId =
                getRoleId(request);

        if (
            currentRoleId == null
            || allowedRoleIds == null
        ) {
            return false;
        }

        for (int allowedRoleId : allowedRoleIds) {
            if (currentRoleId == allowedRoleId) {
                return true;
            }
        }

        return false;
    }

    public static boolean isRequester(
            HttpServletRequest request
    ) {
        return hasRole(
                request,
                REQUESTER_ROLE_ID
        );
    }

    private static Integer getPositiveIntegerAttribute(
            HttpSession session,
            String attributeName
    ) {
        if (session == null) {
            return null;
        }

        Object value =
                session.getAttribute(attributeName);

        if (value == null) {
            return null;
        }

        int parsedValue;

        if (value instanceof Number number) {
            parsedValue = number.intValue();
        } else {
            try {
                parsedValue =
                        Integer.parseInt(
                                value.toString().trim()
                        );
            } catch (NumberFormatException exception) {
                return null;
            }
        }

        return parsedValue > 0
                ? parsedValue
                : null;
    }
}