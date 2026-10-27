package org.example.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.google.gson.JsonParseException;
import org.example.dto.QuizResponse;
import org.example.model.Quiz;
import org.example.service.QuizService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/quizzes")
public class QuizApiServlet extends HttpServlet {

    private final QuizService quizService = new QuizService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUserId(req, res);
        if (ownerUserId == null) return;

        String idParam = req.getParameter("id");
        try {
            if (idParam == null) {
                JsonUtil.sendJson(res, 200, toResponseList(quizService.findAllForOwner(ownerUserId)));
                return;
            }

            String quizId = idParam.trim();
            if (quizId.isEmpty() || quizId.length() > 36) {
                JsonUtil.sendError(res, 400, "id is invalid");
                return;
            }

            Quiz quiz = quizService.findByIdForOwner(quizId, ownerUserId);
            if (quiz == null) {
                JsonUtil.sendError(res, 404, "Quiz not found");
            } else {
                JsonUtil.sendJson(res, 200, toResponse(quiz));
            }

        } catch (SQLException e) {
            getServletContext().log(
                    "Quiz database operation failed (SQLState=" + e.getSQLState()
                            + ", vendorCode=" + e.getErrorCode() + ")",
                    e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res)throws IOException{
        String ownerUserId = authenticatedUserId(req, res);
        if (ownerUserId == null) return;

        String id = requireId(req, res);
        if(id == null){
            return;
        }

        Quiz quiz = readQuiz(req, res);
        if (quiz == null){
            return;
        }

        try {
            Quiz updated = quizService.update(ownerUserId, id, quiz);
            if (updated == null){
                JsonUtil.sendError(res, 404, "quiz not found");
                return;
            }
            JsonUtil.sendJson(res, 200, toResponse(updated));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }

    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException{
        String ownerUserId = authenticatedUserId(req, res);
        if (ownerUserId == null) return;

        String id = requireId(req, res);
        if(id == null){
            return;
        }
        try {
            boolean deleted = quizService.delete(ownerUserId, id);
            if (!deleted) {
                JsonUtil.sendError(res, 404, "quiz not found");
            } else {
                JsonUtil.sendJson(res, 200, Map.of("message", "Quiz deleted"));
            }
        }catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }

    }

    private QuizResponse toResponse(Quiz quiz) {
        return new QuizResponse(quiz.getQuizId(), quiz.getUserId(), quiz.getQuizName(), quiz.getQuizCategory());
    }

    private List<QuizResponse> toResponseList(List<Quiz> quizzes) {
        return quizzes.stream().map(this::toResponse).toList();
    }

    protected Quiz readQuiz (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            Quiz quiz = JsonUtil.fromJson(reader, Quiz.class);
            if(quiz == null){
                JsonUtil.sendError(res, 400, "invalid missing body");
                return null;
            }
            return quiz;
        }
        catch (JsonParseException e){
            JsonUtil.sendError(res, 400, "invalid missing body");
            return null;
        }
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "Database operation failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")",
                e);

        if (e instanceof SQLIntegrityConstraintViolationException) {
            JsonUtil.sendError(res, 409, "Conflict: quizId already exists or the quiz is referenced by other data");
        } else {
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    private String requireId(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String idParam = req.getParameter("id");
        if (idParam == null) {
            JsonUtil.sendError(res, 400, "Query parameter 'id' is required");
            return null;
        }

        String id = cleanId(idParam);
        if (id == null) {
            JsonUtil.sendError(res, 400, "id is invalid");
        }
        return id;
    }

    private String cleanId(String value) {
        String id = value.trim();
        return (id.isEmpty() || id.length() > 36) ? null : id;
    }

    private String authenticatedUserId(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(userId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, 401, "Login required");
            return null;
        }
        return id;
    }
}
