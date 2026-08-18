package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.dto.TranscriptionResponse;
import com.example.speech.entity.TranscriptionTask;
import com.example.speech.mapper.TranscriptionTaskMapper;
import com.example.speech.service.iflytek.AudioValidator;
import com.example.speech.service.iflytek.IatResult;
import com.example.speech.service.iflytek.IflytekIatClient;
import com.example.speech.util.Strings;
import java.io.IOException;
import java.time.LocalDateTime;
import java.util.List;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class TranscriptionService {
    private final TranscriptionTaskMapper taskMapper;
    private final IflytekIatClient iatClient;
    private final AudioValidator audioValidator;

    public TranscriptionService(TranscriptionTaskMapper taskMapper, IflytekIatClient iatClient,
                                AudioValidator audioValidator) {
        this.taskMapper = taskMapper;
        this.iatClient = iatClient;
        this.audioValidator = audioValidator;
    }

    public TranscriptionResponse transcribe(MultipartFile file, String encoding,
                                             int sampleRate, String language) {
        String filename = file.getOriginalFilename() == null ? "未命名音频" : file.getOriginalFilename();
        byte[] bytes;
        try {
            bytes = file.getBytes();
        } catch (IOException exception) {
            throw new IllegalArgumentException("无法读取上传的音频文件", exception);
        }
        AudioValidator.ValidatedAudio audio = audioValidator.validate(bytes, filename, encoding, sampleRate);
        TranscriptionTask task = createTask(filename, language, encoding, sampleRate);
        try {
            IatResult result = iatClient.transcribe(
                    audio.bytes(), audio.encoding(), sampleRate, audio.frameSize(), language);
            task.setStatus("SUCCESS");
            task.setResultText(result.text());
            task.setSid(result.sid());
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            return toResponse(task);
        } catch (RuntimeException exception) {
            task.setStatus("FAILED");
            task.setErrorMessage(Strings.truncate(exception.getMessage(), 1000));
            task.setUpdatedAt(LocalDateTime.now());
            taskMapper.updateById(task);
            throw exception;
        }
    }

    public List<TranscriptionResponse> recent() {
        return taskMapper.selectList(new LambdaQueryWrapper<TranscriptionTask>()
                        .eq(TranscriptionTask::getStatus, "SUCCESS")
                        .orderByDesc(TranscriptionTask::getCreatedAt)
                        .last("LIMIT 10"))
                .stream()
                .map(this::toResponse)
                .toList();
    }

    private TranscriptionTask createTask(String filename, String language, String encoding, int sampleRate) {
        LocalDateTime now = LocalDateTime.now();
        TranscriptionTask task = new TranscriptionTask();
        task.setOriginalFilename(filename);
        task.setLanguage(language);
        task.setAudioEncoding(encoding);
        task.setSampleRate(sampleRate);
        task.setStatus("PROCESSING");
        task.setCreatedAt(now);
        task.setUpdatedAt(now);
        taskMapper.insert(task);
        return task;
    }

    private TranscriptionResponse toResponse(TranscriptionTask task) {
        return new TranscriptionResponse(task.getId(), task.getOriginalFilename(), task.getStatus(),
                task.getResultText(), task.getSid(), task.getCreatedAt());
    }
}
