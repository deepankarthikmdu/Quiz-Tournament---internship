package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Quiz;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.PreparedStatement;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public class QuizDao {
    private static final String SELECT_QUIZ = "SELECT quizId, userId, quizName, quizCategory FROM Quiz";

    public List<Quiz> findAll() throws SQLException {
        List<Quiz> quizzes = new ArrayList<>();
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(SELECT_QUIZ);
            ResultSet rs = ps.executeQuery()){

            while(rs.next()){
                quizzes.add(mapRow(rs));
            }
        }
        return quizzes;
    }

    public List<Quiz> findAllForOwner(String ownerUserId) throws SQLException {
        List<Quiz> quizzes = new ArrayList<>();
        String sql = SELECT_QUIZ + " WHERE userId = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) quizzes.add(mapRow(rs));
            }
        }
        return quizzes;
    }

    public Quiz findById(String quizId) throws SQLException {
        String sql = SELECT_QUIZ + " WHERE quizId = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, quizId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Quiz findByIdForOwner(String quizId, String ownerUserId) throws SQLException {
        String sql = SELECT_QUIZ + " WHERE quizId = ? AND userId = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, quizId);
            ps.setString(2, ownerUserId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public Quiz createQuiz (Quiz quiz) throws SQLException {
        String sql = "INSERT INTO quiz (quizId, userId, quizName, quizCategory) values (?, ?, ?, ?)";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setString(1, quiz.getQuizId());
            ps.setString(2, quiz.getUserId());
            ps.setString(3, quiz.getQuizName());
            ps.setString(4, quiz.getQuizCategory());
            ps.executeUpdate();
        }

        return quiz;
    }

    public Quiz update (String quizId, Quiz quiz) throws SQLException{
        String sql = " UPDATE quiz SET quizName = ?, quizCategory = ? WHERE quizId = ? ";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){

            ps.setString(1, quiz.getQuizName());
            ps.setString(2, quiz.getQuizCategory());
            ps.setString(3, quizId);
            ps.executeUpdate();
        }
        return findById(quizId);
    }

    public boolean delete (String id) throws SQLException {
        String sql = "DELETE FROM quiz WHERE quizId = ? ";

        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)){
            ps.setString(1, id);

            return ps.executeUpdate() > 0;
        }
    }

    private Quiz mapRow(ResultSet rs) throws SQLException {
        Quiz quiz = new Quiz();
        quiz.setQuizId(rs.getString("quizId"));
        quiz.setUserId(rs.getString("userId"));
        quiz.setQuizName(rs.getString("quizName"));
        quiz.setQuizCategory(rs.getString("quizCategory"));
        return quiz;

    }

}
