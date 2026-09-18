package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.ServiceCategory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class ServiceCategoryDAO {

    private static final String SELECT_ACTIVE_CATEGORIES = """
            SELECT
                sc.Category_ID,
                sc.Category_Code,
                sc.Category_Name,
                sc.Category_Description,
                sc.Default_Department_ID,
                d.Department_Name AS Default_Department_Name,
                sc.Display_Order,
                sc.Is_Active
            FROM SERVICE_CATEGORY sc
            INNER JOIN DEPARTMENT d
                ON d.Department_ID = sc.Default_Department_ID
            WHERE sc.Is_Active = TRUE
            ORDER BY
                sc.Display_Order ASC,
                sc.Category_Name ASC
            """;

    private static final String SELECT_ACTIVE_CATEGORY_BY_ID = """
            SELECT
                sc.Category_ID,
                sc.Category_Code,
                sc.Category_Name,
                sc.Category_Description,
                sc.Default_Department_ID,
                d.Department_Name AS Default_Department_Name,
                sc.Display_Order,
                sc.Is_Active
            FROM SERVICE_CATEGORY sc
            INNER JOIN DEPARTMENT d
                ON d.Department_ID = sc.Default_Department_ID
            WHERE sc.Category_ID = ?
              AND sc.Is_Active = TRUE
            LIMIT 1
            """;

    public List<ServiceCategory> findAllActive()
            throws SQLException {

        List<ServiceCategory> categories =
                new ArrayList<>();

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            SELECT_ACTIVE_CATEGORIES
                    );

            ResultSet result =
                    statement.executeQuery()
        ) {
            while (result.next()) {
                categories.add(
                        mapCategory(result)
                );
            }
        }

        return categories;
    }

    public ServiceCategory findActiveById(
            int categoryId
    ) throws SQLException {

        if (categoryId <= 0) {
            return null;
        }

        try (
            Connection connection =
                    DatabaseConnection.getConnection();

            PreparedStatement statement =
                    connection.prepareStatement(
                            SELECT_ACTIVE_CATEGORY_BY_ID
                    )
        ) {
            statement.setInt(
                    1,
                    categoryId
            );

            try (
                ResultSet result =
                        statement.executeQuery()
            ) {
                if (!result.next()) {
                    return null;
                }

                return mapCategory(result);
            }
        }
    }

    private ServiceCategory mapCategory(
            ResultSet result
    ) throws SQLException {

        ServiceCategory category =
                new ServiceCategory();

        category.setCategoryId(
                result.getInt("Category_ID")
        );

        category.setCategoryCode(
                result.getString("Category_Code")
        );

        category.setCategoryName(
                result.getString("Category_Name")
        );

        category.setCategoryDescription(
                result.getString(
                        "Category_Description"
                )
        );

        category.setDefaultDepartmentId(
                result.getInt(
                        "Default_Department_ID"
                )
        );

        category.setDefaultDepartmentName(
                result.getString(
                        "Default_Department_Name"
                )
        );

        category.setDisplayOrder(
                result.getInt("Display_Order")
        );

        category.setActive(
                result.getBoolean("Is_Active")
        );

        return category;
    }
}