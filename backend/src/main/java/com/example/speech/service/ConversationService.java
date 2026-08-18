package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.speech.entity.Conversation;
import com.example.speech.entity.ConversationMessage;
import com.example.speech.mapper.ConversationMapper;
import com.example.speech.mapper.ConversationMessageMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ConversationService {

    private final ConversationMapper conversationMapper;
    private final ConversationMessageMapper messageMapper;
    private final ApplicationEventPublisher eventPublisher;

    /** 创建新会话 */
    @Transactional
    public Conversation createConversation(Long userId, Long sceneId, String title) {
        Conversation conversation = new Conversation();
        conversation.setUserId(userId);
        conversation.setSceneId(sceneId);
        conversation.setTitle(title != null ? title : "新会话");
        conversation.setIsStarred(0);
        conversation.setDuration(0);
        conversation.setRoundCount(0);
        conversation.setErrorCount(0);
        conversation.setSuggestionCount(0);
        conversation.setStatus(1); // 进行中
        conversation.setCreateTime(LocalDateTime.now());
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.insert(conversation);
        return conversation;
    }

    /** 分页查询用户的会话列表 */
    public IPage<Conversation> getUserConversations(Long userId, int page, int size) {
        Page<Conversation> pageParam = new Page<>(page, size);
        return conversationMapper.selectPageByUserId(pageParam, userId);
    }

    /** 获取会话详情(含消息列表) */
    public Conversation getConversationDetail(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        // 数据隔离:非管理员只能查看自己的会话
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权访问他人会话");
        }
        return conversation;
    }

    /** 获取会话的消息列表 */
    public List<ConversationMessage> getConversationMessages(Long conversationId) {
        return messageMapper.selectList(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
                        .orderByAsc(ConversationMessage::getId)
        );
    }

    /** 添加消息到会话 */
    @Transactional
    public ConversationMessage addMessage(Long conversationId, String role, String content,
                                          String grammarJson, boolean hasError, boolean hasSuggestion) {
        ConversationMessage message = new ConversationMessage();
        message.setConversationId(conversationId);
        message.setRole(role);
        message.setContent(content);
        message.setGrammarJson(grammarJson);
        message.setHasError(hasError ? 1 : 0);
        message.setHasSuggestion(hasSuggestion ? 1 : 0);
        message.setCreateTime(LocalDateTime.now());
        messageMapper.insert(message);

        int addRounds = 0;
        int addErrors = 0;
        int addSuggestions = 0;

        // 更新会话统计信息
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation != null) {
            conversation.setUpdateTime(LocalDateTime.now());
            if ("user".equals(role)) {
                addRounds = 1;
                addErrors = hasError ? 1 : 0;
                addSuggestions = hasSuggestion ? 1 : 0;
                conversation.setRoundCount(conversation.getRoundCount() + addRounds);
                if (hasError) {
                    conversation.setErrorCount(conversation.getErrorCount() + 1);
                }
                if (hasSuggestion) {
                    conversation.setSuggestionCount(conversation.getSuggestionCount() + 1);
                }
            }
            conversationMapper.updateById(conversation);
        }

        // 只有用户消息(即完成一轮对话)才触发打卡更新
        if ("user".equals(role) && addRounds > 0 && conversation != null) {
            try {
                eventPublisher.publishEvent(new ConversationUpdateEvent(
                        this, conversation, addRounds, addErrors, addSuggestions, 0
                ));
            } catch (Exception _e) {
                // 忽略打卡事件错误,不影响主流程
            }
        }
        return message;
    }

    /** 结束会话 */
    @Transactional
    public Conversation endConversation(Long userId, Long conversationId, Integer duration, String summary) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        // 数据隔离
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权操作他人会话");
        }
        int prevDuration = conversation.getDuration() != null ? conversation.getDuration() : 0;
        int newDuration = duration != null ? duration : prevDuration;
        int addDuration = Math.max(0, newDuration - prevDuration);

        conversation.setStatus(2); // 已结束
        if (duration != null) {
            conversation.setDuration(duration);
        }
        if (summary != null) {
            conversation.setSummary(summary);
        }
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);

        // 有 roundCount 时,附带把时长同步到打卡记录
        if ((conversation.getRoundCount() != null && conversation.getRoundCount() > 0) || addDuration > 0) {
            try {
                eventPublisher.publishEvent(new ConversationUpdateEvent(
                        this, conversation, 0, 0, 0, addDuration
                ));
            } catch (Exception _e) {
                // 忽略打卡事件错误
            }
        }
        return conversation;
    }

    /** 删除会话 */
    @Transactional
    public void deleteConversation(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权删除他人会话");
        }
        // 删除会话下的所有消息
        messageMapper.delete(
                new LambdaQueryWrapper<ConversationMessage>()
                        .eq(ConversationMessage::getConversationId, conversationId)
        );
        conversationMapper.deleteById(conversationId);
    }

    /** 收藏/取消收藏会话 */
    @Transactional
    public Conversation toggleStar(Long userId, Long conversationId) {
        Conversation conversation = conversationMapper.selectById(conversationId);
        if (conversation == null) {
            throw new IllegalArgumentException("会话不存在");
        }
        if (!conversation.getUserId().equals(userId)) {
            throw new SecurityException("无权操作他人会话");
        }
        int newStarred = conversation.getIsStarred() == null || conversation.getIsStarred() == 0 ? 1 : 0;
        conversation.setIsStarred(newStarred);
        conversation.setUpdateTime(LocalDateTime.now());
        conversationMapper.updateById(conversation);
        return conversation;
    }

    /** 获取用户进行中的会话 */
    public Conversation getActiveConversation(Long userId) {
        return conversationMapper.selectOne(
                new LambdaQueryWrapper<Conversation>()
                        .eq(Conversation::getUserId, userId)
                        .eq(Conversation::getStatus, 1)
                        .orderByDesc(Conversation::getUpdateTime)
                        .last("LIMIT 1")
        );
    }
}
