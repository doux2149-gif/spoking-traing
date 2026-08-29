package com.example.speech.dto;

import jakarta.validation.constraints.NotBlank;

public record DeepSeekKeyUpdateRequest(@NotBlank(message = "DeepSeek API Key 不能为空") String apiKey) {
}
