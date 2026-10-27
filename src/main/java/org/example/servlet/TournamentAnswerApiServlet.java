package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.TournamentAnswerRequest;
import org.example.dto.TournamentAnswerResponse;
import org.example.model.TournamentAnswer;
import org.example.service.AttemptService;
import org.example.service.OptionService;
import org.example.service.TournamentAnswerService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;

@WebServlet("/api/tournament-answers")
public class TournamentAnswerApiServlet extends HttpServlet {
    private final TournamentAnswerService answerService = new TournamentAnswerService();
    private final AttemptService attemptService = new AttemptService();
    private final OptionService optionService = new OptionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String activeAttemptId = getActiveAttemptId(req, res);
            if (activeAttemptId == null) return;

            String requestedAttemptId = req.getParameter("attemptId");
            if (requestedAttemptId != null && !activeAttemptId.equals(requestedAttemptId.trim())) {
                JsonUtil.sendError(res, 403, "You can only view answers for your active attempt");
                return;
            }

            String questionId = req.getParameter("questionId");
            if (questionId == null) {
                JsonUtil.sendJson(res, 200, toResponseList(answerService.findAllByAttemptId(activeAttemptId)));
                return;
            }

            questionId = cleanKey(questionId, "questionId", res);
            if (questionId == null) return;

            TournamentAnswer answer = answerService.findById(activeAttemptId, questionId);
            if (answer == null) JsonUtil.sendError(res, 404, "Answer not found");
            else JsonUtil.sendJson(res, 200, toResponse(answer, false));
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        TournamentAnswerRequest request = readRequest(req, res);
        if (request == null) return;

        try {
            String attemptId = getActiveAttemptId(req, res);
            if (attemptId == null) return;
            if (!answerService.questionBelongsToOpenAttempt(attemptId, request.questionId())) {
                JsonUtil.sendError(res, 400, "Question is not part of the active attempt");
                return;
            }

            Boolean correct = optionService.findAnswer(request.questionId(), request.optionId());
            if (correct == null) {
                JsonUtil.sendError(res, 400, "Selected option does not belong to the question");
                return;
            }

            TournamentAnswer saved = answerService.saveAnswer(attemptId, request, correct);
            JsonUtil.sendJson(res, 200, toResponse(saved, false));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonUtil.sendError(res, 405, "Submit answers with POST while the attempt is open");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonUtil.sendError(res, 405, "Answers cannot be deleted through this endpoint");
    }

    private TournamentAnswerRequest readRequest(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            TournamentAnswerRequest request = JsonUtil.fromJson(reader, TournamentAnswerRequest.class);
            if (request == null || isInvalidKey(request.questionId()) || isInvalidKey(request.optionId())) {
                JsonUtil.sendError(res, 400, "questionId and optionId are required and must be valid");
                return null;
            }
            return new TournamentAnswerRequest(request.questionId().trim(), request.optionId().trim());
        } catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
            return null;
        }
    }

    private String getActiveAttemptId(HttpServletRequest req, HttpServletResponse res) throws IOException, SQLException {
        HttpSession session = req.getSession(false);
        Object attemptValue = session == null ? null : session.getAttribute(AuthAttemptServlet.SESSION_ATTEMPT_ID);
        Object userValue = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(attemptValue instanceof String attemptId) || attemptId.isBlank()
                || !(userValue instanceof String userId) || userId.isBlank()) {
            JsonUtil.sendError(res, 401, "Login and start an attempt first");
            return null;
        }

        String attemptOwnerId = attemptService.authenticateUser(attemptId);
        if (attemptOwnerId == null || !attemptOwnerId.equals(userId)) {
            JsonUtil.sendError(res, 403, "Attempt does not belong to the logged-in user");
            return null;
        }
        if (attemptService.authenticateAttemptStartTimestamp(attemptId)) {
            JsonUtil.sendError(res, 409, "Attempt has not started");
            return null;
        }
        if (!attemptService.authenticateAttemptEndTimestamp(attemptId)) {
            JsonUtil.sendError(res, 409, "Attempt has ended");
            return null;
        }
        return attemptId;
    }

    private TournamentAnswerResponse toResponse(TournamentAnswer answer, boolean revealCorrectness) {
        return new TournamentAnswerResponse(answer.getAttemptId(), answer.getQuestionId(),
                answer.getSelectedOptionId(), revealCorrectness ? answer.getIsCorrect() : null,
                answer.getAttemptedDate());
    }

    private List<TournamentAnswerResponse> toResponseList(List<TournamentAnswer> answers) {
        return answers.stream().map(answer -> toResponse(answer, false)).toList();
    }

    private boolean isInvalidKey(String value) {
        return value == null || value.isBlank() || value.trim().length() > 36;
    }

    private String cleanKey(String value, String field, HttpServletResponse res) throws IOException {
        if (isInvalidKey(value)) {
            JsonUtil.sendError(res, 400, field + " is invalid");
            return null;
        }
        return value.trim();
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log("TournamentAnswer database operation failed (SQLState=" + e.getSQLState()
                + ", vendorCode=" + e.getErrorCode() + ")", e);
        JsonUtil.sendError(res, 500, "Database error");
    }
}
