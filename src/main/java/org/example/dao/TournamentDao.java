package org.example.dao;

import org.example.database.DatabaseConnection;
import org.example.model.Tournament;

import java.math.BigDecimal;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Timestamp;
import java.sql.Types;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

public class TournamentDao {

    private static final String SELECT_TOURNAMENT = "SELECT tournamentId, userId, name, description, "
            + "entryFee, startDate, endDate, status, participantLimit, participantCount, numberOfRounds "
            + "FROM Tournament";

    public List<Tournament> findAll() throws SQLException {
        List<Tournament> tournaments = new ArrayList<>();
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(SELECT_TOURNAMENT + " ORDER BY startDate, name");
             ResultSet rs = ps.executeQuery()) {

            while (rs.next()) {
                tournaments.add(mapRow(rs));
            }
        }
        return tournaments;
    }

    public boolean toggleStatus (String tournamentId, String status) throws SQLException {
        String sql = "UPDATE tournament SET status = ? WHERE tournamentId = ?";
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, status);
            ps.setString(2, tournamentId);
            return ps.executeUpdate() > 0;
        }
    }

    public String setPrizeMoney(String tournamentId, Double prizeMoney) throws SQLException {
        if (tournamentId == null || tournamentId.isBlank()) {
            throw new IllegalArgumentException("tournamentId is required");
        }
        if (prizeMoney == null || !Double.isFinite(prizeMoney) || prizeMoney < 0) {
            throw new IllegalArgumentException("prizeMoney must be a finite, non-negative amount");
        }

        BigDecimal prizeAmount = BigDecimal.valueOf(prizeMoney);
        String clearSql = "WITH final_round AS (\n" +
                "    SELECT roundId FROM `Round` WHERE tournamentId = ?\n" +
                "    ORDER BY roundNumber DESC LIMIT 1\n" +
                ")\n" +
                "UPDATE `TournamentResult` tr\n" +
                "JOIN `Attempt` a ON a.attemptId = tr.attemptId\n" +
                "JOIN `RoundParticipant` rp ON rp.roundParticipantId = a.roundParticipantId\n" +
                "JOIN final_round fr ON fr.roundId = rp.roundId\n" +
                "SET tr.prizeMoney = NULL";
        String winnerSql = "WITH final_round AS (\n" +
                "    SELECT roundId\n" +
                "    FROM `Round`\n" +
                "    WHERE tournamentId = ?\n" +
                "    ORDER BY roundNumber DESC\n" +
                "    LIMIT 1\n" +
                ")\n" +
                "SELECT a.attemptId\n" +
                "FROM final_round fr\n" +
                "JOIN `RoundParticipant` rp ON rp.roundId = fr.roundId\n" +
                "JOIN `Attempt` a ON a.roundParticipantId = rp.roundParticipantId\n" +
                "JOIN `TournamentResult` tr ON tr.attemptId = a.attemptId\n" +
                "WHERE a.endDateTime IS NOT NULL AND tr.rank_ = 1\n" +
                "ORDER BY tr.score DESC, " +
                "TIMESTAMPDIFF(MICROSECOND, a.startAttemptDateTime, a.endDateTime) ASC, a.attemptId ASC\n" +
                "LIMIT 1";
        String awardSql = "UPDATE `TournamentResult` SET `prizeMoney` = ? WHERE `attemptId` = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement clear = conn.prepareStatement(clearSql)) {
                    clear.setString(1, tournamentId);
                    clear.executeUpdate();
                }

                String winnerAttemptId;
                try (PreparedStatement select = conn.prepareStatement(winnerSql)) {
                    select.setString(1, tournamentId);
                    try (ResultSet rs = select.executeQuery()) {
                        if (!rs.next()) {
                            conn.commit();
                            return null;
                        }
                        winnerAttemptId = rs.getString("attemptId");
                    }
                }

                try (PreparedStatement award = conn.prepareStatement(awardSql)) {
                    award.setBigDecimal(1, prizeAmount);
                    award.setString(2, winnerAttemptId);
                    if (award.executeUpdate() != 1) {
                        throw new SQLException("Could not assign prize money to the winning attempt");
                    }
                }
                conn.commit();
                return winnerAttemptId;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    public int completeAndRank(String tournamentId) throws SQLException {
        String lockSql = "SELECT `status` FROM `Tournament` WHERE `tournamentId` = ? FOR UPDATE";
        String resultsSql = "WITH final_round AS (\n" +
                "    SELECT roundId\n" +
                "    FROM `Round`\n" +
                "    WHERE tournamentId = ? \n" +
                "    ORDER BY roundNumber DESC\n" +
                "    LIMIT 1\n" +
                ")\n" +
                "SELECT\n" +
                "    u.userId,\n" +
                "    u.name,\n" +
                "    u.username,\n" +
                "    a.attemptId,\n" +
                "    tr.score,\n" +
                "    TIMESTAMPDIFF(MICROSECOND, a.startAttemptDateTime, a.endDateTime) AS attemptDurationMicros\n" +
                "FROM final_round fr\n" +
                "JOIN `RoundParticipant` rp ON rp.roundId = fr.roundId\n" +
                "JOIN `TournamentRegister` reg ON reg.registerId = rp.registerId\n" +
                "JOIN `User` u ON u.userId = reg.userId\n" +
                "JOIN `Attempt` a ON a.roundParticipantId = rp.roundParticipantId\n" +
                "JOIN `TournamentResult` tr ON tr.attemptId = a.attemptId\n" +
                "WHERE a.endDateTime IS NOT NULL\n" +
                "ORDER BY tr.score DESC, attemptDurationMicros ASC, u.name, a.attemptId;";
        String rankSql = "UPDATE `TournamentResult` SET `rank_` = ? WHERE `attemptId` = ?";
        String completeSql = "UPDATE `Tournament` SET `status` = 'completed' WHERE `tournamentId` = ?";

        try (Connection conn = DatabaseConnection.getConnection()) {
            conn.setAutoCommit(false);
            try {
                try (PreparedStatement lock = conn.prepareStatement(lockSql)) {
                    lock.setString(1, tournamentId);
                    try (ResultSet rs = lock.executeQuery()) {
                        if (!rs.next()) {
                            conn.rollback();
                            return -1;
                        }
                    }
                }

                int rankedAttemptCount = 0;
                try (PreparedStatement select = conn.prepareStatement(resultsSql);
                     PreparedStatement update = conn.prepareStatement(rankSql)) {
                    select.setString(1, tournamentId);
                    try (ResultSet rs = select.executeQuery()) {
                        int position = 0;
                        Integer previousScore = null;
                        Long previousDurationMicros = null;
                        int previousRank = 0;
                        while (rs.next()) {
                            int score = rs.getInt("score");
                            long durationMicros = rs.getLong("attemptDurationMicros");
                            position++;
                            int rank = previousScore != null
                                    && score == previousScore
                                    && previousDurationMicros != null
                                    && durationMicros == previousDurationMicros
                                    ? previousRank
                                    : position;
                            update.setInt(1, rank);
                            update.setString(2, rs.getString("attemptId"));
                            update.addBatch();
                            previousScore = score;
                            previousDurationMicros = durationMicros;
                            previousRank = rank;
                        }
                        rankedAttemptCount = position;
                    }
                    update.executeBatch();
                }

                try (PreparedStatement complete = conn.prepareStatement(completeSql)) {
                    complete.setString(1, tournamentId);
                    complete.executeUpdate();
                }
                conn.commit();
                return rankedAttemptCount;
            } catch (SQLException | RuntimeException e) {
                try {
                    conn.rollback();
                } catch (SQLException rollbackError) {
                    e.addSuppressed(rollbackError);
                }
                throw e;
            }
        }
    }

    public LocalDateTime getRegistrationTimeLimit (String tournamentId) throws SQLException {
        String selectSql = "SELECT startDate FROM tournament WHERE tournamentId = ?";
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(selectSql)){
            ps.setString(1,tournamentId);
            try(ResultSet rs = ps.executeQuery()){
                return rs.next() ? rs.getTimestamp("startDate").toLocalDateTime() : null;
            }
        }
    }

    public Tournament findById(String tournamentId) throws SQLException {
        String sql = SELECT_TOURNAMENT + " WHERE tournamentId = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? mapRow(rs) : null;
            }
        }
    }

    public String getUserId (String tournamentId) throws SQLException {
        String sql = "SELECT userId FROM tournament WHERE tournamentId = ?";
        try(Connection conn = DatabaseConnection.getConnection();
            PreparedStatement ps = conn.prepareStatement(sql)) {
            ps.setString(1, tournamentId);
            try (ResultSet rs = ps.executeQuery()){
                return rs.next() ? rs.getString("userId") : null ;
            }
        }

    }

    public Tournament create(Tournament tournament) throws SQLException {
        String sql = "INSERT INTO Tournament (tournamentId, userId, name, description, entryFee, "
                + "startDate, endDate, status, participantLimit, participantCount, numberOfRounds) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tournament.getTournamentId());
            ps.setString(2, tournament.getUserId());
            ps.setString(3, tournament.getName());
            ps.setString(4, tournament.getDescription());
            ps.setBigDecimal(5, tournament.getEntryFee());
            setDateTime(ps, 6, tournament.getStartDate());
            setDateTime(ps, 7, tournament.getEndDate());
            ps.setString(8, tournament.getStatus());
            ps.setInt(9, tournament.getParticipantLimit());
            ps.setInt(10, tournament.getParticipantCount());
            ps.setInt(11, tournament.getNumberOfRounds());
            ps.executeUpdate();
        }
        return tournament;
    }

    public Tournament update(String tournamentId, Tournament tournament) throws SQLException {
        String sql = "UPDATE Tournament SET userId = ?, name = ?, description = ?, entryFee = ?, "
                + "startDate = ?, endDate = ?, status = ?, participantLimit = ?, participantCount = ?, "
                + "numberOfRounds = ? WHERE tournamentId = ?";

        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tournament.getUserId());
            ps.setString(2, tournament.getName());
            ps.setString(3, tournament.getDescription());
            ps.setBigDecimal(4, tournament.getEntryFee());
            setDateTime(ps, 5, tournament.getStartDate());
            setDateTime(ps, 6, tournament.getEndDate());
            ps.setString(7, tournament.getStatus());
            ps.setInt(8, tournament.getParticipantLimit());
            ps.setInt(9, tournament.getParticipantCount());
            ps.setInt(10, tournament.getNumberOfRounds());
            ps.setString(11, tournamentId);
            ps.executeUpdate();
        }
        return findById(tournamentId);
    }

    public boolean delete(String tournamentId) throws SQLException {
        String sql = "DELETE FROM Tournament WHERE tournamentId = ?";
        try (Connection conn = DatabaseConnection.getConnection();
             PreparedStatement ps = conn.prepareStatement(sql)) {

            ps.setString(1, tournamentId);
            return ps.executeUpdate() > 0;
        }
    }

    private Tournament mapRow(ResultSet rs) throws SQLException {
        Tournament tournament = new Tournament();
        tournament.setTournamentId(rs.getString("tournamentId"));
        tournament.setUserId(rs.getString("userId"));
        tournament.setName(rs.getString("name"));
        tournament.setDescription(rs.getString("description"));
        BigDecimal entryFee = rs.getBigDecimal("entryFee");
        tournament.setEntryFee(rs.wasNull() ? null : entryFee);
        tournament.setStartDate(toLocalDateTime(rs.getTimestamp("startDate")));
        tournament.setEndDate(toLocalDateTime(rs.getTimestamp("endDate")));
        tournament.setStatus(rs.getString("status"));
        tournament.setParticipantLimit(rs.getInt("participantLimit"));
        tournament.setParticipantCount(rs.getInt("participantCount"));
        tournament.setNumberOfRounds(rs.getInt("numberOfRounds"));
        return tournament;
    }

    private void setDateTime(PreparedStatement ps, int parameterIndex, LocalDateTime value) throws SQLException {
        if (value == null) {
            ps.setNull(parameterIndex, Types.TIMESTAMP);
        } else {
            ps.setTimestamp(parameterIndex, Timestamp.valueOf(value));
        }
    }

    private LocalDateTime toLocalDateTime(Timestamp value) {
        return value == null ? null : value.toLocalDateTime();
    }
}
