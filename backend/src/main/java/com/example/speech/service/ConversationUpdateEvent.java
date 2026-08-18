package com.example.speech.service;

import com.example.speech.entity.Conversation;
import lombok.Getter;
import org.springframework.context.ApplicationEvent;

/**
 * 会话更新事件(消息新增或会话结束),由 CheckinEventListener 异步处理打卡
 */
@Getter
public class ConversationUpdateEvent extends ApplicationEvent {
    private final Long userId;
    private final Long conversationId;
    private final Long sceneId;
    private final int addRounds;
    private final int addErrors;
    private final int addSuggestions;
    private final int addDurationSeconds;

    public ConversationUpdateEvent(Object source, Conversation conv,
                                   int addRounds, int addErrors,
                                   int addSuggestions, int addDurationSeconds) {
        super(source);
        this.userId = conv.getUserId();
        this.conversationId = conv.getId();
        this.sceneId = conv.getSceneId();
        this.addRounds = addRounds;
        this.addErrors = addErrors;
        this.addSuggestions = addSuggestions;
        this.addDurationSeconds = addDurationSeconds;
    }
}
