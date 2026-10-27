package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.model.Tournament;
import org.example.dto.TournamentStatusRequest;
import org.example.service.TournamentService;
import org.example.service.LeaderboardService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;

@WebServlet(urlPatterns = {"/api/tournament/open", "/api/tournament/ongoing", "/api/tournament/completed", "/api/tournament/cancelled"})
public class TournamentStatusToggleApiServlet extends HttpServlet {
    private final TournamentService tournamentService = new TournamentService();
    private final LeaderboardService leaderboardService = new LeaderboardService();

    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUser(req, res);
        if(ownerUserId == null){
            return;
        }

        TournamentStatusRequest tournamentStatusRequest = readTournament(req, res);
        if(tournamentStatusRequest == null){
            return;
        }

        String tournamentId = tournamentStatusRequest.tournamentId();
        if (tournamentId == null || tournamentId.isBlank()) {
            JsonUtil.sendError(res, 400, "tournamentId is required");
            return;
        }

        String path = req.getServletPath();
        String status = switch (path) {
            case "/api/tournament/open" -> "open";
            case "/api/tournament/ongoing" -> "ongoing";
            case "/api/tournament/completed" -> "completed";
            case "/api/tournament/cancelled" -> "cancelled";
            default -> null;
        };

        if (status == null) {
            JsonUtil.sendError(res, 404, "Unknown tournament status action");
            return;
        }

        try {
            Tournament tournament = tournamentService.findById(tournamentId);
            if (tournament == null) {
                JsonUtil.sendError(res, 404, "Tournament not found");
                return;
            }

            if (!ownerUserId.equals(tournament.getUserId())) {
                JsonUtil.sendError(res, 403, "Only the tournament owner can change its status");
                return;
            }

            if ("completed".equals(status)
                    && "completed".equalsIgnoreCase(tournament.getStatus())) {
            } else if ("completed".equalsIgnoreCase(tournament.getStatus())) {
                JsonUtil.sendError(res, 409, "Tournament is already completed");
                return;
            }

            if ("completed".equals(status)) {
                int rankedAttemptCount = tournamentService.completeAndRank(tournamentId);
                if (rankedAttemptCount < 0) {
                    JsonUtil.sendError(res, 404, "Tournament not found");
                    return;
                }

                try {
                    leaderboardService.refreshTournamentLeaderboard(tournamentId);
                } catch (SQLException cacheRefreshError) {
                    getServletContext().log("Tournament completed, but its Redis leaderboard cache could not be refreshed",
                            cacheRefreshError);
                }

                JsonUtil.sendJson(res, 200, java.util.Map.of(
                        "message", rankedAttemptCount == 0
                                ? "Tournament completed, but no attempt results were linked to it"
                                : "Tournament completed and attempt ranks assigned",
                        "tournamentId", tournamentId,
                        "status", status,
                        "rankedAttempts", rankedAttemptCount));
                return;
            }

            if (!tournamentService.toggleStatus(tournamentId, status)) {
                JsonUtil.sendError(res, 409, "Tournament status could not be changed");
                return;
            }
            JsonUtil.sendJson(res, 200, java.util.Map.of(
                    "message", "Tournament status updated",
                    "tournamentId", tournamentId,
                    "status", status));
        } catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    protected TournamentStatusRequest readTournament (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            TournamentStatusRequest tournament = JsonUtil.fromJson(reader, TournamentStatusRequest.class);
            if(tournament == null){
                JsonUtil.sendError(res, 400, "invalid missing body");
                return null;
            }
            return tournament;
        }
        catch (JsonParseException e){
            JsonUtil.sendError(res, 400, "invalid missing body");
            return null;
        }
    }

    private String authenticatedUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if(!(userId instanceof String id) || id.isBlank()){
            JsonUtil.sendError(res,401,"login required");
            return null;
        }
        return id;
    }

    private void logDatabaseError(SQLException e) {
        getServletContext().log(
                "Authentication database operation failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")",
                e);
    }
}
