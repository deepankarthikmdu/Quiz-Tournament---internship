package org.example.dto;



public record OptionResponse(
        String optionId,
        String questionId,
        String optionText,
        boolean isCorrect
) { }

