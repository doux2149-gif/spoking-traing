package com.example.speech.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.speech.dto.Result;
import com.example.speech.entity.Conversation;
import com.example.speech.entity.ConversationMessage;
import com.example.speech.entity.ConversationReport;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.ConversationReportService;
import com.example.speech.service.ConversationService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 会话管理接口 */
@RestController
@RequestMapping("/api/conversations")
@RequiredArgsConstructor
public class ConversationController {

    private final ConversationService conversationService;
    private final ConversationReportService reportService;

    /** 创建新会话 */
    @PostMapping
    public Result<Conversation> createConversation(@RequestBody Map<String, Object> body) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Long sceneId = body.get("sceneId") != null ? Long.valueOf(body.get("sceneId").toString()) : null;
            String title = (String) body.get("title");
            Conversation conversation = conversationService.createConversation(userId, sceneId, title);
            return Result.success(conversation);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 分页查询会话列表 */
    @GetMapping
    public Result<IPage<Conversation>> getConversations(
            @RequestParam(defaultValue = "1") int page,
            @RequestParam(defaultValue = "10") int size) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            IPage<Conversation> conversations = conversationService.getUserConversations(userId, page, size);
            return Result.success(conversations);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 获取会话详情 */
    @GetMapping("/{id}")
    public Result<Conversation> getConversation(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Conversation conversation = conversationService.getConversationDetail(userId, id);
            return Result.success(conversation);
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    /** 获取会话的消息列表 */
    @GetMapping("/{id}/messages")
    public Result<List<ConversationMessage>> getMessages(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            // 验证会话归属
            conversationService.getConversationDetail(userId, id);
            List<ConversationMessage> messages = conversationService.getConversationMessages(id);
            return Result.success(messages);
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    /** 添加消息到会话 */
    @PostMapping("/{id}/messages")
    public Result<ConversationMessage> addMessage(@PathVariable Long id,
                                                   @RequestBody Map<String, Object> body) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            conversationService.getConversationDetail(userId, id);
            String role = (String) body.get("role");
            String content = (String) body.get("content");
            String grammarJson = (String) body.get("grammarJson");
            boolean hasError = body.get("hasError") != null && (boolean) body.get("hasError");
            boolean hasSuggestion = body.get("hasSuggestion") != null && (boolean) body.get("hasSuggestion");
            ConversationMessage message = conversationService.addMessage(id, role, content, grammarJson, hasError, hasSuggestion);
            return Result.success(message);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 结束会话: 同步规则聚合报告(status=1), @Async 再生成 LLM 摘要(status=2) */
    @PutMapping("/{id}/end")
    public Result<Conversation> endConversation(@PathVariable Long id,
                                                @RequestBody(required = false) Map<String, Object> body) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Integer duration = body != null && body.get("duration") != null
                    ? Integer.valueOf(body.get("duration").toString()) : null;
            String summary = body != null ? (String) body.get("summary") : null;
            Conversation conversation = conversationService.endConversation(userId, id, duration, summary);
            try {
                reportService.generateOnEnd(conversation);
            } catch (Exception e) {
                // 报告失败不影响结束会话主流程
            }
            return Result.success(conversation);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 查询报告: 返回当前状态(1=规则聚合完成, 2=LLM 已生成) 与结构化内容 */
    @GetMapping("/{id}/report")
    public Result<ConversationReport> getReport(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            ConversationReport report = reportService.getByConversation(id, userId);
            if (report == null) {
                return Result.error(404, "报告尚未生成");
            }
            return Result.success(report);
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    /** 前端手动触发/重新触发 LLM 摘要(当后端 @Async 失败或想要更新) */
    @PostMapping("/{id}/report/regenerate")
    public Result<ConversationReport> regenerateReport(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            conversationService.getConversationDetail(userId, id);
            ConversationReport existing = reportService.getByConversation(id, userId);
            ConversationReport report;
            if (existing != null) {
                report = reportService.regenerateSummary(existing.getId());
            } else {
                report = reportService.generateOnEnd(conversationService.getConversationDetail(userId, id));
            }
            return Result.success(report);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 当前用户全部报告 */
    @GetMapping("/reports")
    public Result<List<ConversationReport>> getMyReports() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(reportService.getByUser(userId));
    }

    /** 删除会话 */
    @DeleteMapping("/{id}")
    public Result<Void> deleteConversation(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            conversationService.deleteConversation(userId, id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 收藏/取消收藏会话 */
    @PutMapping("/{id}/star")
    public Result<Conversation> toggleStar(@PathVariable Long id) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Conversation conversation = conversationService.toggleStar(userId, id);
            return Result.success(conversation);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 获取当前进行中的会话 */
    @GetMapping("/active")
    public Result<Conversation> getActiveConversation() {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            Conversation conversation = conversationService.getActiveConversation(userId);
            return Result.success(conversation);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
