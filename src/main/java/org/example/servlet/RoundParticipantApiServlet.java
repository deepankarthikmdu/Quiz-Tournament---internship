package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.RoundParticipantResponse;
import org.example.model.RoundParticipant;
import org.example.service.RoundParticipantService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

import static org.example.servlet.AuthApiServletCopy.SESSION_USER_ID;

@WebServlet("/api/round-participants")
public class RoundParticipantApiServlet extends HttpServlet {
    private final RoundParticipantService roundParticipantService = new RoundParticipantService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String key = req.getParameter("id");
            if (key == null) key = req.getParameter("roundParticipantId");
            if (key == null) {
                JsonUtil.sendJson(res, 200, toResponseList(roundParticipantService.findAll()));
                return;
            }
            key = cleanKey(key, "roundParticipantId", res);
            if (key == null) return;
            RoundParticipant entity = roundParticipantService.findById(key);
            if (entity == null) JsonUtil.sendError(res, 404, "RoundParticipant not found");
            else JsonUtil.sendJson(res, 200, toResponse(entity));
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String userId = authenticatedUser(req, res);
        if (userId == null) return;
        RoundParticipant entity = readRoundParticipant(req, res);
        if (entity == null) return;
        try {
            JsonUtil.sendJson(res, 201, toResponse(roundParticipantService.create(userId, entity)));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonUtil.sendError(res, 405, "Participant qualification status is managed by the server");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String roundParticipantId = req.getParameter("roundParticipantId");
        if (roundParticipantId == null) roundParticipantId = req.getParameter("id");
        roundParticipantId = cleanKey(roundParticipantId, "roundParticipantId", res);
        if (roundParticipantId == null) return;
        try {
            if (roundParticipantService.delete(roundParticipantId)) {
                JsonUtil.sendJson(res, 200, Map.of("message", "RoundParticipant deleted"));
            } else {
                JsonUtil.sendError(res, 404, "RoundParticipant not found");
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    private RoundParticipant readRoundParticipant(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            RoundParticipant entity = JsonUtil.fromJson(reader, RoundParticipant.class);
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

    private RoundParticipantResponse toResponse(RoundParticipant entity) {
        return new RoundParticipantResponse(entity.getRoundParticipantId(), entity.getRoundId(), entity.getRegisterId(), entity.getQualifiedFromRoundId(), entity.getStatus(), entity.getQualifiedDate());
    }

    private List<RoundParticipantResponse> toResponseList(List<RoundParticipant> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    private String authenticatedUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object value = session == null ? null : session.getAttribute(SESSION_USER_ID);
        if (!(value instanceof String userId) || userId.isBlank()) {
            JsonUtil.sendError(res, 401, "login required");
            return null;
        }
        return userId;
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "RoundParticipant database operation failed (SQLState=" + e.getSQLState()
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
}
