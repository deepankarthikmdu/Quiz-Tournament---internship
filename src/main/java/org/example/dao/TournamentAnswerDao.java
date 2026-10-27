package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.dto.TournamentAnswerRequest;
import org.example.model.TournamentAnswer;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TournamentAnswerDao {
    private static final String SELECT = "SELECT `attemptId`, `questionId`, `selectedOptionId`, `isCorrect`, `attemptedDate` FROM `TournamentAnswer`";

    public List<TournamentAnswer> findAll() throws SQLException {
        List<TournamentAnswer> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public TournamentAnswer findById(String attemptId, String questionId) throws SQLException {
        String sql = SELECT + " WHERE `attemptId` = ? AND `questionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setString(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<TournamentAnswer> findAllByAttemptId(String attemptId) throws SQLException {
        List<TournamentAnswer> entities = new ArrayList<>();
        String sql = SELECT + " WHERE `attemptId` = ? ORDER BY `attemptedDate`, `questionId`";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entities.add(mapRow(rs));
            }
        }
        return entities;
    }

    public int findScore(String attemptId) throws SQLException {
        String sql = "SELECT COUNT(*) AS score FROM TournamentAnswer WHERE attemptId = ? AND isCorrect = 1";
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1,attemptId);
            try(ResultSet rs = ps.executeQuery()){
                if (!rs.next()) throw new SQLException("COUNT query returned no row");
                return rs.getInt("score");
            }
        }
    }

    public boolean questionBelongsToOpenAttempt(String attemptId, String questionId) throws SQLException {
        String sql = "SELECT 1 FROM `Attempt` a "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `Round` r ON r.`roundId` = rp.`roundId` "
                + "JOIN `QuizQuestionMapping` qqm ON qqm.`quizId` = r.`quizId` "
                + "WHERE a.`attemptId` = ? AND qqm.`questionId` = ? "
                + "AND a.`startAttemptDateTime` IS NOT NULL AND a.`endDateTime` IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setString(2, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public TournamentAnswer createOrUpdate(String attemptId, TournamentAnswerRequest entity, boolean answer) throws SQLException {
        String sql = "INSERT INTO `TournamentAnswer` (`attemptId`, `questionId`, `selectedOptionId`, `isCorrect`, `attemptedDate`) "
                + "VALUES (?, ?, ?, ?, NOW()) ON DUPLICATE KEY UPDATE "
                + "`selectedOptionId` = VALUES(`selectedOptionId`), `isCorrect` = VALUES(`isCorrect`), "
                + "`attemptedDate` = NOW()";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setString(2, entity.questionId());
            ps.setString(3, entity.optionId());
            ps.setBoolean(4, answer);
            ps.executeUpdate();
        }
        return findById(attemptId, entity.questionId());
    }

    public TournamentAnswer update(String attemptId, String questionId, TournamentAnswer entity) throws SQLException {
        String sql = "UPDATE `TournamentAnswer` SET `selectedOptionId` = ?, `isCorrect` = ?, `attemptedDate` = ? WHERE `attemptId` = ? AND `questionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getSelectedOptionId());
            if (entity.getIsCorrect() == null) ps.setNull(2, java.sql.Types.TINYINT); else ps.setBoolean(2, entity.getIsCorrect());
            setDateTime(ps, 3, entity.getAttemptedDate());
            ps.setString(4, attemptId);
            ps.setString(5, questionId);
            ps.executeUpdate();
        }
        return findById(attemptId, questionId);
    }

    public boolean delete(String attemptId, String questionId) throws SQLException {
        String sql = "DELETE FROM `TournamentAnswer` WHERE `attemptId` = ? AND `questionId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setString(2, questionId);
            return ps.executeUpdate() > 0;
        }
    }

    private TournamentAnswer mapRow(ResultSet rs) throws SQLException {
        TournamentAnswer entity = new TournamentAnswer();
        entity.setAttemptId(rs.getString("attemptId"));
        entity.setQuestionId(rs.getString("questionId"));
        entity.setSelectedOptionId(rs.getString("selectedOptionId"));
        entity.setIsCorrect(readNullableBoolean(rs, "isCorrect"));
        entity.setAttemptedDate(readLocalDateTime(rs, "attemptedDate"));
        return entity;
    }

    private static void setDateTime(PreparedStatement ps, int index, LocalDateTime value) throws SQLException {
        if (value == null) ps.setNull(index, java.sql.Types.TIMESTAMP);
        else ps.setTimestamp(index, java.sql.Timestamp.valueOf(value));
    }

    private static LocalDateTime readLocalDateTime(ResultSet rs, String column) throws SQLException {
        java.sql.Timestamp value = rs.getTimestamp(column);
        return value == null ? null : value.toLocalDateTime();
    }

    private static Boolean readNullableBoolean(ResultSet rs, String column) throws SQLException {
        boolean value = rs.getBoolean(column);
        return rs.wasNull() ? null : value;
    }
}
