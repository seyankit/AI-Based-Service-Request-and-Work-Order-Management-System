package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonSyntaxException;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import ph.edu.htcgsc.serviceportal.dao.PersonnelManagementDAO;
import ph.edu.htcgsc.serviceportal.model.PersonnelAccessRecord;
import ph.edu.htcgsc.serviceportal.util.SessionUtil;
import ph.edu.htcgsc.serviceportal.util.CsrfUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.logging.Level;
import java.util.logging.Logger;

public final class PersonnelManagementServlet
        extends HttpServlet {

    private static final Logger LOGGER =
            Logger.getLogger(
                    PersonnelManagementServlet.class.getName()
            );

    private final Gson gson =
            new Gson();

    private final PersonnelManagementDAO personnelManagementDAO =
            new PersonnelManagementDAO();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!requireServiceAdministrator(
                request,
                response
        )) {
            return;
        }

        int limit =
                PersonnelManagementDAO.DEFAULT_LIMIT;

        String limitParameter =
                request.getParameter("limit");

        if (
            limitParameter != null
            && !limitParameter.isBlank()
        ) {
            try {
                limit =
                        Integer.parseInt(
                                limitParameter.trim()
                        );
            } catch (NumberFormatException exception) {
                sendInvalidLimit(response);
                return;
            }

            if (
                limit < 1
                || limit
                    > PersonnelManagementDAO.MAXIMUM_LIMIT
            ) {
                sendInvalidLimit(response);
                return;
            }
        }

        try {
            List<PersonnelAccessRecord> personnel =
                    personnelManagementDAO.findAll(
                            limit
                    );

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Personnel records loaded successfully."
            );

            result.put(
                    "count",
                    personnel.size()
            );

            result.put(
                    "limit",
                    limit
            );

            result.put(
                    "personnel",
                    personnel
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    gson.toJson(result)
            );

        } catch (IllegalArgumentException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Personnel retrieval failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse
                            .SC_INTERNAL_SERVER_ERROR,
                    "Unable to load personnel records."
            );
        }
    }


    private record PersonnelAccessUpdateRequest(
            Integer personnelId,
            Integer departmentId,
            Integer roleId
    ) {
    }

    @Override
    protected void doPut(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        prepareJsonResponse(response);

        if (!requireServiceAdministrator(
                request,
                response
        )) {
            return;
        }

        if (!CsrfUtil.isRequestTokenValid(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "The security token is missing or invalid. Refresh the page and try again."
            );

            return;
        }

        String contentType =
                request.getContentType();

        if (
            contentType == null
            || !contentType
                    .toLowerCase()
                    .startsWith(
                            "application/json"
                    )
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNSUPPORTED_MEDIA_TYPE,
                    "Content-Type must be application/json."
            );

            return;
        }

        long contentLength =
                request.getContentLengthLong();

        if (
            contentLength
                    > 8L * 1024L
        ) {
            sendError(
                    response,
                    413,
                    "The personnel update request is too large."
            );

            return;
        }

        PersonnelAccessUpdateRequest updateRequest;

        try {
            updateRequest =
                    gson.fromJson(
                            request.getReader(),
                            PersonnelAccessUpdateRequest.class
                    );

        } catch (JsonSyntaxException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "The request contains invalid JSON."
            );

            return;
        }

        if (updateRequest == null) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Personnel update data is required."
            );

            return;
        }

        if (
            updateRequest.personnelId() == null
            || updateRequest.personnelId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid personnel account."
            );

            return;
        }

        if (
            updateRequest.departmentId() == null
            || updateRequest.departmentId() <= 0
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a valid department."
            );

            return;
        }

        if (
            updateRequest.roleId() == null
            || updateRequest.roleId() < 1
            || updateRequest.roleId() > 4
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    "Select a supported system role."
            );

            return;
        }

        int actorPersonnelId =
                SessionUtil.getAuthenticatedPersonnelId(
                        request
                );

        int actorRoleId =
                SessionUtil.getRoleId(
                        request
                );

        try {
            personnelManagementDAO.updatePersonnelAccess(
                    actorPersonnelId,
                    actorRoleId,
                    updateRequest.personnelId(),
                    updateRequest.departmentId(),
                    updateRequest.roleId(),
                    request.getRemoteAddr(),
                    request.getHeader(
                            "User-Agent"
                    )
            );

            PersonnelAccessRecord updatedPersonnel =
                    personnelManagementDAO.findById(
                            updateRequest.personnelId()
                    );

            if (updatedPersonnel == null) {
                throw new SQLException(
                        "The updated personnel record could not be reloaded."
                );
            }

            Map<String, Object> result =
                    new LinkedHashMap<>();

            result.put(
                    "success",
                    true
            );

            result.put(
                    "message",
                    "Personnel access updated successfully."
            );

            result.put(
                    "personnel",
                    updatedPersonnel
            );

            response.setStatus(
                    HttpServletResponse.SC_OK
            );

            response.getWriter().write(
                    gson.toJson(result)
            );

        } catch (IllegalArgumentException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_BAD_REQUEST,
                    exception.getMessage()
            );

        } catch (SecurityException exception) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    exception.getMessage()
            );

        } catch (SQLException exception) {
            LOGGER.log(
                    Level.SEVERE,
                    "Personnel access update failed.",
                    exception
            );

            sendError(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    "Unable to update personnel access."
            );
        }
    }
    private boolean requireServiceAdministrator(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        if (!SessionUtil.isAuthenticated(request)) {
            sendError(
                    response,
                    HttpServletResponse.SC_UNAUTHORIZED,
                    "Authentication is required."
            );

            return false;
        }

        if (
            !SessionUtil.hasRole(
                    request,
                    SessionUtil
                            .SERVICE_ADMINISTRATOR_ROLE_ID
            )
        ) {
            sendError(
                    response,
                    HttpServletResponse.SC_FORBIDDEN,
                    "Service Administrator access is required."
            );

            return false;
        }

        return true;
    }

    private void sendInvalidLimit(
            HttpServletResponse response
    ) throws IOException {

        sendError(
                response,
                HttpServletResponse.SC_BAD_REQUEST,
                "The limit parameter must be a whole number from 1 to "
                        + PersonnelManagementDAO.MAXIMUM_LIMIT
                        + "."
        );
    }

    private void sendError(
            HttpServletResponse response,
            int status,
            String message
    ) throws IOException {

        response.setStatus(status);

        Map<String, Object> result =
                new LinkedHashMap<>();

        result.put(
                "success",
                false
        );

        result.put(
                "message",
                message
        );

        response.getWriter().write(
                gson.toJson(result)
        );
    }

    private void prepareJsonResponse(
            HttpServletResponse response
    ) {
        response.setContentType(
                "application/json"
        );

        response.setCharacterEncoding(
                "UTF-8"
        );

        response.setHeader(
                "Cache-Control",
                "no-store"
        );

        response.setHeader(
                "X-Content-Type-Options",
                "nosniff"
        );
    }
}
