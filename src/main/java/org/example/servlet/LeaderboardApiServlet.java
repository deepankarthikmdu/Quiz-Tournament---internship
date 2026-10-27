package org.example.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.LeaderboardEntry;
import org.example.service.LeaderboardService;
import org.example.util.JsonUtil;

import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet(urlPatterns = {"/api/tournaments/leaderboard", "/api/users/me/scores"})
public class LeaderboardApiServlet extends HttpServlet {
    private final LeaderboardService leaderboardService = new LeaderboardService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUserId(req, res);
        if (userId == null) return;

        try {
            if ("/api/users/me/scores".equals(req.getServletPath())) {
                List<LeaderboardEntry> entries = leaderboardService.getUserScores(userId);
                JsonUtil.sendJson(res, HttpServletResponse.SC_OK, entries);
                return;
            }

            String tournamentId = cleanTournamentId(req.getParameter("tournamentId"), res);
            if (tournamentId == null) return;
            if (!leaderboardService.tournamentExists(tournamentId)) {
                JsonUtil.sendError(res, HttpServletResponse.SC_NOT_FOUND, "Tournament not found");
                return;
            }

            if (!leaderboardService.canViewTournament(tournamentId, userId)) {
                JsonUtil.sendError(res, HttpServletResponse.SC_FORBIDDEN,
                        "Only the tournament owner or a confirmed participant can view this leaderboard");
                return;
            }

            JsonUtil.sendJson(res, HttpServletResponse.SC_OK,
                    leaderboardService.getTournamentLeaderboard(tournamentId));
        } catch (SQLException e) {
            getServletContext().log("Leaderboard database operation failed (SQLState=" + e.getSQLState()
                    + ", vendorCode=" + e.getErrorCode() + ")", e);
            JsonUtil.sendError(res, HttpServletResponse.SC_INTERNAL_SERVER_ERROR, "Database error");
        }
    }

    private String authenticatedUserId(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object value = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(value instanceof String userId) || userId.isBlank()) {
            JsonUtil.sendError(res, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return null;
        }
        return userId;
    }

    private String cleanTournamentId(String tournamentId, HttpServletResponse res) throws IOException {
        if (tournamentId == null || tournamentId.isBlank() || tournamentId.trim().length() > 36) {
            JsonUtil.sendError(res, HttpServletResponse.SC_BAD_REQUEST,
                    "Query parameter 'tournamentId' is required and must be valid");
            return null;
        }
        return tournamentId.trim();
    }
}
