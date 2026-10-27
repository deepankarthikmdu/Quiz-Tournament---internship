package org.example.service;

import org.example.dao.PaymentDao;
import org.example.dao.UserDao;
import org.example.model.Payment;
import org.example.model.User;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class PaymentService {
    private final PaymentDao paymentDao;
    private final UserDao userDao;
    public PaymentService() { this(new UserDao(), new PaymentDao());}

    PaymentService(UserDao userDao,PaymentDao paymentDao) {
        this.paymentDao = paymentDao;
        this.userDao = userDao;
    }

    public List<Payment> findAll() throws SQLException {
        return paymentDao.findAll();
    }

    public Payment findById(String paymentId) throws SQLException {
        return paymentDao.findById(paymentId);
    }

    public List<Payment> findAllForOwner(String ownerUserId) throws SQLException {
        return paymentDao.findAllForOwner(ownerUserId);
    }

    public Payment startForRegistration(String userId, String paymentId, String registerId, String roundParticipantId) throws SQLException {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("login required");
        if (registerId == null || registerId.isBlank() || registerId.trim().length() > 36)
            throw new IllegalArgumentException("registerId is invalid");

        return paymentDao.startForRegistration(userId, paymentId, registerId.trim(), roundParticipantId);
    }

    public Payment findByIdForUser(String paymentId, String userId) throws SQLException {
        return paymentDao.findByIdForUser(paymentId, userId);
    }

    public Payment create(String ownerUserId, Payment entity) throws SQLException {
        if(ownerUserId == null || ownerUserId.isBlank()){
            throw new IllegalArgumentException("authenticated user is required");
        }

        if(entity == null){
            throw new IllegalArgumentException("payment body required");
        }
        entity.setUserId(ownerUserId);
        User owner = userDao.findById(ownerUserId);

        if(owner == null){
            throw new IllegalArgumentException("userId does not identify any existing user !");
        }

        validate(entity, true);
        return paymentDao.create(entity);
    }

    public Payment update(String paymentId, Payment entity) throws SQLException {
        validate(entity, false);
        if (paymentDao.findById(paymentId) == null) return null;
        return paymentDao.update(paymentId, entity);
    }

    public boolean delete(String paymentId) throws SQLException {
        return paymentDao.delete(paymentId);
    }

    private void validate(Payment entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("Payment body is required");
        if (creating) requireId("paymentId", entity.getPaymentId());
        requireId("userId", entity.getUserId());
        if (entity.getAmount() == null) throw new IllegalArgumentException("amount is required");
        if (entity.getDescription() != null && entity.getDescription().length() > 255) throw new IllegalArgumentException("description must be at most 255 characters");
        requireEnum("status", entity.getStatus(), "pending", "success", "failed", "refunded");
    }

    private void requireId(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (value.trim().length() > 36) throw new IllegalArgumentException(field + " must be at most 36 characters");
    }

    private void requireText(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private void requireDateTime(String field, LocalDateTime value) {
        if (value == null) throw new IllegalArgumentException(field + " is required");
    }

    private void requireEnum(String field, String value, String... allowed) {
        if (value == null) return;
        for (String option : allowed) if (option.equals(value)) return;
        throw new IllegalArgumentException(field + " must be one of: " + String.join(", ", allowed));
    }
}
