package org.example.service;

import org.example.dao.RoundDao;
import org.example.model.Round;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class RoundService {
    private final RoundDao roundDao;

    public RoundService() {
        this(new RoundDao());
    }

    RoundService(RoundDao dao) {
        this.roundDao = dao;
    }

    public List<Round> findAll() throws SQLException {
        return roundDao.findAll();
    }

    public Round findById(String roundId) throws SQLException {
        return roundDao.findById(roundId);
    }

    public List<Round> findAllForOwner(String ownerUserId) throws SQLException {
        return roundDao.findAllForOwner(ownerUserId);
    }

    public Round findByIdForOwner(String roundId, String ownerUserId) throws SQLException {
        return roundDao.findByIdForOwner(roundId, ownerUserId);
    }

    public Round create(Round entity) throws SQLException {
        validate(entity, true);
        return roundDao.create(entity);
    }

    public Round update(String roundId, Round entity) throws SQLException {
        return update(roundId, entity, entity == null ? null : entity.getQualificationThreshold());
    }

    public Round update(String roundId, Round entity, Integer qualificationThreshold) throws SQLException {
        if (entity != null) entity.setQualificationThreshold(qualificationThreshold);
        validate(entity, false);
        if (roundDao.findById(roundId) == null) return null;
        return roundDao.update(roundId, entity, qualificationThreshold);
    }

    public boolean delete(String roundId) throws SQLException {
        return roundDao.delete(roundId);
    }

    private void validate(Round entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("Round body is required");
        if (creating) requireId("roundId", entity.getRoundId());
        requireId("tournamentId", entity.getTournamentId());
        requireId("quizId", entity.getQuizId());
        if (entity.getQualificationThreshold() != null && entity.getQualificationThreshold() < 0) {
            throw new IllegalArgumentException("qualificationThreshold must be zero or greater");
        }
        if ("completed".equalsIgnoreCase(entity.getStatus()) && entity.getQualificationThreshold() == null) {
            throw new IllegalArgumentException("qualificationThreshold is required when completing a round");
        }
        requireEnum("status", entity.getStatus(), "upcoming", "open", "ongoing", "completed");
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
