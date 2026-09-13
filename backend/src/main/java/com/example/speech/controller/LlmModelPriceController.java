package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.entity.LlmModelPrice;
import com.example.speech.service.LlmUsageStatsService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * 管理端 LLM 模型单价配置。
 */
@RestController
@RequestMapping("/api/system/llm-prices")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LlmModelPriceController {

    private final LlmUsageStatsService statsService;

    /** 全部单价配置 */
    @GetMapping
    public Result<List<LlmModelPrice>> list() {
        return Result.success(statsService.listPrices());
    }

    /** 批量保存(有 id 更新, 无 id 新增) */
    @PutMapping
    public Result<Void> save(@RequestBody List<LlmModelPrice> prices) {
        statsService.savePrices(prices);
        return Result.success();
    }
}
