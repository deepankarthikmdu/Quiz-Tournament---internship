package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Option;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class OptionDao {
    private static final String SELECT = "SELECT `optionId`, `questionId`, `optionText`, `isCorrect` FROM `Options`";

    public List<Option> findAll() throws SQLException {
        List<Option> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public List<Option> findAllForOwner(String ownerUserId) throws SQLException {
        String sql = "SELECT DISTINCT o.`optionId`, o.`questionId`, o.`optionText`, o.`isCorrect` "
                + "FROM `Options` o JOIN `QuizQuestionMapping` qm ON qm.`questionId` = o.`questionId` "
                + "JOIN `Quiz` q ON q.`quizId` = qm.`quizId` WHERE q.`userId` = ?";
        List<Option> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entities.add(mapRow(rs));
            }
        }
        return entities;
    }

    public Option findById(String optionId) throws SQLException {
        String sql = SELECT + " WHERE `optionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, optionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Option findByIdForOwner(String optionId, String ownerUserId) throws SQLException {
        String sql = "SELECT DISTINCT o.`optionId`, o.`questionId`, o.`optionText`, o.`isCorrect` "
                + "FROM `Options` o JOIN `QuizQuestionMapping` qm ON qm.`questionId` = o.`questionId` "
                + "JOIN `Quiz` q ON q.`quizId` = qm.`quizId` "
                + "WHERE o.`optionId` = ? AND q.`userId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, optionId);
            ps.setString(2, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Option create(Option entity) throws SQLException {
        String sql = "INSERT INTO `Options` (`optionId`, `questionId`, `optionText`, `isCorrect`) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getOptionId());
            ps.setString(2, entity.getQuestionId());
            ps.setString(3, entity.getOptionText());
            ps.setBoolean(4, entity.isCorrect());
            ps.executeUpdate();
        }
        return entity;
    }

    public Boolean findAnswer(String questionId, String optionId) throws SQLException {
        String sql = "SELECT `isCorrect` FROM `Options` WHERE `questionId` = ? AND `optionId` = ?";
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, questionId);
            ps.setString(2, optionId);
            try(ResultSet rs = ps.executeQuery()){
                return rs.next() ? rs.getBoolean("isCorrect") : null;
            }
        }
    }

    public Option update(String optionId, Option entity) throws SQLException {
        String sql = "UPDATE `Options` SET `questionId` = ?, `optionText` = ?, `isCorrect` = ? WHERE `optionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getQuestionId());
            ps.setString(2, entity.getOptionText());
            ps.setBoolean(3, entity.isCorrect());
            ps.setString(4, optionId);
            ps.executeUpdate();
        }
        return findById(optionId);
    }

    public boolean delete(String optionId) throws SQLException {
        String sql = "DELETE FROM `Options` WHERE `optionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, optionId);
            return ps.executeUpdate() > 0;
        }
    }

    private Option mapRow(ResultSet rs) throws SQLException {
        Option entity = new Option();
        entity.setOptionId(rs.getString("optionId"));
        entity.setQuestionId(rs.getString("questionId"));
        entity.setOptionText(rs.getString("optionText"));
        entity.setCorrect(rs.getBoolean("isCorrect"));
        return entity;
    }
}
