package com.example.speech.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.speech.dto.Result;
import com.example.speech.entity.Word;
import com.example.speech.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping("/api/system/words")
@PreAuthorize("hasRole('ADMIN')")
@RequiredArgsConstructor
public class WordManageController {

    private final WordService wordService;

    @GetMapping
    public Result<IPage<Word>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(wordService.page(keyword, category, pageNum, pageSize));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(wordService.listCategories());
    }

    @PostMapping
    public Result<Word> create(@RequestBody Word word) {
        try {
            return Result.success(wordService.create(word));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PostMapping("/batch")
    public Result<List<Word>> batchCreate(@RequestBody List<Word> words) {
        try {
            return Result.success(wordService.batchCreate(words));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @PutMapping("/{id}")
    public Result<Word> update(@PathVariable Long id, @RequestBody Word word) {
        try {
            return Result.success(wordService.update(id, word));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    @DeleteMapping("/{id}")
    public Result<Void> delete(@PathVariable Long id) {
        wordService.delete(id);
        return Result.success();
    }

    @DeleteMapping("/batch")
    public Result<Void> batchDelete(@RequestBody Map<String, List<Long>> body) {
        wordService.batchDelete(body.get("ids"));
        return Result.success();
    }

    @PutMapping("/{id}/publish")
    public Result<Word> toggle(@PathVariable Long id, @RequestBody Map<String, Integer> body) {
        return Result.success(wordService.togglePublish(id, body.get("published")));
    }

    /** AI 批量生成单词 */
    @PostMapping("/ai-generate")
    public Result<WordService.GenerateResult> aiGenerate(@RequestBody Map<String, Object> body) {
        try {
            int count = body.get("count") != null ? Integer.valueOf(body.get("count").toString()) : 10;
            String category = body.get("category") != null ? body.get("category").toString() : null;
            Integer difficulty = body.get("difficulty") != null ? Integer.valueOf(body.get("difficulty").toString()) : null;
            String topic = body.get("topic") != null ? body.get("topic").toString() : null;
            return Result.success(wordService.aiGenerate(count, category, difficulty, topic));
        } catch (Exception e) {
            return Result.error(400, e.getMessage());
        }
    }

    /** 导出 CSV, UTF-8 BOM */
    @GetMapping("/export")
    public ResponseEntity<byte[]> exportCsv(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category) {
        byte[] csv = wordService.exportCsv(keyword, category);
        String filename = URLEncoder.encode(
                "单词列表_" + LocalDateTime.now().format(
                        java.time.format.DateTimeFormatter.ofPattern("yyyyMMddHHmmss")) + ".csv",
                StandardCharsets.UTF_8).replace("+", "%20");
        return ResponseEntity.ok()
                .contentType(new MediaType("text", "csv", StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"" + filename + "\"; filename*=UTF-8''" + filename)
                .body(csv);
    }
}
