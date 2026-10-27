package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.dto.LeaderboardEntry;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class LeaderboardDao {
    private static final String ENTRY_SELECT = "SELECT a.`attemptId`, t.`tournamentId`, t.`name` AS tournamentName, "
            + "u.`userId`, u.`username`, u.`name`, tr.`score`, tr.`rank_`, tr.`prizeMoney`, "
            + "TIMESTAMPDIFF(MICROSECOND, a.`startAttemptDateTime`, a.`endDateTime`) AS attemptDurationMicros, "
            + "a.`endDateTime` AS completedAt "
            + "FROM `Tournament` t "
            + "JOIN `Round` r ON r.`tournamentId` = t.`tournamentId` "
            + "AND r.`roundNumber` = (SELECT MAX(r2.`roundNumber`) FROM `Round` r2 WHERE r2.`tournamentId` = t.`tournamentId`) "
            + "JOIN `RoundParticipant` rp ON rp.`roundId` = r.`roundId` "
            + "JOIN `TournamentRegister` reg ON reg.`registerId` = rp.`registerId` "
            + "JOIN `User` u ON u.`userId` = reg.`userId` "
            + "JOIN `Attempt` a ON a.`roundParticipantId` = rp.`roundParticipantId` "
            + "JOIN `TournamentResult` tr ON tr.`attemptId` = a.`attemptId` ";

    public boolean tournamentExists(String tournamentId) throws SQLException {
        String sql = "SELECT 1 FROM `Tournament` WHERE `tournamentId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public boolean canViewTournament(String tournamentId, String userId) throws SQLException {
        String sql = "SELECT EXISTS (SELECT 1 FROM `Tournament` t WHERE t.`tournamentId` = ? AND t.`userId` = ?) "
                + "OR EXISTS (SELECT 1 FROM `TournamentRegister` reg WHERE reg.`tournamentId` = ? "
                + "AND reg.`userId` = ? AND reg.`status` = 'confirmed') AS allowed";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            ps.setString(2, userId);
            ps.setString(3, tournamentId);
            ps.setString(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("allowed");
            }
        }
    }

    public boolean canViewAttempt(String attemptId, String userId) throws SQLException {
        String sql = "SELECT EXISTS ("
                + "SELECT 1 FROM `Attempt` a "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `TournamentRegister` reg ON reg.`registerId` = rp.`registerId` "
                + "WHERE a.`attemptId` = ? AND reg.`userId` = ?) "
                + "OR EXISTS ("
                + "SELECT 1 FROM `Attempt` a "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `Round` r ON r.`roundId` = rp.`roundId` "
                + "JOIN `Tournament` t ON t.`tournamentId` = r.`tournamentId` "
                + "WHERE a.`attemptId` = ? AND t.`userId` = ?) AS allowed";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setString(2, userId);
            ps.setString(3, attemptId);
            ps.setString(4, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() && rs.getBoolean("allowed");
            }
        }
    }

    public boolean isTournamentOwnerForAttempt(String attemptId, String userId) throws SQLException {
        String sql = "SELECT 1 FROM `Attempt` a "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `Round` r ON r.`roundId` = rp.`roundId` "
                + "JOIN `Tournament` t ON t.`tournamentId` = r.`tournamentId` "
                + "WHERE a.`attemptId` = ? AND t.`userId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next();
            }
        }
    }

    public String findTournamentIdForAttempt(String attemptId) throws SQLException {
        String sql = "SELECT r.`tournamentId` FROM `Attempt` a "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `Round` r ON r.`roundId` = rp.`roundId` WHERE a.`attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("tournamentId") : null;
            }
        }
    }

    public String findParticipantUserIdForAttempt(String attemptId) throws SQLException {
        String sql = "SELECT reg.`userId` FROM `Attempt` a "
                + "JOIN `RoundParticipant` rp ON rp.`roundParticipantId` = a.`roundParticipantId` "
                + "JOIN `TournamentRegister` reg ON reg.`registerId` = rp.`registerId` "
                + "WHERE a.`attemptId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? rs.getString("userId") : null;
            }
        }
    }

    public List<LeaderboardEntry> findTournamentLeaderboard(String tournamentId) throws SQLException {
        String sql = ENTRY_SELECT + "WHERE t.`tournamentId` = ? AND t.`status` = 'completed' "
                + "AND a.`endDateTime` IS NOT NULL AND tr.`rank_` IS NOT NULL "
                + "ORDER BY tr.`rank_`, tr.`score` DESC, attemptDurationMicros, u.`name`, a.`attemptId`";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                return readEntries(rs);
            }
        }
    }

    public List<LeaderboardEntry> findScoresForUser(String userId) throws SQLException {
        String sql = ENTRY_SELECT + "WHERE reg.`userId` = ? AND t.`status` = 'completed' "
                + "AND a.`endDateTime` IS NOT NULL AND tr.`rank_` IS NOT NULL "
                + "ORDER BY a.`endDateTime` DESC, t.`tournamentId`, tr.`rank_`";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return readEntries(rs);
            }
        }
    }

    private List<LeaderboardEntry> readEntries(ResultSet rs) throws SQLException {
        List<LeaderboardEntry> entries = new ArrayList<>();
        while (rs.next()) {
            Timestamp completedAt = rs.getTimestamp("completedAt");
            entries.add(new LeaderboardEntry(
                    rs.getString("attemptId"),
                    rs.getString("tournamentId"),
                    rs.getString("tournamentName"),
                    rs.getString("userId"),
                    rs.getString("username"),
                    rs.getString("name"),
                    rs.getInt("score"),
                    readNullableInteger(rs, "rank_"),
                    rs.getBigDecimal("prizeMoney"),
                    rs.getLong("attemptDurationMicros"),
                    completedAt == null ? null : completedAt.toLocalDateTime().toString()));
        }
        return entries;
    }

    private Integer readNullableInteger(ResultSet rs, String column) throws SQLException {
        int value = rs.getInt(column);
        return rs.wasNull() ? null : value;
    }
}
