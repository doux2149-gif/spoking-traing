package com.example.speech.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.ZoneId;

@Slf4j
@Component
@RequiredArgsConstructor
public class CheckinEventListener {

    private final CheckinService checkinService;

    @Async
    @EventListener(ConversationUpdateEvent.class)
    public void onConversationUpdate(ConversationUpdateEvent event) {
        try {
            Integer checkinType = event.getSceneId() != null ? 2 : 1;
            checkinService.recordCheckin(
                    event.getUserId(),
                    LocalDate.now(ZoneId.of("Asia/Shanghai")),
                    checkinType,
                    event.getAddDurationSeconds(),
                    event.getAddRounds(),
                    event.getAddErrors(),
                    event.getAddSuggestions()
            );
        } catch (Exception e) {
            log.warn("打卡更新失败: {}", e.getMessage(), e);
        }
    }
}
