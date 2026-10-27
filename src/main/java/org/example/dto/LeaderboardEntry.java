package org.example.dto;

import java.math.BigDecimal;

public record LeaderboardEntry(
        String attemptId,
        String tournamentId,
        String tournamentName,
        String userId,
        String username,
        String name,
        int score,
        Integer rank,
        BigDecimal prizeMoney,
        long attemptDurationMicros,
        String completedAt
) { }
