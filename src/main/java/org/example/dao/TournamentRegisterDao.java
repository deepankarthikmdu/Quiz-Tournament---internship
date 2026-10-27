package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.TournamentRegister;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class TournamentRegisterDao {
    private static final String SELECT = "SELECT `registerId`, `tournamentId`, `userId`, `paymentId`, `status`, `registeredDate` FROM `TournamentRegister`";

    public List<TournamentRegister> findAll() throws SQLException {
        List<TournamentRegister> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public TournamentRegister findById(String registerId) throws SQLException {
        String sql = SELECT + " WHERE `registerId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, registerId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public boolean createWithCapacity(TournamentRegister entity) throws SQLException {
        String incrementSql = "UPDATE `Tournament` SET `participantCount` = `participantCount` + 1 "
                + "WHERE `tournamentId` = ? AND `participantCount` < `participantLimit`";
        String insertSql = "INSERT INTO `TournamentRegister` (`registerId`, `tournamentId`, `userId`, `paymentId`, `status`, `registeredDate`) VALUES (?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement ps = conn.prepareStatement(incrementSql)) {
                    ps.setString(1, entity.getTournamentId());
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(insertSql)) {
                    ps.setString(1, entity.getRegisterId());
                    ps.setString(2, entity.getTournamentId());
                    ps.setString(3, entity.getUserId());
                    ps.setString(4, entity.getPaymentId());
                    ps.setString(5, entity.getStatus());
                    setDateTime(ps, 6, entity.getRegisteredDate());
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    public TournamentRegister update(String registerId, TournamentRegister entity) throws SQLException {
        String sql = "UPDATE `TournamentRegister` SET `tournamentId` = ?, `userId` = ?, `paymentId` = ?, `status` = ?, `registeredDate` = ? WHERE `registerId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getTournamentId());
            ps.setString(2, entity.getUserId());
            ps.setString(3, entity.getPaymentId());
            ps.setString(4, entity.getStatus());
            setDateTime(ps, 5, entity.getRegisteredDate());
            ps.setString(6, registerId);
            ps.executeUpdate();
        }
        return findById(registerId);
    }

    public boolean delete(String registerId) throws SQLException {
        String sql = "DELETE FROM `TournamentRegister` WHERE `registerId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, registerId);
            return ps.executeUpdate() > 0;
        }
    }

    public boolean deleteForOwnerAndDecrement(String registerId, String ownerUserId) throws SQLException {
        String selectSql = "SELECT `tournamentId` FROM `TournamentRegister` "
                + "WHERE `registerId` = ? AND `userId` = ? FOR UPDATE";
        String deleteSql = "DELETE FROM `TournamentRegister` WHERE `registerId` = ? AND `userId` = ?";
        String decrementSql = "UPDATE `Tournament` SET `participantCount` = GREATEST(`participantCount` - 1, 0) "
                + "WHERE `tournamentId` = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                String tournamentId;
                try (PreparedStatement ps = conn.prepareStatement(selectSql)) {
                    ps.setString(1, registerId);
                    ps.setString(2, ownerUserId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return false;
                        }
                        tournamentId = rs.getString("tournamentId");
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(deleteSql)) {
                    ps.setString(1, registerId);
                    ps.setString(2, ownerUserId);
                    if (ps.executeUpdate() == 0) {
                        conn.rollback();
                        return false;
                    }
                }

                try (PreparedStatement ps = conn.prepareStatement(decrementSql)) {
                    ps.setString(1, tournamentId);
                    ps.executeUpdate();
                }

                conn.commit();
                return true;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    private TournamentRegister mapRow(ResultSet rs) throws SQLException {
        TournamentRegister entity = new TournamentRegister();
        entity.setRegisterId(rs.getString("registerId"));
        entity.setTournamentId(rs.getString("tournamentId"));
        entity.setUserId(rs.getString("userId"));
        entity.setPaymentId(rs.getString("paymentId"));
        entity.setStatus(rs.getString("status"));
        entity.setRegisteredDate(readLocalDateTime(rs, "registeredDate"));
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
