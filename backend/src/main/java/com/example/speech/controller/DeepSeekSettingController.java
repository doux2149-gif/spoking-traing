package com.example.speech.controller;

import com.example.speech.dto.DeepSeekKeyUpdateRequest;
import com.example.speech.dto.Result;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.AppSettingService;
import jakarta.validation.Valid;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/system/settings/deepseek")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class DeepSeekSettingController {
    private final AppSettingService appSettingService;

    @GetMapping
    public Result<Map<String, Object>> getStatus() {
        return Result.success(Map.of("configured", appSettingService.isConfigured()));
    }

    @PutMapping
    public Result<Void> update(@Valid @RequestBody DeepSeekKeyUpdateRequest request) {
        appSettingService.updateApiKey(request.apiKey(), SecurityUtils.getCurrentUserId());
        return Result.success();
    }
}
