package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.RoundParticipant;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class RoundParticipantDao {
    private static final String SELECT = "SELECT `roundParticipantId`, `roundId`, `registerId`, `qualifiedFromRoundId`, `status`, `qualifiedDate` FROM `RoundParticipant`";

    public List<RoundParticipant> findAll() throws SQLException {
        List<RoundParticipant> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public RoundParticipant findById(String roundParticipantId) throws SQLException {
        String sql = SELECT + " WHERE `roundParticipantId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roundParticipantId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public RoundParticipant createForRegisteredUser(RoundParticipant entity, String userId) throws SQLException {
        String roundSql = "SELECT `tournamentId`, `roundNumber` FROM `Round` WHERE `roundId` = ? FOR UPDATE";
        String registrationSql = "SELECT 1 FROM `TournamentRegister` WHERE `registerId` = ? "
                + "AND `tournamentId` = ? AND `userId` = ? AND `status` = 'confirmed'";
        String previousRoundSql = "SELECT rp.`roundId` FROM `RoundParticipant` rp "
                + "JOIN `Round` previousRound ON previousRound.`roundId` = rp.`roundId` "
                + "WHERE rp.`registerId` = ? AND previousRound.`tournamentId` = ? "
                + "AND previousRound.`roundNumber` = ? AND rp.`status` = 'qualified' FOR UPDATE";
        String insertSql = "INSERT INTO `RoundParticipant` "
                + "(`roundParticipantId`, `roundId`, `registerId`, `qualifiedFromRoundId`, `status`, `qualifiedDate`) "
                + "VALUES (?, ?, ?, ?, NULL, NULL)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String tournamentId;
                int roundNumber;
                try (PreparedStatement ps = conn.prepareStatement(roundSql)) {
                    ps.setString(1, entity.getRoundId());
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("roundId does not identify an existing round");
                        tournamentId = rs.getString("tournamentId");
                        roundNumber = rs.getInt("roundNumber");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(registrationSql)) {
                    ps.setString(1, entity.getRegisterId());
                    ps.setString(2, tournamentId);
                    ps.setString(3, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException(
                                "You must have a confirmed registration in this tournament");
                    }
                }

                String qualifiedFromRoundId = null;
                if (roundNumber > 1) {
                    try (PreparedStatement ps = conn.prepareStatement(previousRoundSql)) {
                        ps.setString(1, entity.getRegisterId());
                        ps.setString(2, tournamentId);
                        ps.setInt(3, roundNumber - 1);
                        try (ResultSet rs = ps.executeQuery()) {
                            if (!rs.next()) throw new IllegalArgumentException(
                                    "Participant did not qualify from the previous round");
                            qualifiedFromRoundId = rs.getString("roundId");
                        }
                    }
                } else if (roundNumber != 1) {
                    throw new IllegalArgumentException("roundNumber must start at 1");
                }

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, entity.getRoundParticipantId());
                    ps.setString(2, entity.getRoundId());
                    ps.setString(3, entity.getRegisterId());
                    ps.setString(4, qualifiedFromRoundId);
                    ps.executeUpdate();
                }
                conn.commit();
                entity.setQualifiedFromRoundId(qualifiedFromRoundId);
                entity.setStatus(null);
                entity.setQualifiedDate(null);
            } catch (SQLException | RuntimeException e) {
                try { conn.rollback(); } catch (SQLException rollbackError) { e.addSuppressed(rollbackError); }
                throw e;
            }
        }
        return entity;
    }

    public RoundParticipant update(String roundParticipantId, RoundParticipant entity) throws SQLException {
        String sql = "UPDATE `RoundParticipant` SET `roundId` = ?, `registerId` = ?, `qualifiedFromRoundId` = ?, `status` = ?, `qualifiedDate` = ? WHERE `roundParticipantId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getRoundId());
            ps.setString(2, entity.getRegisterId());
            ps.setString(3, entity.getQualifiedFromRoundId());
            ps.setString(4, entity.getStatus());
            setDateTime(ps, 5, entity.getQualifiedDate());
            ps.setString(6, roundParticipantId);
            ps.executeUpdate();
        }
        return findById(roundParticipantId);
    }

    public boolean delete(String roundParticipantId) throws SQLException {
        String sql = "DELETE FROM `RoundParticipant` WHERE `roundParticipantId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, roundParticipantId);
            return ps.executeUpdate() > 0;
        }
    }

    private RoundParticipant mapRow(ResultSet rs) throws SQLException {
        RoundParticipant entity = new RoundParticipant();
        entity.setRoundParticipantId(rs.getString("roundParticipantId"));
        entity.setRoundId(rs.getString("roundId"));
        entity.setRegisterId(rs.getString("registerId"));
        entity.setQualifiedFromRoundId(rs.getString("qualifiedFromRoundId"));
        entity.setStatus(rs.getString("status"));
        entity.setQualifiedDate(readLocalDateTime(rs, "qualifiedDate"));
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
