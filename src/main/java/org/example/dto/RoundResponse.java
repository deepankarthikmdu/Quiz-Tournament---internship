package org.example.dto;

import java.time.LocalDateTime;

public record RoundResponse(
        String roundId,
        String tournamentId,
        String quizId,
        int roundNumber,
        String name,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status
) { }
