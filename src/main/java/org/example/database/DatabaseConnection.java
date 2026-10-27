package org.example.database;

import java.io.IOException;
import java.io.InputStream;
import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.Properties;

public class DatabaseConnection {

    private static final String url;
    private static final String username;
    private static final String password;

    static {
        Properties props = new Properties();
        try (InputStream in = DatabaseConnection.class
                .getClassLoader()
                .getResourceAsStream("db.properties")) {

            if (in == null) {
                throw new IllegalStateException("db.properties not found in src/main/resources");
            }
            props.load(in);

            url = props.getProperty("db.url");
            username = props.getProperty("db.username");
            password = props.getProperty("db.password");

            Class.forName(props.getProperty("db.driver"));

        } catch (IOException | ClassNotFoundException e) {
            throw new ExceptionInInitializerError("Could not initialise database settings: " + e.getMessage());
        }
    }

    public static Connection getConnection() throws SQLException {
        return DriverManager.getConnection(url, username, password);
    }
}