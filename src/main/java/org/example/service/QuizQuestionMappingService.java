package org.example.service;

import org.example.dao.QuizQuestionMappingDao;
import org.example.model.QuizQuestionMapping;
import java.time.LocalDateTime;

import java.sql.SQLException;
import java.util.List;

public class QuizQuestionMappingService {
    private final QuizQuestionMappingDao quizQuestionMappingDao;

    public QuizQuestionMappingService() {
        this(new QuizQuestionMappingDao());
    }

    QuizQuestionMappingService(QuizQuestionMappingDao dao) {
        this.quizQuestionMappingDao = dao;
    }

    public List<QuizQuestionMapping> findAll() throws SQLException {
        return quizQuestionMappingDao.findAll();
    }

    public QuizQuestionMapping findById(String questionId) throws SQLException {
        return quizQuestionMappingDao.findById(questionId);
    }

    public QuizQuestionMapping create(QuizQuestionMapping entity) throws SQLException {
        validate(entity, true);
        return quizQuestionMappingDao.create(entity);
    }

    public QuizQuestionMapping update(String quizId, String questionId, QuizQuestionMapping entity) throws SQLException {
        validate(entity, false);
        if (quizQuestionMappingDao.findById(questionId) == null) return null;
        return quizQuestionMappingDao.update(quizId, questionId, entity);
    }

    public boolean delete(String quizId, String questionId) throws SQLException {
        return quizQuestionMappingDao.delete(quizId, questionId);
    }

    private void validate(QuizQuestionMapping entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("QuizQuestionMapping body is required");
        if (creating) {
            requireId("quizId", entity.getQuizId());
            requireId("questionId", entity.getQuestionId());
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
