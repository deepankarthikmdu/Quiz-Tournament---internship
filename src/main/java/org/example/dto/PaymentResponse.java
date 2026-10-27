package org.example.dto;

import java.math.BigDecimal;
import java.time.LocalDateTime;

public record PaymentResponse(
        String paymentId,
        String userId,
        BigDecimal amount,
        String description,
        LocalDateTime paymentDate,
        String status,
        String paymentGateway
) { }

