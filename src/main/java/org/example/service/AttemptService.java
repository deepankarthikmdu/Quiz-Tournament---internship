package org.example.service;

import org.example.dao.AttemptDao;
import org.example.model.Attempt;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class AttemptService {
    private final AttemptDao attemptDao;

    public AttemptService() {
        this(new AttemptDao());
    }

    AttemptService(AttemptDao dao) {
        this.attemptDao = dao;
    }

    public List<Attempt> findAll() throws SQLException {
        return attemptDao.findAll();
    }

    public Attempt findById(String attemptId) throws SQLException {
        return attemptDao.findById(attemptId);
    }

    public String authenticateUser (String attemptId) throws SQLException{
        return attemptDao.authenticateUser(attemptId);
    }

    public Attempt create(Attempt entity) throws SQLException {
        validate(entity, true);
        return attemptDao.create(entity);
    }

    public Attempt update(String attemptId, Attempt entity) throws SQLException {
        validate(entity, false);
        if (attemptDao.findById(attemptId) == null) return null;
        return attemptDao.update(attemptId, entity);
    }

    public boolean delete(String attemptId) throws SQLException {
        return attemptDao.delete(attemptId);
    }

    public boolean authenticateAttemptEndTimestamp (String attemptId) throws SQLException {
        return attemptDao.authenticateAttemptEndTimestamp(attemptId);
    }

    public boolean authenticateAttemptStartTimestamp (String attemptId) throws SQLException {
        return attemptDao.authenticateAttemptStartTimestamp(attemptId);
    }

    public boolean setStartAttemptTimestamp (String attemptId) throws SQLException {
        return attemptDao.setStartAttemptTimestamp(attemptId);
    }

    public boolean setEndAttemptTimestamp (String attemptId) throws SQLException {
        return attemptDao.setEndAttemptTimestamp(attemptId);
    }

    private void validate(Attempt entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("Attempt body is required");
        if (creating) {
            requireId("attemptId", entity.getAttemptId());
            requireId("roundParticipantId", entity.getRoundParticipantId());
            return;
        }
        requireId("roundParticipantId", entity.getRoundParticipantId());
        if (entity.getEndDateTime() != null && entity.getStartAttemptDateTime() == null) {
            throw new IllegalArgumentException("startAttemptDateTime is required when endDateTime is set");
        }
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
