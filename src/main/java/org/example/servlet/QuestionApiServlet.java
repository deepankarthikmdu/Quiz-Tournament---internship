package org.example.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.google.gson.JsonParseException;
import org.example.dto.QuestionResponse;
import org.example.dto.QuizResponse;
import org.example.model.Question;
import org.example.model.Quiz;
import org.example.service.QuestionService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@WebServlet("/api/questions")
public class QuestionApiServlet extends HttpServlet {
    private final QuestionService questionService = new QuestionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUser(req, res);
        if (ownerUserId == null) return;

        String idParam = req.getParameter("id");
        try{
            if(idParam == null){
                JsonUtil.sendJson(res, 200, toResponseList(questionService.findAllForOwner(ownerUserId)));
                return;
            }

            String questionId = idParam.trim();
            if(questionId.isEmpty() || questionId.length() > 36){
                JsonUtil.sendError(res, 400, "invalid questionId");
                return;
            }

            Question question = questionService.findByIdForOwner(questionId, ownerUserId);
            if (question == null){
                JsonUtil.sendError(res, 404, "question not found");
            }else{
                JsonUtil.sendJson(res, 200, toResponse(question));
            }
        }catch (SQLException e) {
            getServletContext().log(
                    "Quiz database operation failed (SQLState=" + e.getSQLState()
                            + ", vendorCode=" + e.getErrorCode() + ")",
                    e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String questionId = requireId(req, res);
        if (questionId == null){
            return;
        }

        Question question = readQuestion(req, res);
        if (question == null){
            return;
        }

        try{

            Question updated = questionService.update(questionId, question);
            if (updated == null){
                JsonUtil.sendError(res, 400, "question not found");
                return;
            }
            JsonUtil.sendJson(res, 200, updated);
        }catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    protected void doDelete (HttpServletRequest req, HttpServletResponse res) throws IOException {
        String questionId = requireId(req, res);
        if (questionId == null){
            return;
        }
        try{
            boolean deleted = questionService.delete(questionId);
            if (!deleted){
                JsonUtil.sendError(res, 403, "question not found");
            }else{
                JsonUtil.sendJson(res, 200, Map.of("message", "question deleted successfully"));
            }
        }catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    protected Question readQuestion (HttpServletRequest req, HttpServletResponse res) throws IOException{
        try(BufferedReader reader = req.getReader()){
            Question question = JsonUtil.fromJson(reader, Question.class);
            if (question == null) {
                JsonUtil.sendError(res, 400, "invalid or missing body");
                return null;
            }
            return question;
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

    private String authenticatedUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object userId = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(userId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, 401, "filter not handled !");
            return null;
        }
        return id;
    }

    private QuestionResponse toResponse(Question question) {
        return new QuestionResponse(question.getQuestionId(), question.getDescription());
    }

    private List<QuestionResponse> toResponseList(List<Question> question) {
        return question.stream().map(this::toResponse).toList();
    }

}
