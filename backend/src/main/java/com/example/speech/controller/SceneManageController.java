package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.SpeakingScene;
import com.example.speech.service.SpeakingSceneService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 管理端场景管理接口 */
@RestController
@RequestMapping("/api/system/scenes")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class SceneManageController {

    private final SpeakingSceneService sceneService;

    @GetMapping
    public Result<List<SpeakingScene>> list() {
        return Result.success(sceneService.getAllScenes());
    }

    @GetMapping("/{id}")
    public Result<SpeakingScene> get(@PathVariable Long id) {
        try {
            return Result.success(sceneService.getSceneById(id));
        } catch (Exception e) {
            return Result.error(404, e.getMessage());
        }
    }

    @PostMapping
    public Result<Void> create(@RequestBody SpeakingScene scene) {
        try {
            sceneService.createScene(scene);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Void> update(@PathVariable Long id, @RequestBody SpeakingScene scene) {
        try {
            sceneService.updateScene(id, scene);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        try {
            sceneService.deleteScene(id);
            return Result.success();
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }
}
