package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.service.TournamentService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

import static org.example.servlet.AuthApiServletCopy.SESSION_USER_ID;

@WebServlet("/api/tournament/allocate-prize-money")
public class TournamentPrizeMoneyApiServlet extends HttpServlet {
    private final TournamentService tournamentService = new TournamentService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUserId(req, res);
        if (ownerUserId == null) return;

        String tournamentId = cleanId(req.getParameter("tournamentId"));
        if (tournamentId == null) {
            JsonUtil.sendError(res, 400, "Query parameter 'tournamentId' is required and must be valid");
            return;
        }

        PrizeMoneyRequest request = readPrizeMoney(req, res);
        if (request == null) return;

        try {
            String tournamentOwnerId = tournamentService.getUserId(tournamentId);
            if (tournamentOwnerId == null) {
                JsonUtil.sendError(res, 404, "Tournament not found");
                return;
            }
            if (!ownerUserId.equals(tournamentOwnerId)) {
                JsonUtil.sendError(res, 403, "Only the tournament owner can allocate prize money");
                return;
            }

            String winnerAttemptId = tournamentService.setPrizeMoney(tournamentId, request.prizeMoney());
            if (winnerAttemptId == null) {
                JsonUtil.sendError(res, 404, "No completed attempt was found in the tournament's final round");
                return;
            }

            JsonUtil.sendJson(res, 200, Map.of(
                    "message", "Prize money allocated to the first ranker",
                    "tournamentId", tournamentId,
                    "winnerAttemptId", winnerAttemptId,
                    "prizeMoney", request.prizeMoney()));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            getServletContext().log(
                    "Prize allocation database operation failed (SQLState=" + e.getSQLState()
                            + ", vendorCode=" + e.getErrorCode() + ")",
                    e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    private PrizeMoneyRequest readPrizeMoney(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            PrizeMoneyRequest request = JsonUtil.fromJson(reader, PrizeMoneyRequest.class);
            if (request == null || request.prizeMoney() == null) {
                JsonUtil.sendError(res, 400, "A prizeMoney value is required in the JSON body");
                return null;
            }
            if (!Double.isFinite(request.prizeMoney()) || request.prizeMoney() < 0) {
                JsonUtil.sendError(res, 400, "prizeMoney must be a finite, non-negative amount");
                return null;
            }
            return request;
        } catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
            return null;
        }
    }

    private String authenticatedUserId(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(userId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, 401, "Login required");
            return null;
        }
        return id;
    }

    private String cleanId(String value) {
        if (value == null) return null;
        String id = value.trim();
        return id.isEmpty() || id.length() > 36 ? null : id;
    }

    private record PrizeMoneyRequest(Double prizeMoney) {}
}
