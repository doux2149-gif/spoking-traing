package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.SpeakingScene;
import com.example.speech.mapper.SpeakingSceneMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class SpeakingSceneService {

    private final SpeakingSceneMapper sceneMapper;

    /** 获取启用的场景列表(用户端) */
    public List<SpeakingScene> getEnabledScenes() {
        return sceneMapper.selectList(
                new LambdaQueryWrapper<SpeakingScene>()
                        .eq(SpeakingScene::getStatus, 1)
                        .orderByAsc(SpeakingScene::getSort)
        );
    }

    /** 获取所有场景(管理端) */
    public List<SpeakingScene> getAllScenes() {
        return sceneMapper.selectList(
                new LambdaQueryWrapper<SpeakingScene>()
                        .orderByAsc(SpeakingScene::getSort)
        );
    }

    public SpeakingScene getSceneById(Long id) {
        SpeakingScene scene = sceneMapper.selectById(id);
        if (scene == null) {
            throw new IllegalArgumentException("场景不存在");
        }
        return scene;
    }

    public void createScene(SpeakingScene scene) {
        scene.setId(null);
        if (scene.getStatus() == null) {
            scene.setStatus(1);
        }
        if (scene.getSort() == null) {
            scene.setSort(0);
        }
        if (scene.getDifficulty() == null) {
            scene.setDifficulty(1);
        }
        sceneMapper.insert(scene);
    }

    public void updateScene(Long id, SpeakingScene scene) {
        SpeakingScene existing = sceneMapper.selectById(id);
        if (existing == null) {
            throw new IllegalArgumentException("场景不存在");
        }
        scene.setId(id);
        sceneMapper.updateById(scene);
    }

    public void deleteScene(Long id) {
        sceneMapper.deleteById(id);
    }
}
