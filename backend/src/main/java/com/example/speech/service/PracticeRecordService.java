package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.PracticeRecord;
import com.example.speech.entity.SpeakingScene;
import com.example.speech.mapper.PracticeRecordMapper;
import com.example.speech.service.deepseek.DeepSeekClient;
import com.example.speech.service.deepseek.DeepSeekClient.ChatMessage;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class PracticeRecordService {

    private final PracticeRecordMapper recordMapper;
    private final SpeakingSceneService sceneService;
    private final DeepSeekClient deepSeekClient;

    /** 结束练习:保存记录并生成 AI 总结 */
    public PracticeRecord finishPractice(Long userId, Long sceneId, int rounds, int duration, int errorCount, String conversationText) {
        SpeakingScene scene = sceneService.getSceneById(sceneId);

        // 调用 LLM 生成练习总结
        String summary = generateSummary(conversationText, scene, rounds, errorCount);
        int score = extractScore(summary);

        PracticeRecord record = new PracticeRecord();
        record.setUserId(userId);
        record.setSceneId(sceneId);
        record.setSceneName(scene.getName());
        record.setRounds(rounds);
        record.setDuration(duration);
        record.setErrorCount(errorCount);
        record.setScore(score);
        record.setSummary(summary);
        recordMapper.insert(record);
        return record;
    }

    /** 获取用户的练习记录 */
    public List<PracticeRecord> getUserRecords(Long userId) {
        return recordMapper.selectList(
                new LambdaQueryWrapper<PracticeRecord>()
                        .eq(PracticeRecord::getUserId, userId)
                        .orderByDesc(PracticeRecord::getCreateTime)
        );
    }

    private String generateSummary(String conversationText, SpeakingScene scene, int rounds, int errorCount) {
        List<ChatMessage> messages = new ArrayList<>();
        messages.add(new ChatMessage("system", """
                你是一名英语口语练习评估助手。根据用户的对话记录,给出简短的中文练习总结(不超过200字)。
                总结应包括:
                1. 整体表现评价(1-2句)
                2. 主要语法问题(如有)
                3. 改进建议(1-2句)
                最后另起一行,给出一个0-100的分数,格式: 评分:XX
                """));
        String prompt = String.format("""
                场景: %s(%s)
                对话轮数: %d
                语法错误数: %d

                对话记录:
                %s
                """, scene.getName(), scene.getAiRole(), rounds, errorCount, conversationText);
        messages.add(new ChatMessage("user", prompt));

        try {
            return deepSeekClient.chat(messages);
        } catch (Exception e) {
            return "练习总结生成失败。" + (rounds > 0 ? "你完成了 " + rounds + " 轮对话" : "") + ",语法错误 " + errorCount + " 处。继续加油!";
        }
    }

    /** 从总结文本中提取评分 */
    private int extractScore(String summary) {
        if (summary == null) return 70;
        String[] lines = summary.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.contains("评分")) {
                String numPart = trimmed.replaceAll("[^0-9]", "");
                if (!numPart.isEmpty()) {
                    int score = Integer.parseInt(numPart);
                    return Math.min(100, Math.max(0, score));
                }
            }
        }
        return 70;
    }
}
