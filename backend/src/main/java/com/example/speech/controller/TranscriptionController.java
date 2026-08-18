package com.example.speech.controller;

import com.example.speech.dto.TranscriptionResponse;
import com.example.speech.service.TranscriptionService;
import java.util.List;
import org.springframework.http.MediaType;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/transcriptions")
public class TranscriptionController {
    private final TranscriptionService transcriptionService;

    public TranscriptionController(TranscriptionService transcriptionService) {
        this.transcriptionService = transcriptionService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public TranscriptionResponse transcribe(
            @RequestParam MultipartFile file,
            @RequestParam(defaultValue = "raw") String encoding,
            @RequestParam(defaultValue = "16000") int sampleRate,
            @RequestParam(required = false) String language) {
        return transcriptionService.transcribe(file, encoding, sampleRate, language);
    }

    @GetMapping
    public List<TranscriptionResponse> recent() {
        return transcriptionService.recent();
    }
}
