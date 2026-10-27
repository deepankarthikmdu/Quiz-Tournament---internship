package org.example.service;

import org.example.dao.QuizDao;
import org.example.model.Quiz;

import java.sql.SQLException;
import java.util.List;

/** Application rules and workflows for quizzes. */
public class QuizService {

    private final QuizDao quizDao;

    public QuizService() {
        this(new QuizDao());
    }

    QuizService(QuizDao quizDao) {
        this.quizDao = quizDao;
    }

    public List<Quiz> findAll() throws SQLException {
        return quizDao.findAll();
    }

    public List<Quiz> findAllForOwner(String ownerUserId) throws SQLException {
        return quizDao.findAllForOwner(ownerUserId);
    }

    public Quiz findById(String quizId) throws SQLException {
        return quizDao.findById(quizId);
    }

    public Quiz findByIdForOwner(String quizId, String ownerUserId) throws SQLException {
        return quizDao.findByIdForOwner(quizId, ownerUserId);
    }

    public Quiz create(String ownerUserId, Quiz quiz) throws SQLException {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        if (quiz == null) {
            throw new IllegalArgumentException("Quiz body is required");
        }
        quiz.setUserId(ownerUserId);
        validate(quiz, true);
        return quizDao.createQuiz(quiz);
    }

    public Quiz update(String ownerUserId, String quizId, Quiz quiz) throws SQLException {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        if (quiz == null) {
            throw new IllegalArgumentException("Quiz body is required");
        }
        Quiz existing = quizDao.findById(quizId);
        if (existing == null || !existing.getUserId().equals(ownerUserId)) {
            return null;
        }
        quiz.setUserId(existing.getUserId());
        validate(quiz, false);
        return quizDao.update(quizId, quiz);
    }

    public boolean delete(String ownerUserId, String quizId) throws SQLException {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        Quiz existing = quizDao.findById(quizId);
        if (existing == null || !existing.getUserId().equals(ownerUserId)) {
            return false;
        }
        return quizDao.delete(quizId);
    }

    private void validate(Quiz quiz, boolean isCreate) {
        if (quiz == null) {
            throw new IllegalArgumentException("Quiz body is required");
        }
        if (isCreate && (quiz.getQuizId() == null || quiz.getQuizId().isBlank())) {
            throw new IllegalArgumentException("quizId is required");
        }
        if (isCreate && (quiz.getUserId() == null || quiz.getUserId().isBlank())) {
            throw new IllegalArgumentException("userId is required");
        }
        if (isCreate && quiz.getUserId().trim().length() > 255) {
            throw new IllegalArgumentException("userId must be at most 255 characters");
        }
        if (isCreate && quiz.getQuizId().trim().length() > 36) {
            throw new IllegalArgumentException("quizId must be at most 36 characters");
        }
        if (quiz.getQuizName() == null || quiz.getQuizName().isBlank()) {
            throw new IllegalArgumentException("quizName is required");
        }
        if (isCreate) {
            quiz.setQuizId(quiz.getQuizId().trim());
        }
        quiz.setUserId(quiz.getUserId().trim());
        quiz.setQuizName(quiz.getQuizName().trim());
        if (quiz.getQuizCategory() != null) {
            quiz.setQuizCategory(quiz.getQuizCategory().trim());
        }
    }
}
