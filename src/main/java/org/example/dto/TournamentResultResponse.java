package org.example.dto;

import java.math.BigDecimal;

public record TournamentResultResponse(
        String attemptId,
        int score,
        Integer rank,
        BigDecimal prizeMoney
) { }

