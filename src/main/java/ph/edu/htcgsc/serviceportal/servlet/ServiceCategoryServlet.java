package ph.edu.htcgsc.serviceportal.servlet;

import com.google.gson.Gson;
import com.google.gson.JsonObject;

import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import ph.edu.htcgsc.serviceportal.dao.ServiceCategoryDAO;
import ph.edu.htcgsc.serviceportal.model.ServiceCategory;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

public class ServiceCategoryServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    private final ServiceCategoryDAO serviceCategoryDAO =
            new ServiceCategoryDAO();

    private final Gson gson =
            new Gson();

    @Override
    protected void doGet(
            HttpServletRequest request,
            HttpServletResponse response
    ) throws IOException {

        try {
            List<ServiceCategory> categories =
                    serviceCategoryDAO.findAllActive();

            JsonObject responseBody =
                    new JsonObject();

            responseBody.addProperty(
                    "success",
                    true
            );

            responseBody.addProperty(
                    "message",
                    "Service categories loaded successfully."
            );

            responseBody.addProperty(
                    "count",
                    categories.size()
            );

            responseBody.add(
                    "categories",
                    gson.toJsonTree(categories)
            );

            writeJson(
                    response,
                    HttpServletResponse.SC_OK,
                    responseBody
            );

        } catch (SQLException exception) {
            getServletContext().log(
                    "Unable to load service categories. SQL state: "
                            + exception.getSQLState()
            );

            JsonObject responseBody =
                    new JsonObject();

            responseBody.addProperty(
                    "success",
                    false
            );

            responseBody.addProperty(
                    "message",
                    "Unable to load service categories."
            );

            writeJson(
                    response,
                    HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    responseBody
            );
        }
    }

    private void writeJson(
            HttpServletResponse response,
            int statusCode,
            JsonObject responseBody
    ) throws IOException {

        response.setStatus(statusCode);
        response.setCharacterEncoding("UTF-8");
        response.setContentType(
                "application/json"
        );

        response.setHeader(
                "Cache-Control",
                "no-store"
        );

        response.getWriter().write(
                gson.toJson(responseBody)
        );
    }
}