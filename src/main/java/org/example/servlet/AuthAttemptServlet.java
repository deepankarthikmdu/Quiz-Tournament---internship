package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSession;
import org.example.dto.AttemptRequest;
import org.example.dto.AttemptResponse;
import org.example.dto.QuestionResponse;
import org.example.model.Attempt;
import org.example.model.Question;
import org.example.model.TournamentResult;
import org.example.service.AttemptService;
import org.example.service.TournamentAnswerService;
import org.example.service.TournamentResultService;
import org.example.service.QuestionService;
import org.example.util.JsonUtil;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.List;
import java.util.Map;

@WebServlet (urlPatterns = {"/api/attempts/start", "/api/attempts/stop", "/api/attempts/questions"})
public class AuthAttemptServlet extends HttpServlet {

    public static final String SESSION_ATTEMPT_ID = "attemptid";
    private final AttemptService attemptService = new AttemptService();
    private final TournamentAnswerService answerService = new TournamentAnswerService();
    private final TournamentResultService resultService = new TournamentResultService();
    private final QuestionService questionService = new QuestionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            HttpSession session = req.getSession(false);
            Object attemptValue = session == null ? null : session.getAttribute(SESSION_ATTEMPT_ID);
            Object userValue = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
            if (!(attemptValue instanceof String attemptId) || attemptId.isBlank()
                    || !(userValue instanceof String userId) || userId.isBlank()) {
                JsonUtil.sendError(res, 401, "Login and start an attempt first");
                return;
            }

            if("/api/attempts/questions".equals(req.getServletPath())) {

                String requestedAttemptId = req.getParameter("attemptId");
                if (requestedAttemptId != null && !attemptId.equals(requestedAttemptId.trim())) {
                    JsonUtil.sendError(res, 403, "You can only view questions for your active attempt");
                    return;
                }

                String attemptOwnerId = attemptService.authenticateUser(attemptId);
                if (!userId.equals(attemptOwnerId)) {
                    JsonUtil.sendError(res, 403, "Attempt does not belong to the logged-in user");
                    return;
                }
                if (attemptService.authenticateAttemptStartTimestamp(attemptId)) {
                    JsonUtil.sendError(res, 409, "Attempt has not started");
                    return;
                }
                if (!attemptService.authenticateAttemptEndTimestamp(attemptId)) {
                    JsonUtil.sendError(res, 409, "Attempt has ended");
                    return;
                }

                List<QuestionResponse> questions = questionService.findAllForAttempt(attemptId).stream()
                        .map(q -> new QuestionResponse(q.getQuestionId(), q.getDescription()))
                        .toList();
                JsonUtil.sendJson(res, 200, Map.of("attemptId", attemptId, "questions", questions));
            }
        } catch (SQLException e) {
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        AttemptRequest attemptRequest = readAttemptRequest(req, res);
        if(attemptRequest == null){
            return;
        }

        try{
            Attempt attempt = attemptService.findById(attemptRequest.attemptId());
            if(attempt == null){
                JsonUtil.sendError(res,401,"invalid attemptId");
                return;
            }

            String userId = attemptService.authenticateUser((attemptRequest.attemptId()));
            if(userId == null || userId.isBlank()){
                JsonUtil.sendError(res, 401, "current logged user cannot be found !");
                return;
            }

            String ownerUserId = authenticatedUser(req, res);
            if(ownerUserId == null){
                return;
            }

            if(!ownerUserId.equals(userId)){
                JsonUtil.sendError(res, 401, "cannot start this attempt");
                return;
            }

            if("/api/attempts/stop".equals(req.getServletPath())){
                if(attemptService.authenticateAttemptStartTimestamp(attemptRequest.attemptId())){
                    JsonUtil.sendError(res, 401, "attempt yet to be started");
                    return;
                }

                if(!attemptService.setEndAttemptTimestamp(attemptRequest.attemptId())){
                    JsonUtil.sendError(res, 401, "attempt already ended");
                    return;
                }
                attemptStop(req, res);
                int score = answerService.findScore(attemptRequest.attemptId());
                TournamentResult result = resultService.create(attemptRequest.attemptId(), score);
                JsonUtil.sendJson(res, 200, result);
                return;
            }

            if(!attemptService.authenticateAttemptEndTimestamp(attemptRequest.attemptId())){
                JsonUtil.sendError(res, 401, "attempt already ended");
                return;
            }

            attempt = attemptService.findById(attemptRequest.attemptId());

            HttpSession session = req.getSession(true);
            req.changeSessionId();
            session.setAttribute(SESSION_ATTEMPT_ID, attempt.getAttemptId());
            session.setMaxInactiveInterval(30*60);

            if(!attemptService.setStartAttemptTimestamp(attemptRequest.attemptId())){
                JsonUtil.sendError(res, 401, "attempt already started");
                return;
            }

            JsonUtil.sendJson(res, 200,
                    Map.of("message","attempt started successfully",
                            "Attempt", new AttemptResponse(attempt.getAttemptId(),attempt.getRoundParticipantId(),attempt.getStartAttemptDateTime(),attempt.getEndDateTime())));
        }catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    private AttemptRequest readAttemptRequest (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            AttemptRequest attempt = JsonUtil.fromJson(reader, AttemptRequest.class);
            if(attempt == null || attempt.attemptId() == null || attempt.attemptId().isBlank()){
                JsonUtil.sendError(res,400, "attemptId is required");
                return null;
            }
            return attempt;
        }

        catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "invalid or missing json body");
            return null;
        }
    }

    private void attemptStop (HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if(session != null) { session.removeAttribute(SESSION_ATTEMPT_ID); }
        JsonUtil.sendJson(res, 200, Map.of("message", "attempt ended ! "));
    }

    private void logDatabaseError(SQLException e) {
        getServletContext().log(
                "Authentication database operation failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")",
                e);
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
}
