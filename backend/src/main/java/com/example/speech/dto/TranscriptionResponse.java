package com.example.speech.dto;

import java.time.LocalDateTime;

public record TranscriptionResponse(
        Long id,
        String filename,
        String status,
        String text,
        String sid,
        LocalDateTime createdAt
) {
}
