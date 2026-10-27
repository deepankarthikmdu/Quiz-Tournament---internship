package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.OptionResponse;
import org.example.model.Option;
import org.example.service.OptionService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/options")
public class AuthOptionServlet extends HttpServlet {
    private final OptionService optionService = new OptionService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUser(req, res);
        if (ownerUserId == null) return;

        try {
            String key = req.getParameter("id");
            if (key == null) key = req.getParameter("optionId");
            if (key == null) {
                JsonUtil.sendJson(res, 200, toResponseList(optionService.findAllForOwner(ownerUserId)));
                return;
            }

            key = cleanKey(key, "optionId", res);
            if (key == null) return;
            Option entity = optionService.findByIdForOwner(key, ownerUserId);
            if (entity == null) JsonUtil.sendError(res, 404, "Option not found");
            else JsonUtil.sendJson(res, 200, toResponse(entity));
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        Option entity = readOption(req, res);
        if (entity == null) return;
        try {
            String userId = authenticatedUser(req, res);
            if(userId == null){
                return;
            }
            String questionId = authenticatedQuestion(req, res);
            if(questionId == null){
                return;
            }
            entity.setQuestionId(questionId);
            JsonUtil.sendJson(res, 201, toResponse(optionService.create(entity)));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String optionId = req.getParameter("optionId");
        if (optionId == null) optionId = req.getParameter("id");
        optionId = cleanKey(optionId, "optionId", res);
        if (optionId == null) return;
        Option entity = readOption(req, res);
        if (entity == null) return;
        try {
            Option updated = optionService.update(optionId, entity);
            if (updated == null) JsonUtil.sendError(res, 404, "Option not found");
            else JsonUtil.sendJson(res, 200, toResponse(updated));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String optionId = req.getParameter("optionId");
        if (optionId == null) optionId = req.getParameter("id");
        optionId = cleanKey(optionId, "optionId", res);
        if (optionId == null) return;
        try {
            if (optionService.delete(optionId)) {
                JsonUtil.sendJson(res, 200, Map.of("message", "Option deleted"));
            } else {
                JsonUtil.sendError(res, 404, "Option not found");
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    private Option readOption(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            Option entity = JsonUtil.fromJson(reader, Option.class);
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

    private OptionResponse toResponse(Option entity) {
        return new OptionResponse(entity.getOptionId(), entity.getQuestionId(), entity.getOptionText(), entity.isCorrect());
    }

    private List<OptionResponse> toResponseList(List<Option> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "Option database operation failed (SQLState=" + e.getSQLState()
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

    private String authenticatedQuestion(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object questionId = session == null ? null : session.getAttribute(AuthQuestionApiServlet.SESSION_QUESTION_ID);
        if(!(questionId instanceof String id) || id.isBlank()){
            JsonUtil.sendError(res, 401, "question required");
            return null;
        }
        return id;
    }

}
