package org.example.service;

import org.example.dao.TournamentAnswerDao;
import org.example.dto.TournamentAnswerRequest;
import org.example.model.TournamentAnswer;
import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class TournamentAnswerService {
    private final TournamentAnswerDao tournamentAnswerDao;

    public TournamentAnswerService() {
        this(new TournamentAnswerDao());
    }

    TournamentAnswerService(TournamentAnswerDao dao) {
        this.tournamentAnswerDao = dao;
    }

    public List<TournamentAnswer> findAll() throws SQLException {
        return tournamentAnswerDao.findAll();
    }

    public TournamentAnswer findById(String attemptId, String questionId) throws SQLException {
        return tournamentAnswerDao.findById(attemptId, questionId);
    }

    public boolean questionBelongsToOpenAttempt(String attemptId, String questionId) throws SQLException {
        return tournamentAnswerDao.questionBelongsToOpenAttempt(attemptId, questionId);
    }

    public List<TournamentAnswer> findAllByAttemptId(String attemptId) throws SQLException {
        return tournamentAnswerDao.findAllByAttemptId(attemptId);
    }

    public int findScore(String attemptId) throws SQLException {
        return tournamentAnswerDao.findScore(attemptId);
    }

    public TournamentAnswer saveAnswer(String attemptId, TournamentAnswerRequest entity, Boolean answer) throws SQLException {
        if (attemptId == null || attemptId.isBlank()) throw new IllegalArgumentException("attemptId is required");
        if (entity == null) throw new IllegalArgumentException("Answer body is required");
        requireId("questionId", entity.questionId());
        requireId("optionId", entity.optionId());
        if (answer == null) throw new IllegalArgumentException("Selected option does not belong to question");
        return tournamentAnswerDao.createOrUpdate(attemptId, entity, answer);
    }

    public TournamentAnswer update(String attemptId, String questionId, TournamentAnswer entity) throws SQLException {
        validate(entity, false);
        if (tournamentAnswerDao.findById(attemptId, questionId) == null) return null;
        return tournamentAnswerDao.update(attemptId, questionId, entity);
    }

    public boolean delete(String attemptId, String questionId) throws SQLException {
        return tournamentAnswerDao.delete(attemptId, questionId);
    }

    private void validate(TournamentAnswer entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("TournamentAnswer body is required");
        if (creating) {
            requireId("attemptId", entity.getAttemptId());
            requireId("questionId", entity.getQuestionId());
        }
        requireId("selectedOptionId", entity.getSelectedOptionId());
    }

    private void requireId(String field, String value) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(field + " is required");
        if (value.trim().length() > 36) throw new IllegalArgumentException(field + " must be at most 36 characters");
    }

}
