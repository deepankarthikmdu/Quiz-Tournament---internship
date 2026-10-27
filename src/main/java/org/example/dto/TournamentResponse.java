package org.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record TournamentResponse(
        String tournamentId,
        String userId,
        String name,
        String description,
        BigDecimal entryFee,
        LocalDateTime startDate,
        LocalDateTime endDate,
        String status,
        int participantLimit,
        int participantCount,
        int numberOfRounds
) { }
