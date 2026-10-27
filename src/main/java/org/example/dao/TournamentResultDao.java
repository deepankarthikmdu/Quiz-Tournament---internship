package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Tournament;
import org.example.model.TournamentResult;
import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TournamentResultDao {
    private static final String SELECT = "SELECT `attemptId`, `score`, `rank_`, `prizeMoney` FROM `TournamentResult`";

    public List<TournamentResult> findAll() throws SQLException {
        List<TournamentResult> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public List<TournamentResult> findForUser(String userId) throws SQLException {
        String sql = "SELECT tr.`attemptId`, tr.`score`, tr.`rank_`, tr.`prizeMoney` "
                + "FROM `TournamentResult` tr JOIN `Attempt` a ON a.`attemptId` = tr.`attemptId` "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `TournamentRegister` reg ON reg.`registerId` = rp.`registerId` "
                + "WHERE reg.`userId` = ? ORDER BY a.`endDateTime` DESC, a.`attemptId`";
        List<TournamentResult> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entities.add(mapRow(rs));
            }
        }
        return entities;
    }

    public TournamentResult findById(String attemptId) throws SQLException {
        String sql = SELECT + " WHERE `attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public TournamentResult create(String attemptId, int score) throws SQLException {
        String sql = "INSERT INTO `TournamentResult` (`attemptId`, `score`) VALUES (?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setInt(2, score);
            ps.executeUpdate();
        }
        TournamentResult entity = new TournamentResult();
        entity.setAttemptId(attemptId);
        entity.setScore(score);
        entity.setRank(null);
        entity.setPrizeMoney(null);
        return entity;
    }

    public TournamentResult update(String attemptId, TournamentResult entity) throws SQLException {
        String sql = "UPDATE `TournamentResult` SET `score` = ?, `rank_` = ?, `prizeMoney` = ? WHERE `attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setInt(1, entity.getScore());
            if (entity.getRank() == null) ps.setNull(2, java.sql.Types.INTEGER); else ps.setInt(2, entity.getRank());
            ps.setBigDecimal(3, entity.getPrizeMoney());
            ps.setString(4, attemptId);
            ps.executeUpdate();
        }
        return findById(attemptId);
    }

    public boolean delete(String attemptId) throws SQLException {
        String sql = "DELETE FROM `TournamentResult` WHERE `attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            return ps.executeUpdate() > 0;
        }
    }

    private TournamentResult mapRow(ResultSet rs) throws SQLException {
        TournamentResult entity = new TournamentResult();
        entity.setAttemptId(rs.getString("attemptId"));
        entity.setScore(rs.getInt("score"));
        entity.setRank(readNullableInteger(rs, "rank_"));
        entity.setPrizeMoney(rs.getBigDecimal("prizeMoney"));
        return entity;
    }

    private static Integer readNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
