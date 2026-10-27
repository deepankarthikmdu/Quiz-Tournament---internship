package org.example.service;

import org.example.dao.RoundParticipantDao;
import org.example.model.RoundParticipant;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class RoundParticipantService {
    private final RoundParticipantDao roundParticipantDao;

    public RoundParticipantService() {
        this(new RoundParticipantDao());
    }

    RoundParticipantService(RoundParticipantDao dao) {
        this.roundParticipantDao = dao;
    }

    public List<RoundParticipant> findAll() throws SQLException {
        return roundParticipantDao.findAll();
    }

    public RoundParticipant findById(String roundParticipantId) throws SQLException {
        return roundParticipantDao.findById(roundParticipantId);
    }

    public RoundParticipant create(String userId, RoundParticipant entity) throws SQLException {
        if (userId == null || userId.isBlank()) throw new IllegalArgumentException("Authenticated user is required");
        validate(entity, true);
        return roundParticipantDao.createForRegisteredUser(entity, userId);
    }

    public RoundParticipant update(String roundParticipantId, RoundParticipant entity) throws SQLException {
        validate(entity, false);
        if (roundParticipantDao.findById(roundParticipantId) == null) return null;
        return roundParticipantDao.update(roundParticipantId, entity);
    }

    public boolean delete(String roundParticipantId) throws SQLException {
        return roundParticipantDao.delete(roundParticipantId);
    }

    private void validate(RoundParticipant entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("RoundParticipant body is required");
        if (creating) requireId("roundParticipantId", entity.getRoundParticipantId());
        requireId("roundId", entity.getRoundId());
        requireId("registerId", entity.getRegisterId());
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
