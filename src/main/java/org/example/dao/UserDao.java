package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.User;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class UserDao {

    private static final String SELECT_USER = "SELECT userId, username, name FROM `User`";

    public List<User> findAll() throws SQLException {
        List<User> users = new ArrayList<>();
        String sql = SELECT_USER + " ORDER BY username";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql);
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                users.add(mapRow(rs));
            }
        }
        return users;
    }

    public User findById(String userId) throws SQLException {
        String sql = SELECT_USER + " WHERE userId = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public User findByName(String username) throws SQLException {
        String sql = SELECT_USER + " WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public User findByUsernameForAuthentication(String username) throws SQLException {
        String sql = "SELECT userId, username, password, name FROM `User` WHERE username = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, username);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) return null;
                User user = new User();
                user.setUserId(rs.getString("userId"));
                user.setUsername(rs.getString("username"));
                user.setPassword(rs.getString("password"));
                user.setName(rs.getString("name"));
                return user;
            }
        }
    }


    public User create(User user) throws SQLException {

        String sql = "INSERT INTO `User` (userId, username, password, name) VALUES (?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, user.getUserId());
            ps.setString(2, user.getUsername());
            ps.setString(3, user.getPassword());
            ps.setString(4, user.getName());
            ps.executeUpdate();
        }

        return user;
    }

    public boolean update(String userId, User user) throws SQLException {
        boolean changePassword = user.getPassword() != null && !user.getPassword().isBlank();

        String sql = changePassword
                ? "UPDATE `User` SET username = ?, name = ?, password = ? WHERE userId = ?"
                : "UPDATE `User` SET username = ?, name = ? WHERE userId = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            int i = 1;
            ps.setString(i++, user.getUsername());
            ps.setString(i++, user.getName());
            if (changePassword) {
                ps.setString(i++,user.getPassword());
            }
            ps.setString(i, userId);

            return ps.executeUpdate() > 0;
        }
    }

    public boolean replacePasswordIfUnchanged(String userId, String oldPassword, String passwordHash)
            throws SQLException {
        String sql = "UPDATE `User` SET password = ? WHERE userId = ? AND BINARY password = BINARY ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, passwordHash);
            ps.setString(2, userId);
            ps.setString(3, oldPassword);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean delete(String userId) throws SQLException {
        String sql = "DELETE FROM `User` WHERE userId = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, userId);
            return ps.executeUpdate() > 0;
        }
    }

    private User mapRow(ResultSet rs) throws SQLException {
        User user = new User();
        user.setUserId(rs.getString("userId"));
        user.setUsername(rs.getString("username"));
        user.setName(rs.getString("name"));
        return user;
    }
}
