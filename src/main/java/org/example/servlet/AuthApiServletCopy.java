package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpSession;
import org.example.dto.LoginRequest;
import org.example.dto.UserResponse;
import org.example.service.UserService;
import org.example.util.JsonUtil;
import org.example.model.User;
import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet (urlPatterns = {"/api/auth/login", "/api/auth/logout"})
public class AuthApiServletCopy extends HttpServlet {

    public static final String SESSION_USER_ID = "userid";
    private final UserService userService = new UserService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        if("/api/auth/logout".equals(req.getServletPath())){
            logout(req, res);
            return;
        }

        LoginRequest loginRequest = readLoginRequest(req, res);
        if(loginRequest == null){
            return;
        }

        try{
            User user = userService.authenticate(loginRequest.username(), loginRequest.password());
            if(user == null){
                JsonUtil.sendError(res,401,"invalid username or password");
                return;
            }

            HttpSession session = req.getSession(true);
            req.changeSessionId();
            session.setAttribute(SESSION_USER_ID, user.getUserId());
            session.setMaxInactiveInterval(30*60);

            JsonUtil.sendJson(res, 200,
                    Map.of("message","login successful",
                            "User", new UserResponse(user.getUserId(),user.getUsername(),user.getName())));
        }catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    private LoginRequest readLoginRequest (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            LoginRequest login = JsonUtil.fromJson(reader, LoginRequest.class);
            if(login == null || login.username() == null || login.username().isBlank() || login.password() == null || login.password().isEmpty() || login.password().isBlank()){
                JsonUtil.sendError(res,400, "cannot username and password are required");
                return null;
            }
            return login;
        }
        catch (JsonParseException e) {
            JsonUtil.sendError(res, 400, "invalid or missing json body");
            return null;
        }
    }

    private void logout (HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if(session != null) session.invalidate();
        JsonUtil.sendJson(res, 200, Map.of("message", "logged out succesfull "));
    }
    private void logDatabaseError(SQLException e) {
        getServletContext().log(
                "Authentication database operation failed (SQLState=" + e.getSQLState()
                        + ", vendorCode=" + e.getErrorCode() + ")",
                e);
    }
}
