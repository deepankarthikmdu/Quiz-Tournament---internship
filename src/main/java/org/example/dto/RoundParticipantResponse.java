package org.example.dto;

import java.time.LocalDateTime;

public record RoundParticipantResponse(
        String roundParticipantId,
        String roundId,
        String registerId,
        String qualifiedFromRoundId,
        String status,
        LocalDateTime qualifiedDate
) { }

