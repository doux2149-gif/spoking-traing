package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.PracticeRecord;
import com.example.speech.entity.SpeakingScene;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.PracticeRecordService;
import com.example.speech.service.SpeakingSceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/** 用户端场景练习接口 */
@RestController
@RequestMapping("/api/scenes")
@RequiredArgsConstructor
public class SceneController {

    private final SpeakingSceneService sceneService;
    private final PracticeRecordService practiceService;

    /** 获取启用的场景列表 */
    @GetMapping
    public Result<List<SpeakingScene>> getScenes() {
        return Result.success(sceneService.getEnabledScenes());
    }

    /** 获取场景详情(包含开场白和 system prompt) */
    @GetMapping("/{id}")
    public Result<SpeakingScene> getScene(@PathVariable Long id) {
        try {
            return Result.success(sceneService.getSceneById(id));
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    /** 结束练习,生成报告 */
    @PostMapping("/{id}/finish")
    public Result<PracticeRecord> finishPractice(
            @PathVariable Long id,
            @RequestBody Map<String, Object> body) {
        try {
            Long userId = SecurityUtils.getCurrentUserId();
            int rounds = (int) body.getOrDefault("rounds", 0);
            int duration = (int) body.getOrDefault("duration", 0);
            int errorCount = (int) body.getOrDefault("errorCount", 0);
            String conversation = (String) body.getOrDefault("conversation", "");
            PracticeRecord record = practiceService.finishPractice(userId, id, rounds, duration, errorCount, conversation);
            return Result.success(record);
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 获取当前用户的练习记录 */
    @GetMapping("/practice/records")
    public Result<List<PracticeRecord>> getMyRecords() {
        Long userId = SecurityUtils.getCurrentUserId();
        return Result.success(practiceService.getUserRecords(userId));
    }
}
