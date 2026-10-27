package org.example.model;

import java.time.LocalDateTime;

public class Attempt {
    private String attemptId;
    private String roundParticipantId;
    private LocalDateTime startAttemptDateTime;
    private LocalDateTime endDateTime;

    public String getAttemptId() {
        return attemptId;
    }

    public void setAttemptId(String attemptId) {
        this.attemptId = attemptId;
    }

    public String getRoundParticipantId() {
        return roundParticipantId;
    }

    public void setRoundParticipantId(String roundParticipantId) {
        this.roundParticipantId = roundParticipantId;
    }

    public LocalDateTime getStartAttemptDateTime() {
        return startAttemptDateTime;
    }

    public void setStartAttemptDateTime(LocalDateTime startAttemptDateTime) {
        this.startAttemptDateTime = startAttemptDateTime;
    }

    public LocalDateTime getEndDateTime() {
        return endDateTime;
    }

    public void setEndDateTime(LocalDateTime endDateTime) {
        this.endDateTime = endDateTime;
    }
}

