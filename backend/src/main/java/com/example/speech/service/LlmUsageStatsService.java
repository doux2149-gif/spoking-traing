package com.example.speech.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.example.speech.entity.LlmModelPrice;
import com.example.speech.mapper.LlmModelPriceMapper;
import com.example.speech.mapper.LlmUsageLogMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * LLM 用量统计与模型单价管理。成本单价为当前配置价(改价后历史成本按新价重算)。
 */
@Service
@RequiredArgsConstructor
public class LlmUsageStatsService {

    private final LlmUsageLogMapper usageLogMapper;
    private final LlmModelPriceMapper priceMapper;

    public Map<String, Object> summary(LocalDate start, LocalDate end) {
        Map<String, Object> row = usageLogMapper.selectSummary(atStart(start), atEnd(end));
        return row == null ? emptySummary() : normalizeRow(row);
    }

    /** 按天趋势, 无数据的日期补零, 保证图表连续 */
    public List<Map<String, Object>> daily(LocalDate start, LocalDate end) {
        LocalDate s = defaultStart(start);
        LocalDate e = defaultEnd(end);
        List<Map<String, Object>> rows = usageLogMapper.selectDaily(s.atStartOfDay(), e.atTime(LocalTime.MAX));
        Map<String, Map<String, Object>> indexed = new HashMap<>();
        for (Map<String, Object> row : rows) {
            indexed.put(dateKey(row.get("date")), normalizeRow(row));
        }
        List<Map<String, Object>> result = new ArrayList<>();
        for (LocalDate d = s; !d.isAfter(e); d = d.plusDays(1)) {
            String key = d.toString();
            if (indexed.containsKey(key)) {
                Map<String, Object> row = indexed.get(key);
                row.put("date", key);
                result.add(row);
            } else {
                Map<String, Object> zero = emptySummary();
                zero.put("date", key);
                result.add(zero);
            }
        }
        return result;
    }

    public List<Map<String, Object>> byModel(LocalDate start, LocalDate end) {
        return usageLogMapper.selectByModel(atStart(start), atEnd(end));
    }

    public List<Map<String, Object>> byOperation(LocalDate start, LocalDate end) {
        return usageLogMapper.selectByOperation(atStart(start), atEnd(end));
    }

    public List<Map<String, Object>> topUsers(LocalDate start, LocalDate end) {
        return usageLogMapper.selectTopUsers(atStart(start), atEnd(end));
    }

    public List<Map<String, Object>> exportDetail(LocalDate start, LocalDate end) {
        return usageLogMapper.selectDetailForExport(atStart(start), atEnd(end));
    }

    // ---------------- 单价配置 ----------------

    public List<LlmModelPrice> listPrices() {
        return priceMapper.selectList(
                new LambdaQueryWrapper<LlmModelPrice>().orderByAsc(LlmModelPrice::getId));
    }

    /** 批量保存: 有 id 更新; 无 id 且模型名不重复则新增 */
    @Transactional
    public void savePrices(List<LlmModelPrice> prices) {
        if (prices == null) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        for (LlmModelPrice item : prices) {
            validatePrice(item);
            if (item.getId() != null) {
                LlmModelPrice db = priceMapper.selectById(item.getId());
                if (db == null) {
                    throw new IllegalArgumentException("单价配置不存在: id=" + item.getId());
                }
                db.setInputPrice(item.getInputPrice());
                db.setOutputPrice(item.getOutputPrice());
                if (item.getEnabled() != null) {
                    db.setEnabled(item.getEnabled());
                }
                db.setRemark(item.getRemark());
                db.setUpdateTime(now);
                priceMapper.updateById(db);
            } else if (StringUtils.hasText(item.getModel())) {
                Long exists = priceMapper.selectCount(
                        new LambdaQueryWrapper<LlmModelPrice>().eq(LlmModelPrice::getModel, item.getModel()));
                if (exists > 0) {
                    throw new IllegalArgumentException("模型单价已存在: " + item.getModel());
                }
                LlmModelPrice row = new LlmModelPrice();
                row.setModel(item.getModel().trim());
                row.setInputPrice(item.getInputPrice());
                row.setOutputPrice(item.getOutputPrice());
                row.setEnabled(item.getEnabled() == null ? 1 : item.getEnabled());
                row.setRemark(item.getRemark());
                row.setCreateTime(now);
                row.setUpdateTime(now);
                priceMapper.insert(row);
            }
        }
    }

    private void validatePrice(LlmModelPrice item) {
        if (item == null || item.getInputPrice() == null || item.getOutputPrice() == null) {
            throw new IllegalArgumentException("单价不能为空");
        }
        if (item.getInputPrice().signum() < 0 || item.getOutputPrice().signum() < 0) {
            throw new IllegalArgumentException("单价不能为负数");
        }
        if (item.getInputPrice().scale() > 6 || item.getOutputPrice().scale() > 6) {
            throw new IllegalArgumentException("单价最多保留 6 位小数");
        }
    }

    // ---------------- 工具 ----------------

    private LocalDateTime atStart(LocalDate start) {
        return defaultStart(start).atStartOfDay();
    }

    private LocalDateTime atEnd(LocalDate end) {
        return defaultEnd(end).atTime(LocalTime.MAX);
    }

    private LocalDate defaultStart(LocalDate start) {
        return start != null ? start : LocalDate.now().minusDays(6);
    }

    private LocalDate defaultEnd(LocalDate end) {
        return end != null ? end : LocalDate.now();
    }

    private Map<String, Object> emptySummary() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("calls", 0L);
        map.put("promptTokens", 0L);
        map.put("completionTokens", 0L);
        map.put("totalTokens", 0L);
        map.put("cost", BigDecimal.ZERO);
        return map;
    }

    /** 聚合 Map 数值类型归一, 方便前端直接消费 */
    private Map<String, Object> normalizeRow(Map<String, Object> row) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("calls", toLong(row.get("calls")));
        map.put("promptTokens", toLong(row.get("promptTokens")));
        map.put("completionTokens", toLong(row.get("completionTokens")));
        map.put("totalTokens", toLong(row.get("totalTokens")));
        Object cost = row.get("cost");
        map.put("cost", cost instanceof BigDecimal ? cost : new BigDecimal(cost == null ? "0" : cost.toString()));
        return map;
    }

    private Long toLong(Object value) {
        if (value == null) {
            return 0L;
        }
        if (value instanceof Number) {
            return ((Number) value).longValue();
        }
        return Long.parseLong(value.toString());
    }

    /** MySQL DATE() 在 Map 中可能是 LocalDate/java.sql.Date/String, 统一取前 10 位 */
    private String dateKey(Object date) {
        if (date == null) {
            return "";
        }
        String text = date.toString();
        return text.length() >= 10 ? text.substring(0, 10) : text;
    }
}
