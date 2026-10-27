package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.RoundResponse;
import org.example.model.Quiz;
import org.example.model.Round;
import org.example.model.Tournament;
import org.example.service.QuizService;
import org.example.service.RoundService;
import org.example.service.TournamentService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/rounds")
public class RoundApiServlet extends HttpServlet {
    private final RoundService roundService = new RoundService();
    private final TournamentService tournamentService = new TournamentService();
    private final QuizService quizService = new QuizService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String userId = authenticatedUser(req, res);
            if (userId == null) return;

            String key = req.getParameter("id");
            if (key == null) key = req.getParameter("roundId");
            if (key == null) {
                JsonUtil.sendJson(res, 200, toResponseList(roundService.findAllForOwner(userId)));
                return;
            }
            key = cleanKey(key, "roundId", res);
            if (key == null) return;
            Round entity = roundService.findByIdForOwner(key, userId);
            if (entity == null) JsonUtil.sendError(res, 404, "Round not found");
            else JsonUtil.sendJson(res, 200, toResponse(entity));
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Round entity = readRound(req, res);
        if (entity == null) return;

        try {
            String userId = authenticatedUser(req, res);
            if(userId == null){
                return;
            }

            Tournament tournament = tournamentService.findById(entity.getTournamentId());
            if(!tournament.getUserId().equals(userId)){
                JsonUtil.sendError(res, 401, "user not identified ! only the owner of the tournament can create rounds");
                return;
            }

            Quiz quiz = quizService.findById(entity.getQuizId());
            if(!quiz.getUserId().equals(userId)){
                JsonUtil.sendError(res, 401, "user not identified as this quiz's creator");
                return;
            }

            JsonUtil.sendJson(res, 201, toResponse(roundService.create(entity)));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUser(req, res);
        if (userId == null) return;

        String roundId = req.getParameter("roundId");
        if (roundId == null) roundId = req.getParameter("id");
        roundId = cleanKey(roundId, "roundId", res);
        if (roundId == null) return;

        try {
            roundRequest entity = readQualificationThreshold(req,res);
            if(entity == null){
                JsonUtil.sendError(res, 400, "qualificationThreshold required !");
                return;
            }
            Round existing = roundService.findById(roundId);
            if (existing == null) {
                JsonUtil.sendError(res, 404, "Round not found");
                return;
            }

            Tournament currentTournament = tournamentService.findById(existing.getTournamentId());
            if (currentTournament == null) {
                JsonUtil.sendError(res, 404, "Round's tournament not found");
                return;
            }

            if (!userId.equals(currentTournament.getUserId())) {
                JsonUtil.sendError(res, 403, "Only the tournament owner can update this round");
                return;
            }

            Tournament targetTournament = tournamentService.findById(existing.getTournamentId());
            if (targetTournament == null) {
                JsonUtil.sendError(res, 400, "tournamentId does not identify an existing tournament");
                return;
            }

            if (!userId.equals(targetTournament.getUserId())) {
                JsonUtil.sendError(res, 403, "You can only assign a round to your own tournament");
                return;
            }

            Quiz targetQuiz = quizService.findById(existing.getQuizId());
            if (targetQuiz == null) {
                JsonUtil.sendError(res, 400, "quizId does not identify an existing quiz");
                return;
            }

            if (!userId.equals(targetQuiz.getUserId())) {
                JsonUtil.sendError(res, 403, "You can only assign your own quiz to a round");
                return;
            }

            existing.setStatus(entity.status);
            Round updated = roundService.update(roundId, existing, entity.qualificationThreshold);
            if (updated == null) JsonUtil.sendError(res, 404, "Round not found");
            else JsonUtil.sendJson(res, 200, toResponse(updated));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUser(req, res);
        if (userId == null) return;

        String roundId = req.getParameter("roundId");
        if (roundId == null) roundId = req.getParameter("id");
        roundId = cleanKey(roundId, "roundId", res);
        if (roundId == null) return;
        try {
            Round existing = roundService.findById(roundId);
            if (existing == null) {
                JsonUtil.sendError(res, 404, "Round not found");
                return;
            }

            Tournament tournament = tournamentService.findById(existing.getTournamentId());
            if (tournament == null) {
                JsonUtil.sendError(res, 404, "Round's tournament not found");
                return;
            }
            if (!userId.equals(tournament.getUserId())) {
                JsonUtil.sendError(res, 403, "Only the tournament owner can delete this round");
                return;
            }

            if (roundService.delete(roundId)) {
                JsonUtil.sendJson(res, 200, Map.of("message", "Round deleted"));
            } else {
                JsonUtil.sendError(res, 404, "Round not found");
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    private Round readRound(HttpServletRequest req, HttpServletResponse res) throws IOException {
        req.setCharacterEncoding("UTF-8");
        try (BufferedReader reader = req.getReader()) {
            Round entity = JsonUtil.fromJson(reader, Round.class);
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

    private roundRequest readQualificationThreshold (HttpServletRequest req, HttpServletResponse res) throws  IOException {
        try (BufferedReader reader = req.getReader()) {
            roundRequest entity = JsonUtil.fromJson(reader, roundRequest.class);
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

    private RoundResponse toResponse(Round entity) {
        return new RoundResponse(entity.getRoundId(), entity.getTournamentId(), entity.getQuizId(), entity.getRoundNumber(), entity.getName(), entity.getStartDate(), entity.getEndDate(), entity.getStatus());
    }

    private List<RoundResponse> toResponseList(List<Round> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "Round database operation failed (SQLState=" + e.getSQLState()
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

    private String authenticatedUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if(!(userId instanceof String id) || id.isBlank()){
            JsonUtil.sendError(res,401,"login required");
            return null;
        }
        return id;
    }

    private record roundRequest(Integer qualificationThreshold, String status) {}
}
