package com.example.speech.controller;

import com.baomidou.mybatisplus.core.metadata.IPage;
import com.example.speech.dto.Result;
import com.example.speech.entity.Word;
import com.example.speech.service.WordService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * 用户端单词浏览接口, 不需要 ADMIN, 只要登录即可。
 * 只返回 is_published=1 的单词。
 */
@RestController
@RequestMapping("/api/words")
@RequiredArgsConstructor
public class WordUserController {

    private final WordService wordService;

    @GetMapping
    public Result<IPage<Word>> page(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) String category,
            @RequestParam(defaultValue = "1") int pageNum,
            @RequestParam(defaultValue = "20") int pageSize) {
        return Result.success(wordService.pagePublished(keyword, category, pageNum, pageSize));
    }

    @GetMapping("/categories")
    public Result<List<String>> categories() {
        return Result.success(wordService.listCategories());
    }
}
