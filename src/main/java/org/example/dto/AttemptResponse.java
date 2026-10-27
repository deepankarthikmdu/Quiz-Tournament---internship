package org.example.dto;

import java.time.LocalDateTime;

public record AttemptResponse(
        String attemptId,
        String roundParticipantId,
        LocalDateTime startAttemptDateTime,
        LocalDateTime endDateTime
) { }

