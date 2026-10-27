package org.example.dto;

import java.time.LocalDateTime;

public record TournamentAnswerResponse(
        String attemptId,
        String questionId,
        String selectedOptionId,
        Boolean isCorrect,
        LocalDateTime attemptedDate
) { }

