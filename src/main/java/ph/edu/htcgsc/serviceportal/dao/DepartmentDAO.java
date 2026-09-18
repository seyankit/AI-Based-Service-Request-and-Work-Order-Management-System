package ph.edu.htcgsc.serviceportal.dao;

import ph.edu.htcgsc.serviceportal.config.DatabaseConnection;
import ph.edu.htcgsc.serviceportal.model.Department;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class DepartmentDAO {

    public List<Department> findAll() throws SQLException {
        String sql = """
                SELECT Department_ID, Department_Name
                FROM DEPARTMENT
                ORDER BY Department_ID
                """;

        List<Department> departments = new ArrayList<>();

        try (Connection connection = DatabaseConnection.getConnection();
             PreparedStatement statement = connection.prepareStatement(sql);
             ResultSet result = statement.executeQuery()) {

            while (result.next()) {
                departments.add(new Department(
                        result.getInt("Department_ID"),
                        result.getString("Department_Name")
                ));
            }
        }

        return departments;
    }
}
