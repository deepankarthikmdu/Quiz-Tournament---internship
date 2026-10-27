package org.example.dto;

import java.time.LocalDateTime;

public record TournamentRegisterResponse(
        String registerId,
        String tournamentId,
        String userId,
        String paymentId,
        String status,
        LocalDateTime registeredDate
) { }

