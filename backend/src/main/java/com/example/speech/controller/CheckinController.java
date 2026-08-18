package com.example.speech.controller;

import com.example.speech.dto.Result;
import com.example.speech.security.SecurityUtils;
import com.example.speech.service.CheckinService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/api/checkin")
@RequiredArgsConstructor
public class CheckinController {

    private final CheckinService checkinService;

    /**
     * 今日打卡状态 + 统计数据(侧边栏卡片用)
     */
    @GetMapping("/today")
    public Result<Map<String, Object>> todayStatus() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(checkinService.getTodayStatus(userId));
    }

    /**
     * 累计统计(详情页顶部卡片)
     */
    @GetMapping("/stats")
    public Result<Map<String, Object>> stats() {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        return Result.success(checkinService.getStats(userId));
    }

    /**
     * 月度打卡日历
     * 参数: year / month (不传默认当前年月)
     */
    @GetMapping("/calendar")
    public Result<Map<String, Map<String, Object>>> calendar(
            @RequestParam(required = false) Integer year,
            @RequestParam(required = false) Integer month) {
        Long userId = SecurityUtils.getCurrentUserId();
        if (userId == null) return Result.error(401, "未登录");
        java.time.LocalDate now = java.time.LocalDate.now();
        int y = year != null ? year : now.getYear();
        int m = month != null ? month : now.getMonthValue();
        return Result.success(checkinService.getCalendar(userId, y, m));
    }
}
