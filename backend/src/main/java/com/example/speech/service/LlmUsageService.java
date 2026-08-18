package com.example.speech.service;

import com.example.speech.config.DeepSeekProperties;
import com.example.speech.entity.LlmUsageLog;
import com.example.speech.mapper.LlmUsageLogMapper;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class LlmUsageService {

    private final LlmUsageLogMapper usageLogMapper;
    private final DeepSeekProperties deepSeekProperties;

    /** 异步记录 token 用量 */
    @Async
    public void logUsage(Long userId, Long conversationId, String operation,
                         int promptTokens, int completionTokens, int totalTokens) {
        LlmUsageLog log = new LlmUsageLog();
        log.setUserId(userId);
        log.setConversationId(conversationId);
        log.setModel(deepSeekProperties.model());
        log.setPromptTokens(promptTokens);
        log.setCompletionTokens(completionTokens);
        log.setTotalTokens(totalTokens);
        log.setOperation(operation);
        log.setCreatedAt(LocalDateTime.now());
        usageLogMapper.insert(log);
    }
}
