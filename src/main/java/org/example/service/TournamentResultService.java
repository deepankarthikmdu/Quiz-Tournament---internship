package org.example.service;

import org.example.dao.TournamentResultDao;
import org.example.model.TournamentResult;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class TournamentResultService {
    private final TournamentResultDao tournamentResultDao;

    public TournamentResultService() {
        this(new TournamentResultDao());
    }

    TournamentResultService(TournamentResultDao dao) {
        this.tournamentResultDao = dao;
    }

    public List<TournamentResult> findAll() throws SQLException {
        return tournamentResultDao.findAll();
    }

    public List<TournamentResult> findForUser(String userId) throws SQLException {
        return tournamentResultDao.findForUser(userId);
    }

    public TournamentResult findById(String attemptId) throws SQLException {
        return tournamentResultDao.findById(attemptId);
    }

    public TournamentResult create(String attemptId, int score) throws SQLException {
        return tournamentResultDao.create(attemptId, score);
    }

    public TournamentResult update(String attemptId, TournamentResult entity) throws SQLException {
        validate(entity, false);
        if (tournamentResultDao.findById(attemptId) == null) return null;
        return tournamentResultDao.update(attemptId, entity);
    }

    public boolean delete(String attemptId) throws SQLException {
        return tournamentResultDao.delete(attemptId);
    }

    private void validate(TournamentResult entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("TournamentResult body is required");
        if (creating) requireId("attemptId", entity.getAttemptId());
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
