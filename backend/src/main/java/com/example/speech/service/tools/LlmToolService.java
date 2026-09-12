package com.example.speech.service.tools;

import com.example.speech.entity.SysLlmTool;
import com.example.speech.mapper.SysLlmToolMapper;
import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import java.util.List;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

/**
 * 动态 LLM 工具管理服务。
 * <p>管理端 CRUD, 每次写操作 version+1 并发布 {@link ToolRefreshEvent} 即时刷新缓存。</p>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class LlmToolService {

    private final SysLlmToolMapper toolMapper;
    private final ApplicationEventPublisher eventPublisher;
    private final HttpToolProvider httpToolProvider;

    public List<SysLlmTool> list(String keyword, Integer enabled) {
        LambdaQueryWrapper<SysLlmTool> w = new LambdaQueryWrapper<>();
        if (keyword != null && !keyword.isBlank()) {
            w.and(q -> q.like(SysLlmTool::getToolName, keyword)
                    .or().like(SysLlmTool::getDescription, keyword));
        }
        if (enabled != null) {
            w.eq(SysLlmTool::getEnabled, enabled);
        }
        w.orderByDesc(SysLlmTool::getId);
        return toolMapper.selectList(w);
    }

    public SysLlmTool get(Long id) {
        SysLlmTool tool = toolMapper.selectById(id);
        if (tool == null) {
            throw new IllegalStateException("工具不存在: id=" + id);
        }
        return tool;
    }

    public void create(SysLlmTool tool) {
        validate(tool);
        // 名称唯一
        Long count = toolMapper.selectCount(
                new LambdaQueryWrapper<SysLlmTool>().eq(SysLlmTool::getToolName, tool.getToolName()));
        if (count != null && count > 0) {
            throw new IllegalStateException("工具名已存在: " + tool.getToolName());
        }
        tool.setId(null);
        tool.setVersion(0L);
        tool.setEnabled(tool.getEnabled() == null ? 1 : tool.getEnabled());
        if (tool.getToolType() == null || tool.getToolType().isBlank()) {
            tool.setToolType("HTTP");
        }
        toolMapper.insert(tool);
        publishRefresh("create", tool.getToolName());
        log.info("创建工具: {}", tool.getToolName());
    }

    public void update(Long id, SysLlmTool tool) {
        SysLlmTool existing = get(id);
        validate(tool);
        // 名称唯一(排除自身)
        Long count = toolMapper.selectCount(
                new LambdaQueryWrapper<SysLlmTool>()
                        .eq(SysLlmTool::getToolName, tool.getToolName())
                        .ne(SysLlmTool::getId, id));
        if (count != null && count > 0) {
            throw new IllegalStateException("工具名已存在: " + tool.getToolName());
        }
        existing.setToolName(tool.getToolName());
        existing.setDescription(tool.getDescription());
        existing.setParametersSchema(tool.getParametersSchema());
        existing.setToolType(tool.getToolType() == null ? existing.getToolType() : tool.getToolType());
        existing.setHttpEndpoint(tool.getHttpEndpoint());
        existing.setHttpMethod(tool.getHttpMethod());
        existing.setHttpHeaders(tool.getHttpHeaders());
        existing.setTimeoutMs(tool.getTimeoutMs());
        existing.setRemark(tool.getRemark());
        existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        toolMapper.updateById(existing);
        publishRefresh("update", existing.getToolName());
        log.info("更新工具: {}", existing.getToolName());
    }

    /** 启停切换 */
    public void toggle(Long id) {
        SysLlmTool existing = get(id);
        existing.setEnabled(existing.getEnabled() != null && existing.getEnabled() == 1 ? 0 : 1);
        existing.setVersion(existing.getVersion() == null ? 1 : existing.getVersion() + 1);
        toolMapper.updateById(existing);
        publishRefresh("toggle", existing.getToolName());
        log.info("切换工具 {} 启用状态: {}", existing.getToolName(), existing.getEnabled());
    }

    public void delete(Long id) {
        SysLlmTool existing = get(id);
        toolMapper.deleteById(id);
        publishRefresh("delete", existing.getToolName());
        log.info("删除工具: {}", existing.getToolName());
    }

    /** 试运行(不经过 LLM, 直接调用工具看返回) */
    public String test(Long id, String argumentsJson) {
        SysLlmTool tool = get(id);
        if (!"HTTP".equals(tool.getToolType())) {
            return "试运行仅支持 HTTP 类型工具";
        }
        return httpToolProvider.testExecute(tool.getToolName(), argumentsJson);
    }

    private void validate(SysLlmTool tool) {
        if (tool.getToolName() == null || tool.getToolName().isBlank()) {
            throw new IllegalStateException("工具名不能为空");
        }
        if (tool.getDescription() == null || tool.getDescription().isBlank()) {
            throw new IllegalStateException("工具描述不能为空");
        }
        if ("HTTP".equals(tool.getToolType())
                && (tool.getHttpEndpoint() == null || tool.getHttpEndpoint().isBlank())) {
            throw new IllegalStateException("HTTP 工具必须配置 endpoint");
        }
    }

    private void publishRefresh(String reason, String toolName) {
        eventPublisher.publishEvent(new ToolRefreshEvent(reason, toolName));
    }
}
