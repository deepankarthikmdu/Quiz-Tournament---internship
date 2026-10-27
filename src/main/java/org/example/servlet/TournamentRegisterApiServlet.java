package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.dto.TournamentRegisterResponse;
import org.example.model.TournamentRegister;
import org.example.service.TournamentRegisterService;
import org.example.service.TournamentService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@WebServlet("/api/tournament-registers")
public class TournamentRegisterApiServlet extends HttpServlet {
    private final TournamentRegisterService tournamentRegisterService = new TournamentRegisterService();
    private final TournamentService tournamentService = new TournamentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try {
            String key = req.getParameter("id");
            if (key == null) key = req.getParameter("registerId");
            if (key == null) {
                JsonUtil.sendJson(res, 200, toResponseList(tournamentRegisterService.findAll()));
                return;
            }
            key = cleanKey(key, "registerId", res);
            if (key == null) return;
            TournamentRegister entity = tournamentRegisterService.findById(key);
            if (entity == null) JsonUtil.sendError(res, 404, "TournamentRegister not found");
            else JsonUtil.sendJson(res, 200, toResponse(entity));
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUser(req, res);
        if(ownerUserId == null){
            return;
        }

        TournamentRegister entity = readTournamentRegister(req, res);
        if (entity == null) return;
        try {
            LocalDateTime tournamentStartDateTime = tournamentService.getRegistrationTimeLimit(entity.getTournamentId());
            if(tournamentStartDateTime == null){
                return;
            }
            if(!tournamentStartDateTime.isAfter(LocalDateTime.now())){
                JsonUtil.sendError(res, 400, "registration closed, cannot register !");
                return;
            }
            TournamentRegister created = tournamentRegisterService.create(ownerUserId, entity);
            if (created == null) {
                JsonUtil.sendError(res, 409, "Tournament not found or participant limit reached");
                return;
            }
            JsonUtil.sendJson(res, 201, toResponse(created));

        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        JsonUtil.sendError(res, 405, "Registration status is managed by the server");
    }

    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUser(req, res);
        if (ownerUserId == null) return;

        String registerId = req.getParameter("registerId");
        if (registerId == null) registerId = req.getParameter("id");
        registerId = cleanKey(registerId, "registerId", res);
        if (registerId == null) return;
        try {
            if (tournamentRegisterService.delete(ownerUserId, registerId)) {
                JsonUtil.sendJson(res, 200, Map.of("message", "TournamentRegister deleted"));
            } else {
                JsonUtil.sendError(res, 404, "TournamentRegister not found");
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    private TournamentRegister readTournamentRegister(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            TournamentRegister entity = JsonUtil.fromJson(reader, TournamentRegister.class);
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

    private TournamentRegisterResponse toResponse(TournamentRegister entity) {
        return new TournamentRegisterResponse(entity.getRegisterId(), entity.getTournamentId(), entity.getUserId(), entity.getPaymentId(), entity.getStatus(), entity.getRegisteredDate());
    }

    private List<TournamentRegisterResponse> toResponseList(List<TournamentRegister> entities) {
        return entities.stream().map(this::toResponse).toList();
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "TournamentRegister database operation failed (SQLState=" + e.getSQLState()
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
}
