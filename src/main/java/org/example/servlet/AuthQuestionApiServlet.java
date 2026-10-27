package org.example.servlet;

import com.google.gson.JsonParseException;
import jakarta.servlet.annotation.WebServlet;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.servlet.http.HttpSession;
import org.example.model.Question;
import org.example.model.QuizQuestionMapping;
import org.example.service.QuestionService;
import org.example.service.QuizQuestionMappingService;
import org.example.util.JsonUtil;

import java.io.BufferedReader;
import java.io.IOException;
import java.sql.SQLException;
import java.util.Map;

@WebServlet(urlPatterns = {"/api/questions/start", "/api/questions/stop"})
public class AuthQuestionApiServlet extends HttpServlet {
    public static final String SESSION_QUESTION_ID = "questionId";
    private final QuestionService questionService = new QuestionService();
    private final QuizQuestionMappingService quizQuestionMappingService = new QuizQuestionMappingService();

    @Override
    protected void doPost(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try{
            String userId = authenticatedUser(req, res);
            if(userId == null){
                return;
            }

            String quizId = authenticatedQuiz(req, res);
            if(quizId == null){
                return;
            }

            if("/api/questions/stop".equals(req.getServletPath())){
                HttpSession session = req.getSession(false);
                Object questionid = session == null ? null : session.getAttribute(SESSION_QUESTION_ID);
                if(!(questionid instanceof String questionId) || questionId.isBlank()){
                    JsonUtil.sendError(res, 400, "no session for any question ");
                    return;
                }

                QuizQuestionMapping quiz = new QuizQuestionMapping();
                quiz.setQuizId(quizId);
                quiz.setQuestionId(questionId);
                QuizQuestionMapping created = quizQuestionMappingService.create(quiz);
                questionStop(req, res);
                JsonUtil.sendJson(res, 200, Map.of("message", "question logged successfully !",
                        "quiz and question mapping", created));
                return;
            }

            HttpSession session = req.getSession(false);
            Object questionid = session == null ? null : session.getAttribute(SESSION_QUESTION_ID);
            if(questionid != null){
                JsonUtil.sendError(res, 400, "stop the current question !");
                return;
            }

            Question question = readQuestion(req, res);
            if(question == null){
                return;
            }

            Question created = questionService.create(question);
            session = req.getSession(true);
            session.setAttribute(SESSION_QUESTION_ID, created.getQuestionId());
            session.setMaxInactiveInterval(30*60);
            JsonUtil.sendJson(res, 200, created);

        }catch (IllegalArgumentException e){
            JsonUtil.sendError(res, 400, e.getMessage());
        }catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    protected void doPut(HttpServletRequest req, HttpServletResponse res) throws IOException {
        try{
            String userId = authenticatedUser(req, res);
            if(userId == null){
                return;
            }

            String quizId = authenticatedQuiz(req, res);
            if(quizId == null){
                return;
            }

            if("/api/questions/stop".equals(req.getServletPath())){
                HttpSession session = req.getSession(false);
                Object questionid = session == null ? null : session.getAttribute(SESSION_QUESTION_ID);
                if(!(questionid instanceof String questionId) || questionId.isBlank()){
                    JsonUtil.sendError(res, 400, "no session for any question ");
                    return;
                }

                questionStop(req, res);
                JsonUtil.sendJson(res, 200, Map.of("message", "question logged successfully !"));
                return;
            }

            HttpSession session = req.getSession(false);
            Object questionid = session == null ? null : session.getAttribute(SESSION_QUESTION_ID);
            if(questionid != null){
                JsonUtil.sendError(res, 400, "stop the current question !");
                return;
            }

            Question question = readQuestion(req, res);
            if(question == null){
                return;
            }

            if(!quizId.equals(quizQuestionMappingService.findById(question.getQuestionId()).getQuizId())){
                JsonUtil.sendError(res, 400, "this question does not belong to the current open quiz");
                return;
            }

            Question updated = questionService.update(question.getQuestionId(), question);
            session = req.getSession(true);
            session.setAttribute(SESSION_QUESTION_ID, updated.getQuestionId());
            session.setMaxInactiveInterval(30*60);
            JsonUtil.sendJson(res, 200, Map.of("status: ", "question started",
                            "question: ", updated));

        }catch (IllegalArgumentException e){
            JsonUtil.sendError(res, 400, e.getMessage());
        }catch (SQLException e){
            logDatabaseError(e);
            JsonUtil.sendError(res, 500, "Database error");
        }
    }

    protected Question readQuestion (HttpServletRequest req, HttpServletResponse res) throws IOException {
        try(BufferedReader reader = req.getReader()){
            Question question = JsonUtil.fromJson(reader, Question.class);
            if(question == null){
                JsonUtil.sendError(res, 400, "invalid missing body");
                return null;
            }
            return question;
        }
        catch (JsonParseException e){
            JsonUtil.sendError(res, 400, "invalid missing body");
            return null;
        }
    }

    private void questionStop (HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        if(session != null) { session.removeAttribute(SESSION_QUESTION_ID);}
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

    private String authenticatedQuiz (HttpServletRequest req, HttpServletResponse res) throws IOException {
        HttpSession session = req.getSession(false);
        Object quizId = session == null ? null : session.getAttribute(AuthQuizApiServlet.SESSION_QUIZ_ID);
        if(!(quizId instanceof String id) || id.isBlank()) {
            JsonUtil.sendError(res, 401, "start a quiz !");
            return null;
        }
        return id;
    }
}
