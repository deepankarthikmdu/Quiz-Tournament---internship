package org.example.service;

import org.example.dao.TournamentDao;
import org.example.dao.UserDao;
import org.example.model.Tournament;
import org.example.model.User;

import java.math.BigDecimal;
import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

public class TournamentService {
    private final TournamentDao tournamentDao;
    private final UserDao userDao;
    private final TournamentCache cache;

    public TournamentService() {
        this(new TournamentDao(), new UserDao(), new TournamentCache());
    }

    TournamentService(TournamentDao tournamentDao, UserDao userDao) {
        this(tournamentDao, userDao, new TournamentCache());
    }

    TournamentService(TournamentDao tournamentDao, UserDao userDao, TournamentCache cache) {
        this.tournamentDao = tournamentDao;
        this.userDao = userDao;
        this.cache = cache;
    }

    public List<Tournament> findAll() throws SQLException {
        List<Tournament> cached = cache.getTournamentList();
        if (cached != null) {
            return cached;
        }
        List<Tournament> entries = tournamentDao.findAll();
        cache.replaceTournamentList(entries);
        return entries;
    }

    public Tournament findById(String tournamentId) throws SQLException {
        Tournament cached = cache.getTournament(tournamentId);
        if (cached != null) return cached;
        Tournament tournament = tournamentDao.findById(tournamentId);
        if (tournament != null) cache.replaceTournament(tournament);
        return tournament;
    }

    public String getUserId (String tournamentId) throws SQLException {
        return tournamentDao.getUserId(tournamentId);
    }

    public Tournament create(String ownerUserId, Tournament tournament) throws SQLException {
        if (ownerUserId == null || ownerUserId.isBlank()) {
            throw new IllegalArgumentException("Authenticated user is required");
        }
        if (tournament == null) {
            throw new IllegalArgumentException("Tournament body is required");
        }
        tournament.setUserId(ownerUserId);
        validate(tournament, true);
        User owner = userDao.findById(tournament.getUserId());
        if (owner == null) {
            throw new IllegalArgumentException("userId does not identify an existing user");
        }
        Tournament created = tournamentDao.create(tournament);
        cache.invalidateTournament(created.getTournamentId());
        return created;
    }

    public LocalDateTime getRegistrationTimeLimit (String tournamentId) throws SQLException {
        return tournamentDao.getRegistrationTimeLimit(tournamentId);
    }

    public String setPrizeMoney (String tournamentId, Double prizeMoney) throws SQLException {
        return tournamentDao.setPrizeMoney(tournamentId,prizeMoney);
    }

    public boolean toggleStatus (String tournamentId, String status) throws SQLException {
        boolean updated = tournamentDao.toggleStatus(tournamentId, status);
        if (updated) cache.invalidateTournament(tournamentId);
        return updated;
    }

    public int completeAndRank(String tournamentId) throws SQLException {
        int rankedCount = tournamentDao.completeAndRank(tournamentId);
        if (rankedCount >= 0) cache.invalidateTournament(tournamentId);
        return rankedCount;
    }

    public Tournament update(String ownerUserId, String tournamentId, Tournament tournament) throws SQLException {
        if (tournament == null) {
            throw new IllegalArgumentException("Tournament body is required");
        }
        Tournament existing = tournamentDao.findById(tournamentId);
        if (existing == null || !existing.getUserId().equals(ownerUserId)) {
            return null;
        }
        tournament.setUserId(ownerUserId);
        tournament.setStatus(existing.getStatus());
        tournament.setParticipantCount(existing.getParticipantCount());
        validate(tournament, false);
        Tournament updated = tournamentDao.update(tournamentId, tournament);
        if (updated != null) cache.invalidateTournament(tournamentId);
        return updated;
    }

    public boolean delete(String ownerUserId, String tournamentId) throws SQLException {
        Tournament existing = tournamentDao.findById(tournamentId);
        if (existing == null || !existing.getUserId().equals(ownerUserId)) {
            return false;
        }
        boolean deleted = tournamentDao.delete(tournamentId);
        if (deleted) cache.invalidateTournament(tournamentId);
        return deleted;
    }

    private void validate(Tournament tournament, boolean isCreate) {
        if (tournament == null) {
            throw new IllegalArgumentException("Tournament body is required");
        }
        if (isCreate && (tournament.getTournamentId() == null || tournament.getTournamentId().isBlank())) {
            throw new IllegalArgumentException("tournamentId is required");
        }
        if (isCreate && tournament.getTournamentId().trim().length() > 36) {
            throw new IllegalArgumentException("tournamentId must be at most 36 characters");
        }

        if (tournament.getUserId() == null || tournament.getUserId().isBlank()) {
            throw new IllegalArgumentException("userId is required");
        }
        if (tournament.getUserId().trim().length() > 36) {
            throw new IllegalArgumentException("userId must be at most 36 characters");
        }
        if (tournament.getName() == null || tournament.getName().isBlank()) {
            throw new IllegalArgumentException("name is required");
        }
        if (tournament.getName().trim().length() > 255) {
            throw new IllegalArgumentException("name must be at most 255 characters");
        }
        if (tournament.getParticipantLimit() <= 0) {
            throw new IllegalArgumentException("participantLimit must be greater than zero");
        }
        if (tournament.getNumberOfRounds() <= 0) {
            throw new IllegalArgumentException("numberOfRounds must be greater than zero");
        }

        BigDecimal entryFee = tournament.getEntryFee();
        if (entryFee != null && entryFee.signum() < 0) {
            throw new IllegalArgumentException("entryFee cannot be negative");
        }
        if (entryFee != null && entryFee.precision() - entryFee.scale() > 8) {
            throw new IllegalArgumentException("entryFee exceeds the database limit");
        }
        if (entryFee != null && entryFee.scale() > 2) {
            throw new IllegalArgumentException("entryFee can have at most two decimal places");
        }

        LocalDateTime startDate = tournament.getStartDate();
        LocalDateTime endDate = tournament.getEndDate();
        if ((startDate == null) != (endDate == null)) {
            throw new IllegalArgumentException("startDate and endDate must either both be provided or both be omitted");
        }
        if (startDate != null && !endDate.isAfter(startDate)) {
            throw new IllegalArgumentException("endDate must be after startDate");
        }

        if (isCreate) {
            tournament.setTournamentId(tournament.getTournamentId().trim());
            tournament.setParticipantCount(0);
            tournament.setStatus("draft");
        }
        tournament.setUserId(tournament.getUserId().trim());
        tournament.setName(tournament.getName().trim());
        if (tournament.getDescription() != null) tournament.setDescription(tournament.getDescription().trim());
        if (isCreate && tournament.getEntryFee() == null) tournament.setEntryFee(BigDecimal.ZERO);
    }
}
