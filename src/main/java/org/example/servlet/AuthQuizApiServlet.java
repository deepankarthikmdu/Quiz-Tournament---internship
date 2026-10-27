package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.model.Quiz;
import org.example.service.QuizService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/quizzes/start", "/api/quizzes/stop"})
public class AuthQuizApiServlet extends HttpServlet {
    public static final String SESSION_QUIZ_ID = "quizId";
    private final QuizService quizService = new QuizService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try{
            String userId = authenticatedUser(req, res);
            if(userId == null){
                return;
            }

            if("/api/quizzes/stop".equals(req.getServletPath())){
                quizStop(req, res);
                return;
            }

            Quiz quiz = readQuiz(req, res);
            if(quiz == null){
                return;
            }

            HttpSession session = req.getSession(false);
            if(session.getAttribute(SESSION_QUIZ_ID) != null ){
                JsonUtil.sendError(res, 400, "stop the current quiz to create a new quiz ");
                return;
            }

            Quiz created = quizService.create(userId, quiz);
            session.setAttribute(SESSION_QUIZ_ID, created.getQuizId());
            session.setMaxInactiveInterval(30*60);
            JsonUtil.sendJson(res, 200, created);

        }catch (IllegalArgumentException e){
            JsonUtil.sendError(res, 400, e.getMessage());
        }catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    protected void doPut (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try{
            String userId = authenticatedUser(req, res);
            if(userId == null){
                return;
            }

            if("/api/quizzes/stop".equals(req.getServletPath())){
                quizStop(req, res);
                return;
            }

            Quiz quiz = readQuiz(req, res);
            if(quiz == null){
                return;
            }

            Quiz quiz1 = quizService.findById(quiz.getQuizId());
            if(quiz1 == null){
                return;
            }

            if(!quiz1.getUserId().equals(userId)){
                JsonUtil.sendError(res, 400, "Only the owner of this quiz can set updates !");
                return;
            }

            HttpSession session = req.getSession(false);
            if(session.getAttribute(SESSION_QUIZ_ID) != null ){
                JsonUtil.sendError(res, 400, "stop the current quiz to create a new quiz ");
                return;
            }

            quiz.setQuizName(quiz1.getQuizName());
            quiz.setQuizCategory(quiz1.getQuizCategory());

            Quiz updated = quizService.update(userId, quiz.getQuizId(), quiz);
            session.setAttribute(SESSION_QUIZ_ID, updated.getQuizId());
            session.setMaxInactiveInterval(30*60);
            JsonUtil.sendJson(res, 200,Map.of("status: ", "quiz started !",
                "quiz: ", updated));

        }catch (IllegalArgumentException e){
            JsonUtil.sendError(res, 400, e.getMessage());
        }catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    protected Quiz readQuiz (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            Quiz quiz = JsonUtil.fromJson(reader, Quiz.class);
            if(quiz == null
                    || quiz.getQuizId() == null || quiz.getQuizId().isBlank()
                    || quiz.getQuizName() == null || quiz.getQuizName().isBlank()){
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

    private void quizStop (HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if(session != null) { session.removeAttribute(SESSION_QUIZ_ID); }
        JsonUtil.sendJson(res, 200, Map.of("message", "Quiz stopped"));
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
