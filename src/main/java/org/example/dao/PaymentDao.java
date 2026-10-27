package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Payment;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class PaymentDao {
    private static final String SELECT = "SELECT `paymentId`, `userId`, `amount`, `description`, `paymentDate`, `status`, `paymentGateway` FROM `Payment`";

    public List<Payment> findAll() throws SQLException {
        List<Payment> entities = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT);
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) entities.add(mapRow(rs));
        }
        return entities;
    }

    public List<Payment> findAllForOwner (String ownerUserId) throws SQLException {
        String sql = SELECT + " WHERE userId = ? ";
        List<Payment> entities = new ArrayList<>();
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1,ownerUserId);
            try(ResultSet rs = ps.executeQuery()){
                while(rs.next()) entities.add((mapRow(rs)));
            }
        }
        return entities;
    }

    public Payment findById(String paymentId) throws SQLException {
        String sql = SELECT + " WHERE `paymentId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Payment create(Payment entity) throws SQLException {
        String sql = "INSERT INTO `Payment` (`paymentId`, `userId`, `amount`, `description`, `paymentDate`, `status`, `paymentGateway`) VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getPaymentId());
            ps.setString(2, entity.getUserId());
            ps.setBigDecimal(3, entity.getAmount());
            ps.setString(4, entity.getDescription());
            setDateTime(ps, 5, entity.getPaymentDate());
            ps.setString(6, entity.getStatus());
            ps.setString(7, entity.getPaymentGateway());
            ps.executeUpdate();
        }
        return entity;
    }

    public Payment startForRegistration(String userId, String paymentId, String registerId, String roundParticipantId) throws SQLException {
        String registrationSql = "SELECT tr.`tournamentId`, tr.`paymentId`, tr.`status`, t.`entryFee`, t.`name` "
                + "FROM `TournamentRegister` tr JOIN `Tournament` t ON t.`tournamentId` = tr.`tournamentId` "
                + "WHERE tr.`registerId` = ? AND tr.`userId` = ? FOR UPDATE";
        try (Connection conn = DatabaseConnection.getConnection()) {
            boolean oldAutoCommit = conn.getAutoCommit();
            conn.setAutoCommit(false);
            try {
                BigDecimal amount;
                String description;
                String tournamentId;
                try (PreparedStatement ps = conn.prepareStatement(registrationSql)) {
                    ps.setString(1, registerId);
                    ps.setString(2, userId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Registration not found");
                        if (rs.getString("paymentId") != null) throw new IllegalArgumentException("Registration already has a payment");
                        if (!"pending".equals(rs.getString("status"))) throw new IllegalArgumentException("Registration is not pending");
                        amount = rs.getBigDecimal("entryFee");
                        description = "Tournament entry: " + rs.getString("name");
                        tournamentId = rs.getString("tournamentId");
                    }
                }
                if (amount == null) amount = BigDecimal.ZERO;

                Payment payment = new Payment();
                payment.setPaymentId(paymentId);
                payment.setUserId(userId);
                payment.setAmount(amount);
                payment.setDescription(description);
                payment.setPaymentDate(LocalDateTime.now());
                payment.setStatus("success");
                payment.setPaymentGateway("razorpay");
                String insert = "INSERT INTO `Payment` (`paymentId`, `userId`, `amount`, `description`, `paymentDate`, `status`, `paymentGateway`) VALUES (?, ?, ?, ?, ?, ?, ?)";
                try (PreparedStatement ps = conn.prepareStatement(insert)) {
                    ps.setString(1, payment.getPaymentId());
                    ps.setString(2, payment.getUserId());
                    ps.setBigDecimal(3, payment.getAmount());
                    ps.setString(4, payment.getDescription());
                    setDateTime(ps, 5, payment.getPaymentDate());
                    ps.setString(6, payment.getStatus());
                    ps.setString(7, payment.getPaymentGateway());
                    ps.executeUpdate();
                }
                try (PreparedStatement ps = conn.prepareStatement("UPDATE `TournamentRegister` SET `paymentId` = ?, `status` = 'confirmed' WHERE `registerId` = ? AND `userId` = ? AND `paymentId` IS NULL")) {
                    ps.setString(1, payment.getPaymentId());
                    ps.setString(2, registerId);
                    ps.setString(3, userId);
                    if (ps.executeUpdate() != 1) throw new SQLException("Registration changed while payment was starting");
                }

                String firstRoundId;
                try (PreparedStatement ps = conn.prepareStatement(
                        "SELECT `roundId` FROM `Round` WHERE `tournamentId` = ? ORDER BY `roundNumber` ASC LIMIT 1")) {
                    ps.setString(1, tournamentId);
                    try (ResultSet rs = ps.executeQuery()) {
                        if (!rs.next()) throw new IllegalArgumentException("Tournament has no rounds configured");
                        firstRoundId = rs.getString("roundId");
                    }
                }

                String participantSql = "INSERT INTO `RoundParticipant` "
                        + "(`roundParticipantId`, `roundId`, `registerId`, `qualifiedFromRoundId`, `status`, `qualifiedDate`) "
                        + "VALUES (?, ?, ?, NULL, NULL, NULL)";
                try (PreparedStatement ps = conn.prepareStatement(participantSql)) {
                    ps.setString(1, roundParticipantId);
                    ps.setString(2, firstRoundId);
                    ps.setString(3, registerId);
                    ps.executeUpdate();
                }
                conn.commit();
                return payment;
            } catch (SQLException | RuntimeException e) {
                conn.rollback();
                throw e;
            } finally {
                conn.setAutoCommit(oldAutoCommit);
            }
        }
    }

    public Payment findByIdForUser(String paymentId, String userId) throws SQLException {
        String sql = SELECT + " WHERE `paymentId` = ? AND `userId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentId);
            ps.setString(2, userId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Payment update(String paymentId, Payment entity) throws SQLException {
        String sql = "UPDATE `Payment` SET `userId` = ?, `amount` = ?, `description` = ?, `paymentDate` = ?, `status` = ?, `paymentGateway` = ? WHERE `paymentId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, entity.getUserId());
            ps.setBigDecimal(2, entity.getAmount());
            ps.setString(3, entity.getDescription());
            setDateTime(ps, 4, entity.getPaymentDate());
            ps.setString(5, entity.getStatus());
            ps.setString(6, entity.getPaymentGateway());
            ps.setString(7, paymentId);
            ps.executeUpdate();
        }
        return findById(paymentId);
    }

    public boolean delete(String paymentId) throws SQLException {
        String sql = "DELETE FROM `Payment` WHERE `paymentId` = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, paymentId);
            return ps.executeUpdate() > 0;
        }
    }

    private Payment mapRow(ResultSet rs) throws SQLException {
        Payment entity = new Payment();
        entity.setPaymentId(rs.getString("paymentId"));
        entity.setUserId(rs.getString("userId"));
        entity.setAmount(rs.getBigDecimal("amount"));
        entity.setDescription(rs.getString("description"));
        entity.setPaymentDate(readLocalDateTime(rs, "paymentDate"));
        entity.setStatus(rs.getString("status"));
        entity.setPaymentGateway(rs.getString("paymentGateway"));
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
