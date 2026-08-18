package com.example.speech.exception;

import java.time.Instant;

public record ApiError(String message, Instant timestamp) {
}
