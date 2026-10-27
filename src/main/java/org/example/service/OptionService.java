package org.example.service;

import org.example.dao.OptionDao;
import org.example.model.Option;
import java.time.LocalDateTime;

import java.sql.SQLException;
import java.util.List;

public class OptionService {
    private final OptionDao optionDao;

    public OptionService() {
        this(new OptionDao());
    }

    OptionService(OptionDao dao) {
        this.optionDao = dao;
    }

    public List<Option> findAll() throws SQLException {
        return optionDao.findAll();
    }

    public List<Option> findAllForOwner(String ownerUserId) throws SQLException {
        return optionDao.findAllForOwner(ownerUserId);
    }

    public Option findById(String optionId) throws SQLException {
        return optionDao.findById(optionId);
    }

    public Option findByIdForOwner(String optionId, String ownerUserId) throws SQLException {
        return optionDao.findByIdForOwner(optionId, ownerUserId);
    }

    public Option create(Option entity) throws SQLException {
        validate(entity, true);
        return optionDao.create(entity);
    }

    public Boolean findAnswer(String questionId, String optionId) throws SQLException {
        return optionDao.findAnswer(questionId, optionId);
    }

    public Option update(String optionId, Option entity) throws SQLException {
        validate(entity, false);
        if (optionDao.findById(optionId) == null) return null;
        return optionDao.update(optionId, entity);
    }

    public boolean delete(String optionId) throws SQLException {
        return optionDao.delete(optionId);
    }

    private void validate(Option entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("Option body is required");
        if (creating) requireId("optionId", entity.getOptionId());
        requireId("questionId", entity.getQuestionId());
        requireText("optionText", entity.getOptionText());
        if (entity.getOptionText().length() > 500) throw new IllegalArgumentException("optionText must be at most 500 characters");
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
