package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.service.LlmUsageStatsService;
import com.example.speech.util.CsvUtils;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

/**
 * 管理端 LLM 用量与成本统计。start/end 为 yyyy-MM-dd, 默认近 7 天。
 */
@RestController
@RequestMapping("/api/system/llm-usage")
@RequiredArgsConstructor
@PreAuthorize("hasRole('ADMIN')")
public class LlmUsageStatsController {

    private final LlmUsageStatsService statsService;

    /** 区间汇总: 调用数/三类 token/估算成本 */
    @GetMapping("/summary")
    public Result<Map<String, Object>> summary(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(statsService.summary(start, end));
    }

    /** 每日趋势(无数据日补零) */
    @GetMapping("/daily")
    public Result<List<Map<String, Object>>> daily(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(statsService.daily(start, end));
    }

    /** 按模型聚合 */
    @GetMapping("/by-model")
    public Result<List<Map<String, Object>>> byModel(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(statsService.byModel(start, end));
    }

    /** 按操作类型聚合 */
    @GetMapping("/by-operation")
    public Result<List<Map<String, Object>>> byOperation(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(statsService.byOperation(start, end));
    }

    /** 用量 Top10 用户 */
    @GetMapping("/top-users")
    public Result<List<Map<String, Object>>> topUsers(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end) {
        return Result.success(statsService.topUsers(start, end));
    }

    /** 明细 CSV 导出(UTF-8 BOM) */
    @GetMapping("/export")
    public void export(
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate start,
            @RequestParam(required = false) @DateTimeFormat(pattern = "yyyy-MM-dd") LocalDate end,
            HttpServletResponse response) throws Exception {
        List<Map<String, Object>> rows = statsService.exportDetail(start, end);
        OutputStream out = CsvUtils.prepareCsvResponse(response, "llm-usage");
        Writer writer = new OutputStreamWriter(out, StandardCharsets.UTF_8);
        writer.write(CsvUtils.row("时间", "用户ID", "用户名", "模型", "操作",
                "输入Token", "输出Token", "总Token", "估算成本(元)"));
        for (Map<String, Object> row : rows) {
            writer.write(CsvUtils.row(
                    formatDateTime(row.get("createdAt")),
                    str(row.get("userId")),
                    str(row.get("username")),
                    str(row.get("model")),
                    str(row.get("operation")),
                    str(row.get("promptTokens")),
                    str(row.get("completionTokens")),
                    str(row.get("totalTokens")),
                    cost(row.get("cost"))
            ));
        }
        writer.flush();
    }

    private static String str(Object value) {
        return value == null ? "" : String.valueOf(value);
    }

    private static String cost(Object value) {
        if (value == null) {
            return "0";
        }
        BigDecimal decimal = value instanceof BigDecimal ? (BigDecimal) value : new BigDecimal(value.toString());
        return decimal.setScale(6, java.math.RoundingMode.HALF_UP).toPlainString();
    }

    /** 明细时间兼容 LocalDateTime / Timestamp */
    private static String formatDateTime(Object value) {
        if (value == null) {
            return "";
        }
        if (value instanceof LocalDateTime time) {
            return CsvUtils.formatTime(time);
        }
        String text = String.valueOf(value);
        return text.length() >= 19 ? text.substring(0, 19) : text;
    }
}
