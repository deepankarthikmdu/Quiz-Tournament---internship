package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Round;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.util.ArrayList;
import java.util.List;

public class RoundDao {
    private static final String SELECT = "SELECT `roundId`, `tournamentId`, `quizId`, `roundNumber`, `name`, `startDate`, `endDate`, `status` FROM `Round`";
    private static final String SELECT_FOR_OWNER = "SELECT r.`roundId`, r.`tournamentId`, r.`quizId`, r.`roundNumber`, r.`name`, r.`startDate`, r.`endDate`, r.`status` "
            + "FROM `Round` r JOIN `Tournament` t ON t.`tournamentId` = r.`tournamentId` WHERE t.`userId` = ?";

    public List<Round> findAll() throws SQLException {
        List<Round> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public Round findById(String roundId) throws SQLException {
        String sql = SELECT + " WHERE `roundId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public List<Round> findAllForOwner(String ownerUserId) throws SQLException {
        List<Round> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_FOR_OWNER)) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) entities.add(mapRow(rs));
            }
        }
        return entities;
    }

    public Round findByIdForOwner(String roundId, String ownerUserId) throws SQLException {
        String sql = SELECT_FOR_OWNER + " AND r.`roundId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUserId);
            ps.setString(2, roundId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Round create(Round entity) throws SQLException {
        String sql = "INSERT INTO `Round` (`roundId`, `tournamentId`, `quizId`, `roundNumber`, `name`, `startDate`, `endDate`, `status`) VALUES (?, ?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getRoundId());
            ps.setString(2, entity.getTournamentId());
            ps.setString(3, entity.getQuizId());
            ps.setInt(4, entity.getRoundNumber());
            ps.setString(5, entity.getName());
            setDateTime(ps, 6, entity.getStartDate());
            setDateTime(ps, 7, entity.getEndDate());
            ps.setString(8, entity.getStatus());
            ps.executeUpdate();
        }
        return entity;
    }

    public Round update(String roundId, Round entity) throws SQLException {
        return update(roundId, entity, entity.getQualificationThreshold());
    }

    public Round update(String roundId, Round entity, Integer qualificationThreshold) throws SQLException {
        String sql = "UPDATE `Round` SET `tournamentId` = ?, `quizId` = ?, `roundNumber` = ?, `name` = ?, `startDate` = ?, `endDate` = ?, `status` = ? WHERE `roundId` = ?";
        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(sql)) {
                    ps.setString(1, entity.getTournamentId());
                    ps.setString(2, entity.getQuizId());
                    ps.setInt(3, entity.getRoundNumber());
                    ps.setString(4, entity.getName());
                    setDateTime(ps, 5, entity.getStartDate());
                    setDateTime(ps, 6, entity.getEndDate());
                    ps.setString(7, entity.getStatus());
                    ps.setString(8, roundId);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return null;
                    }
                }

                if ("completed".equalsIgnoreCase(entity.getStatus())) {
                    String scoresSql = "SELECT rp.`roundParticipantId`, MAX(tr.`score`) AS score "
                            + "FROM `RoundParticipant` rp "
                            + "LEFT JOIN `Attempt` a ON a.`roundParticipantId` = rp.`roundParticipantId` "
                            + "LEFT JOIN `TournamentResult` tr ON tr.`attemptId` = a.`attemptId` "
                            + "WHERE rp.`roundId` = ? GROUP BY rp.`roundParticipantId` FOR UPDATE";
                    String qualifySql = "UPDATE `RoundParticipant` SET `status` = ?, `qualifiedDate` = ? "
                            + "WHERE `roundParticipantId` = ?";
                    try (PreparedStatement scores = conn.prepareStatement(scoresSql);
                         PreparedStatement qualify = conn.prepareStatement(qualifySql)) {
                        scores.setString(1, roundId);
                        try (ResultSet rs = scores.executeQuery()) {
                            while (rs.next()) {
                                int score = rs.getInt("score");
                                boolean hasScore = !rs.wasNull();
                                boolean passed = hasScore && score > qualificationThreshold;
                                qualify.setString(1, passed ? "qualified" : "eliminated");
                                if (passed) qualify.setTimestamp(2, Timestamp.valueOf(LocalDateTime.now()));
                                else qualify.setNull(2, java.sql.Types.TIMESTAMP);
                                qualify.setString(3, rs.getString("roundParticipantId"));
                                qualify.addBatch();
                            }
                        }
                        qualify.executeBatch();
                    }
                }
                conn.commit();
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                throw e;
            }
        }
        return findById(roundId);
    }

    public boolean delete(String roundId) throws SQLException {
        String sql = "DELETE FROM `Round` WHERE `roundId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roundId);
            return ps.executeUpdate() > 0;
        }
    }

    private Round mapRow(ResultSet rs) throws SQLException {
        Round entity = new Round();
        entity.setRoundId(rs.getString("roundId"));
        entity.setTournamentId(rs.getString("tournamentId"));
        entity.setQuizId(rs.getString("quizId"));
        entity.setRoundNumber(rs.getInt("roundNumber"));
        entity.setName(rs.getString("name"));
        entity.setStartDate(readLocalDateTime(rs, "startDate"));
        entity.setEndDate(readLocalDateTime(rs, "endDate"));
        entity.setStatus(rs.getString("status"));
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
