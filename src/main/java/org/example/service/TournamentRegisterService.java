package org.example.service;

import org.example.dao.TournamentRegisterDao;
import org.example.model.TournamentRegister;
import org.example.model.User;
import org.example.dao.UserDao;

import java.time.LocalDateTime;
import java.sql.SQLException;
import java.util.List;

public class TournamentRegisterService {
    private final TournamentRegisterDao tournamentRegisterDao;
    private final UserDao userDao;
    private final TournamentCache tournamentCache;

    public TournamentRegisterService() {
        this(new TournamentRegisterDao(), new UserDao(), new TournamentCache());
    }

    TournamentRegisterService(TournamentRegisterDao dao, UserDao userDao) {
        this(dao, userDao, new TournamentCache());
    }

    TournamentRegisterService(TournamentRegisterDao dao, UserDao userDao, TournamentCache tournamentCache) {
        this.tournamentRegisterDao = dao;
        this.userDao = userDao;
        this.tournamentCache = tournamentCache;
    }

    public List<TournamentRegister> findAll() throws SQLException {
        return tournamentRegisterDao.findAll();
    }

    public TournamentRegister findById(String registerId) throws SQLException {
        return tournamentRegisterDao.findById(registerId);
    }

    public TournamentRegister create(String ownerUserId, TournamentRegister entity) throws SQLException {
        if(ownerUserId == null || ownerUserId.isBlank()){
            throw new IllegalArgumentException("authenticated user is required");
        }

        if(entity == null){
            throw new IllegalArgumentException(("tournamentRegister body required !"));
        }

        entity.setUserId(ownerUserId);
        entity.setPaymentId(null);
        entity.setStatus("pending");
        entity.setRegisteredDate(LocalDateTime.now());
        validate(entity, true);
        User owner = userDao.findById(entity.getUserId());
        if(owner == null){
            throw new IllegalArgumentException("userId does not identify an existing user");
        }

        if (!tournamentRegisterDao.createWithCapacity(entity)) return null;
        tournamentCache.invalidateTournament(entity.getTournamentId());
        return entity;
    }

    public TournamentRegister update(String registerId, TournamentRegister entity) throws SQLException {
        validate(entity, false);
        if (tournamentRegisterDao.findById(registerId) == null) return null;
        return tournamentRegisterDao.update(registerId, entity);
    }

    public boolean delete(String ownerUserId, String registerId) throws SQLException {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("authenticated user is required");
        }
        TournamentRegister existing = tournamentRegisterDao.findById(registerId);
        boolean deleted = tournamentRegisterDao.deleteForOwnerAndDecrement(registerId, ownerUserId);
        if (deleted && existing != null) {
            tournamentCache.invalidateTournament(existing.getTournamentId());
        }
        return deleted;
    }

    private void validate(TournamentRegister entity, boolean creating) {
        if (entity == null) throw new IllegalArgumentException("TournamentRegister body is required");
        if (creating) requireId("registerId", entity.getRegisterId());
        requireId("tournamentId", entity.getTournamentId());
        requireId("userId", entity.getUserId());
        requireEnum("status", entity.getStatus(), "pending", "confirmed", "cancelled");
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
