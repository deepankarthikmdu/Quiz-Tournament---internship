package org.example.model;

import java.time.LocalDateTime;

public class RoundParticipant {
    private String roundParticipantId;
    private String roundId;
    private String registerId;
    private String qualifiedFromRoundId;
    private String status;
    private LocalDateTime qualifiedDate;

    public String getRoundParticipantId() {
        return roundParticipantId;
    }

    public void setRoundParticipantId(String roundParticipantId) {
        this.roundParticipantId = roundParticipantId;
    }

    public String getRoundId() {
        return roundId;
    }

    public void setRoundId(String roundId) {
        this.roundId = roundId;
    }

    public String getRegisterId() {
        return registerId;
    }

    public void setRegisterId(String registerId) {
        this.registerId = registerId;
    }

    public String getQualifiedFromRoundId() {
        return qualifiedFromRoundId;
    }

    public void setQualifiedFromRoundId(String qualifiedFromRoundId) {
        this.qualifiedFromRoundId = qualifiedFromRoundId;
    }

    public String getStatus() {
        return status;
    }

    public void setStatus(String status) {
        this.status = status;
    }

    public LocalDateTime getQualifiedDate() {
        return qualifiedDate;
    }

    public void setQualifiedDate(LocalDateTime qualifiedDate) {
        this.qualifiedDate = qualifiedDate;
    }
}

