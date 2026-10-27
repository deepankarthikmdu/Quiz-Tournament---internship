package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Question;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QuestionDao {
    private static final String SELECT_QUESTION = "SELECT questionId, description FROM Question";

    public List<Question> findAll() throws SQLException {
        List<Question> questions = new ArrayList<>();
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SELECT_QUESTION);
            ResultSet rs = ps.executeQuery()){

            while(rs.next()){
                questions.add(mapRow(rs));
            }
        }
        return questions;
    }

    public List<Question> findAllForOwner(String ownerUserId) throws SQLException {
        String sql = "SELECT DISTINCT qn.questionId, qn.description FROM Question qn "
                + "JOIN QuizQuestionMapping qm ON qm.questionId = qn.questionId "
                + "JOIN Quiz q ON q.quizId = qm.quizId WHERE q.userId = ?";
        List<Question> questions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) questions.add(mapRow(rs));
            }
        }
        return questions;
    }

    public List<Question> findAllForAttempt(String attemptId) throws SQLException {
        String sql = "SELECT DISTINCT q.`questionId`, q.`description` FROM `Question` q "
                + "JOIN `QuizQuestionMapping` qm ON qm.`questionId` = q.`questionId` "
                + "JOIN `Round` r ON r.`quizId` = qm.`quizId` "
                + "JOIN `RoundParticipant` rp ON rp.`roundId` = r.`roundId` "
                + "JOIN `Attempt` a ON a.`roundParticipantId` = rp.`roundParticipantId` "
                + "WHERE a.`attemptId` = ? ORDER BY q.`questionId`";
        List<Question> questions = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, attemptId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) questions.add(mapRow(rs));
            }
        }
        return questions;
    }

    public Question findById(String questionId) throws SQLException {
        String sql = SELECT_QUESTION + " WHERE questionId = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, questionId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Question findByIdForOwner(String questionId, String ownerUserId) throws SQLException {
        String sql = "SELECT DISTINCT qn.questionId, qn.description FROM Question qn "
                + "JOIN QuizQuestionMapping qm ON qm.questionId = qn.questionId "
                + "JOIN Quiz q ON q.quizId = qm.quizId "
                + "WHERE qn.questionId = ? AND q.userId = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, questionId);
            ps.setString(2, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Question create (Question question) throws SQLException {
        String sql = "INSERT INTO Question (questionId, description) values (?, ?)";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setString(1, question.getQuestionId());
            ps.setString(2, question.getDescription());
            ps.executeUpdate();
        }

        return question;
    }

    public Question update (String questionId, Question question) throws SQLException{
        String sql = " UPDATE Question SET description = ? WHERE questionId = ? ";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setString(1, question.getDescription());
            ps.setString(2, questionId);
            ps.executeUpdate();
        }
        return findById(questionId);
    }

    public boolean delete (String id) throws SQLException {
        String sql = "DELETE FROM Question WHERE questionId = ? ";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, id);

            return ps.executeUpdate() > 0;
        }
    }

    private Question mapRow(ResultSet rs) throws SQLException {
        Question question = new Question();
        question.setQuestionId(rs.getString("questionId"));
        question.setDescription(rs.getString("description"));
        return question;

    }
}
