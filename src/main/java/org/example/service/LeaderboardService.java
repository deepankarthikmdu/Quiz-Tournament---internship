package org.example.service;

import org.example.dao.LeaderboardDao;
import org.example.dto.LeaderboardEntry;

import java.sql.SQLException;
import java.util.List;

public class LeaderboardService {
    private final LeaderboardDao dao;
    private final LeaderboardCache cache;

    public LeaderboardService() {
        this(new LeaderboardDao(), new LeaderboardCache());
    }

    LeaderboardService(LeaderboardDao dao, LeaderboardCache cache) {
        this.dao = dao;
        this.cache = cache;
    }

    public boolean tournamentExists(String tournamentId) throws SQLException {
        return dao.tournamentExists(tournamentId);
    }

    public boolean canViewTournament(String tournamentId, String userId) throws SQLException {
        return dao.canViewTournament(tournamentId, userId);
    }

    public boolean canViewAttempt(String attemptId, String userId) throws SQLException {
        return dao.canViewAttempt(attemptId, userId);
    }

    public boolean isTournamentOwnerForAttempt(String attemptId, String userId) throws SQLException {
        return dao.isTournamentOwnerForAttempt(attemptId, userId);
    }

    public void invalidateAttemptCache(String attemptId) throws SQLException {
        String tournamentId = dao.findTournamentIdForAttempt(attemptId);
        String participantUserId = dao.findParticipantUserIdForAttempt(attemptId);
        if (tournamentId != null && participantUserId != null) {
            cache.invalidateAttempt(tournamentId, participantUserId, attemptId);
        }
    }

    public List<LeaderboardEntry> getTournamentLeaderboard(String tournamentId) throws SQLException {
        List<LeaderboardEntry> cached = cache.getTournamentLeaderboard(tournamentId);
        if (cached != null) return cached;

        List<LeaderboardEntry> entries = dao.findTournamentLeaderboard(tournamentId);
        cache.replaceTournamentLeaderboard(tournamentId, entries);
        return entries;
    }

    public List<LeaderboardEntry> getUserScores(String userId) throws SQLException {
        List<LeaderboardEntry> cached = cache.getUserScores(userId);
        if (cached != null) return cached;

        List<LeaderboardEntry> entries = dao.findScoresForUser(userId);
        cache.replaceUserScores(userId, entries);
        return entries;
    }

    public void refreshTournamentLeaderboard(String tournamentId) throws SQLException {
        cache.replaceTournamentLeaderboard(tournamentId, dao.findTournamentLeaderboard(tournamentId));
    }
}
