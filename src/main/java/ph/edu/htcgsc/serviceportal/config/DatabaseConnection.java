package ph.edu.htcgsc.serviceportal.config;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;

public final class DatabaseConnection {

    private static final String DEFAULT_URL =
            "jdbc:mysql://127.0.0.1:3306/htc_service_portal"
            + "?useSSL=false"
            + "&allowPublicKeyRetrieval=true"
            + "&serverTimezone=Asia/Manila"
            + "&useUnicode=true"
            + "&characterEncoding=UTF-8";

    private static final String URL = setting(
            "htc.db.url",
            "HTC_DB_URL",
            DEFAULT_URL
    );

    private static final String USER = setting(
            "htc.db.user",
            "HTC_DB_USER",
            "htc_app"
    );

    private static final String PASSWORD = setting(
            "htc.db.password",
            "HTC_DB_PASSWORD",
            ""
    );

    static {
        try {
            Class.forName("com.mysql.cj.jdbc.Driver");
        } catch (ClassNotFoundException exception) {
            throw new ExceptionInInitializerError(
                    "MySQL Connector/J was not found on the application classpath."
            );
        }
    }

    private DatabaseConnection() {
    }

    public static Connection getConnection() throws SQLException {
        if (PASSWORD.isBlank()) {
            throw new SQLException(
                    "Database password is not configured. Set HTC_DB_PASSWORD for the Tomcat process."
            );
        }

        return DriverManager.getConnection(URL, USER, PASSWORD);
    }

    private static String setting(
            String propertyName,
            String environmentName,
            String defaultValue) {

        String propertyValue = System.getProperty(propertyName);
        if (propertyValue != null && !propertyValue.isBlank()) {
            return propertyValue.trim();
        }

        String environmentValue = System.getenv(environmentName);
        if (environmentValue != null && !environmentValue.isBlank()) {
            return environmentValue.trim();
        }

        return defaultValue;
    }
}
