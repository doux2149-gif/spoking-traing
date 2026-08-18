package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.example.speech.entity.UserCheckin;
import com.example.speech.entity.UserCheckinStats;
import com.example.speech.mapper.UserCheckinMapper;
import com.example.speech.mapper.UserCheckinStatsMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

@Slf4j
@Service
@RequiredArgsConstructor
public class CheckinService {

    private final UserCheckinMapper checkinMapper;
    private final UserCheckinStatsMapper statsMapper;

    /**
     * 根据会话结束累计的数据,刷新当日打卡记录+统计数据
     * 1. 会话 round_count == 0 则不触发(尚未对话)
     * 2. 使用 UPSERT 方式更新 user_checkin(累加)
     * 3. 重新计算 user_checkin_stats(连续打卡、累计天数等)
     */
    @Transactional
    public void recordCheckin(Long userId, LocalDate checkinDate,
                              Integer checkinType, int addSeconds,
                              int addRounds, int addErrors, int addSuggestions) {
        if (userId == null || checkinDate == null) return;
        if (addRounds <= 0 && addSeconds <= 0) return;

        // 1. 查找或创建当日打卡记录
        QueryWrapper<UserCheckin> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("checkin_date", checkinDate);
        UserCheckin record = checkinMapper.selectOne(qw);

        boolean isNewRecord = false;
        if (record == null) {
            record = new UserCheckin();
            record.setUserId(userId);
            record.setCheckinDate(checkinDate);
            record.setCheckinType(checkinType != null ? checkinType : 1);
            record.setTotalSeconds(0);
            record.setTotalRounds(0);
            record.setTotalErrors(0);
            record.setTotalSuggestions(0);
            record.setCreatedAt(LocalDateTime.now());
            isNewRecord = true;
        }
        record.setTotalSeconds(record.getTotalSeconds() + addSeconds);
        record.setTotalRounds(record.getTotalRounds() + addRounds);
        record.setTotalErrors(record.getTotalErrors() + addErrors);
        record.setTotalSuggestions(record.getTotalSuggestions() + addSuggestions);
        // 场景练习优先级高于自由对话(2>1)
        if (checkinType != null && checkinType > (record.getCheckinType() == null ? 0 : record.getCheckinType())) {
            record.setCheckinType(checkinType);
        }
        if (isNewRecord) {
            checkinMapper.insert(record);
        } else {
            checkinMapper.updateById(record);
        }

        // 2. 更新统计数据
        refreshStats(userId);
    }

    /**
     * 重新计算用户统计(连续打卡、最长连续、累计天数、累计时长/轮数)
     */
    private void refreshStats(Long userId) {
        QueryWrapper<UserCheckin> allQw = new QueryWrapper<>();
        allQw.eq("user_id", userId).orderByDesc("checkin_date");
        List<UserCheckin> allRecords = checkinMapper.selectList(allQw);

        int totalDays = allRecords.size();
        int totalSeconds = allRecords.stream().mapToInt(r -> r.getTotalSeconds() == null ? 0 : r.getTotalSeconds()).sum();
        int totalRounds = allRecords.stream().mapToInt(r -> r.getTotalRounds() == null ? 0 : r.getTotalRounds()).sum();

        // 计算连续打卡:从今天开始往前数连续日期
        LocalDate today = LocalDate.now();
        int streakCount = 0;
        LocalDate cursor = today;
        // 如果今天没打,从昨天开始往前数(即昨天是有效打卡起点)
        boolean todayExists = allRecords.stream().anyMatch(r -> today.equals(r.getCheckinDate()));
        if (!todayExists) {
            cursor = today.minusDays(1);
        }
        while (true) {
            LocalDate cur = cursor;
            boolean exists = allRecords.stream().anyMatch(r -> cur.equals(r.getCheckinDate()));
            if (exists) {
                streakCount++;
                cursor = cursor.minusDays(1);
            } else {
                break;
            }
        }

        // 计算最长连续(简单 O(n) 处理,日期已经倒序排好)
        int maxStreak = 0;
        int currentMax = 0;
        LocalDate prevCursor = null;
        for (UserCheckin r : allRecords) { // 倒序:新→旧
            if (prevCursor == null) {
                currentMax = 1;
            } else if (prevCursor.minusDays(1).equals(r.getCheckinDate())) {
                currentMax++;
            } else {
                currentMax = 1;
            }
            if (currentMax > maxStreak) maxStreak = currentMax;
            prevCursor = r.getCheckinDate();
        }

        // 写入统计
        QueryWrapper<UserCheckinStats> sqw = new QueryWrapper<>();
        sqw.eq("user_id", userId);
        UserCheckinStats stats = statsMapper.selectOne(sqw);
        boolean isNewStats = false;
        if (stats == null) {
            stats = new UserCheckinStats();
            stats.setUserId(userId);
            isNewStats = true;
        }
        stats.setStreakCount(streakCount);
        stats.setMaxStreak(maxStreak);
        stats.setTotalDays(totalDays);
        stats.setTotalSeconds(totalSeconds);
        stats.setTotalRounds(totalRounds);
        stats.setUpdatedAt(LocalDateTime.now());
        if (isNewStats) {
            statsMapper.insert(stats);
        } else {
            statsMapper.updateById(stats);
        }
    }

    /**
     * 获取今日打卡状态 + 统计数据(侧边栏卡片使用)
     */
    public Map<String, Object> getTodayStatus(Long userId) {
        Map<String, Object> result = new HashMap<>();
        LocalDate today = LocalDate.now();
        QueryWrapper<UserCheckin> qw = new QueryWrapper<>();
        qw.eq("user_id", userId).eq("checkin_date", today);
        UserCheckin todayRecord = checkinMapper.selectOne(qw);
        boolean checkedIn = todayRecord != null;
        result.put("todayCheckedIn", checkedIn);
        result.put("checkinDate", today.toString());
        if (checkedIn) {
            result.put("checkinType", todayRecord.getCheckinType());
            result.put("todayRounds", todayRecord.getTotalRounds());
            result.put("todaySeconds", todayRecord.getTotalSeconds());
            result.put("todayErrors", todayRecord.getTotalErrors());
            result.put("todaySuggestions", todayRecord.getTotalSuggestions());
        }

        QueryWrapper<UserCheckinStats> sqw = new QueryWrapper<>();
        sqw.eq("user_id", userId);
        UserCheckinStats stats = statsMapper.selectOne(sqw);
        if (stats != null) {
            result.put("streakCount", stats.getStreakCount());
            result.put("maxStreak", stats.getMaxStreak());
            result.put("totalDays", stats.getTotalDays());
            result.put("totalSeconds", stats.getTotalSeconds());
            result.put("totalRounds", stats.getTotalRounds());
        } else {
            result.put("streakCount", 0);
            result.put("maxStreak", 0);
            result.put("totalDays", 0);
            result.put("totalSeconds", 0);
            result.put("totalRounds", 0);
        }
        return result;
    }

    /**
     * 获取累计统计(详情页顶部四个方块)
     */
    public Map<String, Object> getStats(Long userId) {
        Map<String, Object> result = getTodayStatus(userId);
        // 去掉今日当日细节,只保留统计字段 + checkedIn
        result.remove("todayRounds");
        result.remove("todaySeconds");
        result.remove("todayErrors");
        result.remove("todaySuggestions");
        return result;
    }

    /**
     * 获取指定月份的打卡日历
     * 返回 Map: dateStr(YYYY-MM-DD) -> 当日打卡详情/null
     */
    public Map<String, Map<String, Object>> getCalendar(Long userId, int year, int month) {
        Map<String, Map<String, Object>> result = new HashMap<>();
        LocalDate monthStart = LocalDate.of(year, month, 1);
        LocalDate monthEnd = monthStart.plusMonths(1).minusDays(1);

        QueryWrapper<UserCheckin> qw = new QueryWrapper<>();
        qw.eq("user_id", userId)
          .ge("checkin_date", monthStart)
          .le("checkin_date", monthEnd)
          .orderByAsc("checkin_date");
        List<UserCheckin> records = checkinMapper.selectList(qw);

        for (UserCheckin r : records) {
            Map<String, Object> item = new HashMap<>();
            item.put("checkinType", r.getCheckinType());
            item.put("totalSeconds", r.getTotalSeconds());
            item.put("totalRounds", r.getTotalRounds());
            item.put("totalErrors", r.getTotalErrors());
            item.put("totalSuggestions", r.getTotalSuggestions());
            result.put(r.getCheckinDate().toString(), item);
        }
        return result;
    }
}
