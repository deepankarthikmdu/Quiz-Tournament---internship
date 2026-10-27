package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.QuizQuestionMapping;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QuizQuestionMappingDao {
    private static final String SELECT = "SELECT `quizId`, `questionId` FROM `QuizQuestionMapping`";

    public List<QuizQuestionMapping> findAll() throws SQLException {
        List<QuizQuestionMapping> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public QuizQuestionMapping findById(String questionId) throws SQLException {
        String sql = SELECT + " WHERE `questionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public QuizQuestionMapping create(QuizQuestionMapping entity) throws SQLException {
        String sql = "INSERT INTO `QuizQuestionMapping` (`quizId`, `questionId`) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getQuizId());
            ps.setString(2, entity.getQuestionId());
            ps.executeUpdate();
        }
        return entity;
    }

    public QuizQuestionMapping update(String quizId, String questionId, QuizQuestionMapping entity) throws SQLException {
        return findById(questionId);
    }

    public boolean delete(String quizId, String questionId) throws SQLException {
        String sql = "DELETE FROM `QuizQuestionMapping` WHERE `quizId` = ? AND `questionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, quizId);
            ps.setString(2, questionId);
            return ps.executeUpdate() > 0;
        }
    }

    private QuizQuestionMapping mapRow(ResultSet rs) throws SQLException {
        QuizQuestionMapping entity = new QuizQuestionMapping();
        entity.setQuizId(rs.getString("quizId"));
        entity.setQuestionId(rs.getString("questionId"));
        return entity;
    }
}

