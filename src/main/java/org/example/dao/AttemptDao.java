package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Attempt;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class AttemptDao {
    private static final String SELECT = "SELECT `attemptId`, `roundParticipantId`, `startAttemptDateTime`, `endDateTime` FROM `Attempt`";

    public List<Attempt> findAll() throws SQLException {
        List<Attempt> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public Attempt findById(String attemptId) throws SQLException {
        String sql = SELECT + " WHERE `attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public String authenticateUser (String attemptId) throws SQLException {
        String sql = "SELECT tr.userId From Attempt a join RoundParticipant rp ON a.roundParticipantId = rp.roundParticipantId JOIN TournamentRegister tr ON rp.registerId = tr.registerId WHERE a.attemptId = ?";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("userId") : null;
            }
        }
    }

    public Attempt create(Attempt entity) throws SQLException {
        String sql = "INSERT INTO `Attempt` (`attemptId`, `roundParticipantId`, `startAttemptDateTime`, `endDateTime`) VALUES (?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getAttemptId());
            ps.setString(2, entity.getRoundParticipantId());
            ps.setNull(3, java.sql.Types.TIMESTAMP);
            ps.setNull(4, java.sql.Types.TIMESTAMP);
            ps.executeUpdate();
        }
        return entity;
    }

    public Attempt update(String attemptId, Attempt entity) throws SQLException {
        String sql = "UPDATE `Attempt` SET `roundParticipantId` = ?, `startAttemptDateTime` = ?, `endDateTime` = ? WHERE `attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getRoundParticipantId());
            setDateTime(ps, 2, entity.getStartAttemptDateTime());
            setDateTime(ps, 3, entity.getEndDateTime());
            ps.setString(4, attemptId);
            ps.executeUpdate();
        }
        return findById(attemptId);
    }

    public boolean authenticateAttemptEndTimestamp (String attemptId) throws SQLException {
        String sql = "SELECT endDateTime FROM attempt where attemptId = ? AND endDateTime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()){
                return rs.next();
            }
        }
    }

    public boolean authenticateAttemptStartTimestamp (String attemptId) throws SQLException {
        String sql = "SELECT startAttemptDateTime FROM attempt where attemptId = ? AND startAttemptDateTime IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()){
                return rs.next();
            }
        }
    }

    public boolean setStartAttemptTimestamp (String attemptId) throws SQLException {
        String sql = "UPDATE `Attempt` SET `startAttemptDateTime` = NOW()"
                + "WHERE `attemptId` = ? AND `startAttemptDateTime` IS NULL AND `endDateTime` IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean setEndAttemptTimestamp (String attemptId) throws SQLException {
        String sql = "UPDATE `Attempt` SET `endDateTime` = NOW() "
                + "WHERE `attemptId` = ? AND `startAttemptDateTime` IS NOT NULL AND `endDateTime` IS NULL";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            return ps.executeUpdate() == 1;
        }
    }

    public boolean delete(String attemptId) throws SQLException {
        String sql = "DELETE FROM `Attempt` WHERE `attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            return ps.executeUpdate() > 0;
        }
    }

    private Attempt mapRow(ResultSet rs) throws SQLException {
        Attempt entity = new Attempt();
        entity.setAttemptId(rs.getString("attemptId"));
        entity.setRoundParticipantId(rs.getString("roundParticipantId"));
        entity.setStartAttemptDateTime(readLocalDateTime(rs, "startAttemptDateTime"));
        entity.setEndDateTime(readLocalDateTime(rs, "endDateTime"));
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
}
