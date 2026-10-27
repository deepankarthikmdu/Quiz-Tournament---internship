package org.example.service;

import org.example.dao.QuestionDao;
import org.example.model.Question;

import java.sql.SQLException;
import java.util.List;

public class QuestionService {
    private final QuestionDao questionDao;

    public QuestionService (){ this (new QuestionDao()); }

    QuestionService (QuestionDao questionDao) { this.questionDao = questionDao; }

    public List<Question> findAll () throws SQLException{
        return questionDao.findAll();
    }

    public List<Question> findAllForOwner(String ownerUserId) throws SQLException {
        return questionDao.findAllForOwner(ownerUserId);
    }

    public List<Question> findAllForAttempt(String attemptId) throws SQLException {
        return questionDao.findAllForAttempt(attemptId);
    }

    public Question findById (String questionId) throws SQLException {
        return questionDao.findById(questionId);
    }

    public Question findByIdForOwner(String questionId, String ownerUserId) throws SQLException {
        return questionDao.findByIdForOwner(questionId, ownerUserId);
    }

    public Question create (Question question) throws SQLException {
        return questionDao.create(question);
    }

    public Question update (String questionId, Question question) throws SQLException {
        return questionDao.update(questionId, question);
    }

    public boolean delete (String questionId) throws  SQLException {
        return questionDao.delete(questionId);
    }


}
