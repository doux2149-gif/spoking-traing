package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.SysLlmTool;
import com.example.speech.service.tools.LlmToolService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

/**
 * 管理端 LLM 工具 CRUD + 试运行。
 * <p>所有写操作会触发 ToolRefreshEvent, 让 ToolRegistry 即时刷新。</p>
 */
@RestController
@RequestMapping("/api/system/llmtool")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LlmToolController {

    private final LlmToolService service;
    private final ObjectMapper objectMapper;

    @GetMapping
    public Result<List<SysLlmTool>> list(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) Integer enabled) {
        return Result.success(service.list(keyword, enabled));
    }

    @GetMapping("/{id}")
    public Result<SysLlmTool> get(@PathVariable Long id) {
        try {
            return Result.success(service.get(id));
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    @PostMapping
    public Result<Void> create(@RequestBody SysLlmTool tool) {
        try {
            service.create(tool);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SysLlmTool tool) {
        try {
            service.update(id, tool);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}/toggle")
    public Result<Void> toggle(@PathVariable Long id) {
        try {
            service.toggle(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            service.delete(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 试运行: 不经过 LLM, 直接用给定参数调用工具看返回 */
    @PostMapping("/{id}/test")
    public Result<Map<String, String>> test(@PathVariable Long id,
                                            @RequestBody(required = false) Map<String, Object> body) {
        try {
            String argsJson = null;
            if (body != null && body.get("arguments") != null) {
                // 把 arguments 字段序列化为 JSON 字符串(避免 Map.toString 产生非 JSON 格式)
                argsJson = objectMapper.writeValueAsString(body.get("arguments"));
            }
            String result = service.test(id, argsJson);
            Map<String, String> data = new HashMap<>();
            data.put("result", result);
            return Result.success(data);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
