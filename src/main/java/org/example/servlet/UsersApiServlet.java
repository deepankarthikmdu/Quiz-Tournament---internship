package org.example.servlet;

import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import com.google.gson.JsonParseException;
import org.example.dto.UserResponse;
import org.example.model.User;
import org.example.service.UserService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.sql.SQLIntegrityConstraintViolationException;
import java.util.List;
import java.util.Map;

@WebServlet("/api/users")
public class UsersApiServlet extends HttpServlet {

    private final UserService userService = new UserService();

    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String idParam = req.getParameter("id");
        try {
            if (idParam == null) {
                JsonUtil.sendJson(res, 200, toResponseList(userService.findAll()));
                return;
            }

            String id = cleanId(idParam);
            if (id == null) {
                JsonUtil.sendError(res, 400, "id is invalid");
                return;
            }

            User user = userService.findById(id);
            if (user == null) {
                JsonUtil.sendError(res, 404, "User not found");
            } else {
                JsonUtil.sendJson(res, 200, toResponse(user));
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        User user = readUser(req, res);
        if (user == null) {
            return;
        }

        try {
            JsonUtil.sendJson(res, 201, toResponse(userService.create(user)));
        } catch (IllegalArgumentException e) {
            JsonUtil.sendError(res, 400, e.getMessage());
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }

    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String id = requireId(req, res);
        if (id == null) {
            return;
        }
        if (!requireOwnAccount(req, res, id)) {
            return;
        }

        User user = readUser(req, res);
        if (user == null) {
            return;
        }

        try {
            User updated = userService.update(id, user);
            if (updated == null) {
                JsonUtil.sendError(res, 404, "User not found");
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
    protected void doDelete(HttpServletRequest req, HttpServletResponse res) throws IOException {
        String id = requireId(req, res);
        if (id == null) {
            return;
        }
        if (!requireOwnAccount(req, res, id)) {
            return;
        }

        try {
            if (userService.delete(id)) {
                JsonUtil.sendJson(res, 200, Map.of("message", "User deleted"));
            } else {
                JsonUtil.sendError(res, 404, "User not found");
            }
        } catch (SQLException e) {
            handleSqlError(res, e);
        }
    }


    private User readUser(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try (BufferedReader reader = req.getReader()) {
            User user = JsonUtil.fromJson(reader, User.class);
            if (user == null) {
                JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
                return null;
            }

            return user;
        } catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "Invalid or missing JSON body");
            return null;
        }
    }

    private UserResponse toResponse(User user) {
        return new UserResponse(user.getUserId(), user.getUsername(), user.getName());
    }

    private List<UserResponse> toResponseList(List<User> users) {
        return users.stream()
                .map(user -> new UserResponse(user.getUserId(), user.getUsername(), user.getName()))
                .toList();
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

    private boolean requireOwnAccount(HttpServletRequest req, HttpServletResponse res, String requestedUserId)
            throws IOException {
        HttpSession session = req.getSession(false);
        Object sessionUserId = session == null ? null : session.getAttribute(AuthApiServletCopy.SESSION_USER_ID);
        if (!(sessionUserId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, 401, "Login required");
            return false;
        }
        if (!id.equals(requestedUserId)) {
            JsonUtil.sendError(res, 403, "You can only modify your own account");
            return false;
        }
        return true;
    }

    private void handleSqlError(HttpServletResponse res, SQLException e) throws IOException {
        getServletContext().log(
                "Database operation failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")",
                e);

        if (e instanceof SQLIntegrityConstraintViolationException) {
            JsonUtil.sendError(res, 409, "Conflict: username already exists or the user is referenced by other data");
        } else {
            JsonUtil.sendError(res, 500, "Database error");
        }
    }
}
