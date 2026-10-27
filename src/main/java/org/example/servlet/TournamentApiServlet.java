package org.example.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.google.gson.JsonParseException;
import org.example.dto.TournamentResponse;
import org.example.model.Tournament;
import org.example.service.TournamentService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/tournaments")
public class TournamentApiServlet extends HttpServlet {
    private final TournamentService tournamentService = new TournamentService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String idParam = req.getParameter("id");
        try {
            if (idParam == null) {
                JsonUtil.sendJson(res, 200, toResponseList(tournamentService.findAll()));
                return;
            }

            String tournamentId = idParam.trim();
            if (tournamentId.isEmpty() || tournamentId.length() > 36) {
                JsonUtil.sendError(res, 400, "id is invalid");
                return;
            }

            Tournament tournament = tournamentService.findById(tournamentId);
            if (tournament == null) {
                JsonUtil.sendError(res, 404, "Tournament not found");
            } else {
                JsonUtil.sendJson(res, 200, toResponse(tournament));
            }

        } catch (SQLException e) {
            getServletContext().log(
                    "Tournament database operation failed (SQLState=" + e.getSQLState()
                            + ", vendorCode=" + e.getErrorCode() + ")",
                    e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    @Override
    protected void doPost (HttpServletRequest req, HttpServletResponse res) throws IOException {
        String ownerUserId = authenticatedUserId(req, res);
        if (ownerUserId == null) return;

        try {
            Tournament tournament = readTournament(req, res);
            if (tournament == null) return;
            JsonUtil.sendJson(res, 201, toResponse(tournamentService.create(ownerUserId, tournament)));

        }catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
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

        Tournament tournament = readTournament(req, res);
        if (tournament == null){
            return;
        }

        try {
            Tournament updated = tournamentService.update(ownerUserId, id, tournament);
            if (updated == null){
                JsonUtil.sendError(res, 404, "Tournament not found");
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
            boolean deleted = tournamentService.delete(ownerUserId, id);
            if (!deleted) {
                JsonUtil.sendError(res, 404, "Tournament not found");
            } else {
                JsonUtil.sendJson(res, 200, Map.of("message", "Tournament deleted"));
            }
        }catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }

    }

    private TournamentResponse toResponse(Tournament tournament) {
        return new TournamentResponse(tournament.getTournamentId(), tournament.getUserId(), tournament.getName(), tournament.getDescription(), tournament.getEntryFee(), tournament.getStartDate(), tournament.getEndDate(), tournament.getStatus(), tournament.getParticipantLimit(), tournament.getParticipantCount(), tournament.getNumberOfRounds());
    }

    private List<TournamentResponse> toResponseList(List<Tournament> tournaments) {
        return tournaments.stream().map(this::toResponse).toList();
    }

    protected Tournament readTournament (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            Tournament tournament = JsonUtil.fromJson(reader, Tournament.class);
            if(tournament == null){
                JsonUtil.sendError(res, 400, "invalid missing body");
                return null;
            }
            return tournament;
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
            JsonUtil.sendError(res, 409, "Conflict: tournamentId already exists or the tournament is referenced by other data");
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
        if (value == null) return null;
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
