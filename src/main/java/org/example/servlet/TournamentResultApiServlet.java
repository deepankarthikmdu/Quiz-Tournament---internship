package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.TournamentResultResponse;
import org.example.model.TournamentResult;
import org.example.service.LeaderboardService;
import org.example.service.TournamentResultService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/tournament-results")
public class TournamentResultApiServlet extends HttpServlet {
    private final TournamentResultService tournamentResultService = new TournamentResultService();
    private final LeaderboardService leaderboardService = new LeaderboardService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUserId(req, res);
        if (userId == null) return;
        try {
            String key = req.getParameter("id");
            if (key == null) key = req.getParameter("attemptId");
            if (key == null) {
                JsonUtil.sendJson(res, 200, toResponseList(tournamentResultService.findForUser(userId)));
                return;
            }
            key = cleanKey(key, "attemptId", res);
            if (key == null) return;
            TournamentResult entity = tournamentResultService.findById(key);
            if (entity == null) {
                JsonUtil.sendError(res, 404, "TournamentResult not found");
            } else if (!leaderboardService.canViewAttempt(key, userId)) {
                JsonUtil.sendError(res, 403, "Only the attempt owner or tournament owner can view this result");
            } else {
                JsonUtil.sendJson(res, 200, toResponse(entity));
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUserId(req, res);
        if (userId == null) return;
        String attemptId = req.getParameter("attemptId");
        if (attemptId == null) attemptId = req.getParameter("id");
        attemptId = cleanKey(attemptId, "attemptId", res);
        if (attemptId == null) return;
        TournamentResult entity = readTournamentResult(req, res);
        if (entity == null) return;
        try {
            if (tournamentResultService.findById(attemptId) == null) {
                JsonUtil.sendError(res, 404, "TournamentResult not found");
                return;
            }
            if (!leaderboardService.isTournamentOwnerForAttempt(attemptId, userId)) {
                JsonUtil.sendError(res, 403, "Only the tournament owner can update a result");
                return;
            }
            leaderboardService.invalidateAttemptCache(attemptId);
            TournamentResult updated = tournamentResultService.update(attemptId, entity);
            if (updated == null) JsonUtil.sendError(res, 404, "TournamentResult not found");
            else JsonUtil.sendJson(res, 200, toResponse(updated));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUserId(req, res);
        if (userId == null) return;
        String attemptId = req.getParameter("attemptId");
        if (attemptId == null) attemptId = req.getParameter("id");
        attemptId = cleanKey(attemptId, "attemptId", res);
        if (attemptId == null) return;
        try {
            if (tournamentResultService.findById(attemptId) == null) {
                JsonUtil.sendError(res, 404, "TournamentResult not found");
                return;
            }
            if (!leaderboardService.isTournamentOwnerForAttempt(attemptId, userId)) {
                JsonUtil.sendError(res, 403, "Only the tournament owner can delete a result");
                return;
            }
            leaderboardService.invalidateAttemptCache(attemptId);
            if (tournamentResultService.delete(attemptId)) {
                JsonUtil.sendJson(res, 200, Map.of("message", "TournamentResult deleted"));
            } else {
                JsonUtil.sendError(res, 404, "TournamentResult not found");
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    private TournamentResult readTournamentResult(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            TournamentResult entity = JsonUtil.fromJson(reader, TournamentResult.class);
            if (entity == null) {
                JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
                return null;
            }
            return entity;
        } catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
            return null;
        }
    }

    private TournamentResultResponse toResponse(TournamentResult entity) {
        return new TournamentResultResponse(entity.getAttemptId(), entity.getScore(), entity.getRank(), entity.getPrizeMoney());
    }

    private List<TournamentResultResponse> toResponseList(List<TournamentResult> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "TournamentResult database operation failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")", e);
        if (e instanceof SQLIntegrityConstraintViolationException) {
            JsonUtil.sendError(res, 409, "Conflict: a database relationship or uniqueness constraint was violated");
        } else {
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    private String cleanKey(String value, String field, HttpServletResponse res) throws IOException {
        if (value == null || value.trim().isEmpty() || value.trim().length() > 36) {
            JsonUtil.sendError(res, 400, field + " is invalid");
            return null;
        }
        return value.trim();
    }

    private String authenticatedUserId(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null
                : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(userId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, HttpServletResponse.SC_UNAUTHORIZED, "Login required");
            return null;
        }
        return id;
    }
}
