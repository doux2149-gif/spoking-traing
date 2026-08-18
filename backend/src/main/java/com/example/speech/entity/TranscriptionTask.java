package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;

@TableName("transcription_task")
public class TranscriptionTask {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String originalFilename;
    private String language;
    private String audioEncoding;
    private Integer sampleRate;
    private String status;
    private String resultText;
    private String errorMessage;
    private String sid;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    public Long getId() { return id; }
    public void setId(Long id) { this.id = id; }
    public String getOriginalFilename() { return originalFilename; }
    public void setOriginalFilename(String value) { this.originalFilename = value; }
    public String getLanguage() { return language; }
    public void setLanguage(String value) { this.language = value; }
    public String getAudioEncoding() { return audioEncoding; }
    public void setAudioEncoding(String value) { this.audioEncoding = value; }
    public Integer getSampleRate() { return sampleRate; }
    public void setSampleRate(Integer value) { this.sampleRate = value; }
    public String getStatus() { return status; }
    public void setStatus(String value) { this.status = value; }
    public String getResultText() { return resultText; }
    public void setResultText(String value) { this.resultText = value; }
    public String getErrorMessage() { return errorMessage; }
    public void setErrorMessage(String value) { this.errorMessage = value; }
    public String getSid() { return sid; }
    public void setSid(String value) { this.sid = value; }
    public LocalDateTime getCreatedAt() { return createdAt; }
    public void setCreatedAt(LocalDateTime value) { this.createdAt = value; }
    public LocalDateTime getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(LocalDateTime value) { this.updatedAt = value; }
}
