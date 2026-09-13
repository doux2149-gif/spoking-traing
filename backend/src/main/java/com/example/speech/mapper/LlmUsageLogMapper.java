package com.example.speech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.speech.entity.LlmUsageLog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@Mapper
public interface LlmUsageLogMapper extends BaseMapper<LlmUsageLog> {

    // 成本(元) = (输入token*输入单价 + 输出token*输出单价) / 100万; 无单价模型成本为 0
    String COST_EXPR =
            "(IFNULL(l.prompt_tokens,0)*IFNULL(p.input_price,0)" +
            "+IFNULL(l.completion_tokens,0)*IFNULL(p.output_price,0))/1000000";
    String JOIN_PRICE =
            " FROM llm_usage_log l LEFT JOIN llm_model_price p ON l.model = p.model ";
    String RANGE = " l.created_at >= #{start} AND l.created_at <= #{end}";

    /** 区间汇总 */
    @Select("SELECT COUNT(*) AS calls, " +
            "IFNULL(SUM(l.prompt_tokens),0) AS promptTokens, " +
            "IFNULL(SUM(l.completion_tokens),0) AS completionTokens, " +
            "IFNULL(SUM(l.total_tokens),0) AS totalTokens, " +
            "IFNULL(SUM(" + COST_EXPR + "),0) AS cost " +
            JOIN_PRICE + " WHERE" + RANGE)
    Map<String, Object> selectSummary(@Param("start") LocalDateTime start,
                                      @Param("end") LocalDateTime end);

    /** 按天聚合 */
    @Select("SELECT DATE(l.created_at) AS date, COUNT(*) AS calls, " +
            "IFNULL(SUM(l.prompt_tokens),0) AS promptTokens, " +
            "IFNULL(SUM(l.completion_tokens),0) AS completionTokens, " +
            "IFNULL(SUM(l.total_tokens),0) AS totalTokens, " +
            "IFNULL(SUM(" + COST_EXPR + "),0) AS cost " +
            JOIN_PRICE + " WHERE" + RANGE +
            " GROUP BY DATE(l.created_at) ORDER BY date")
    List<Map<String, Object>> selectDaily(@Param("start") LocalDateTime start,
                                          @Param("end") LocalDateTime end);

    /** 按模型聚合 */
    @Select("SELECT IFNULL(l.model,'unknown') AS model, COUNT(*) AS calls, " +
            "IFNULL(SUM(l.prompt_tokens),0) AS promptTokens, " +
            "IFNULL(SUM(l.completion_tokens),0) AS completionTokens, " +
            "IFNULL(SUM(l.total_tokens),0) AS totalTokens, " +
            "IFNULL(SUM(" + COST_EXPR + "),0) AS cost " +
            JOIN_PRICE + " WHERE" + RANGE +
            " GROUP BY l.model ORDER BY totalTokens DESC")
    List<Map<String, Object>> selectByModel(@Param("start") LocalDateTime start,
                                            @Param("end") LocalDateTime end);

    /** 按操作类型聚合(chat/translate/summary) */
    @Select("SELECT IFNULL(l.operation,'unknown') AS operation, COUNT(*) AS calls, " +
            "IFNULL(SUM(l.prompt_tokens),0) AS promptTokens, " +
            "IFNULL(SUM(l.completion_tokens),0) AS completionTokens, " +
            "IFNULL(SUM(l.total_tokens),0) AS totalTokens, " +
            "IFNULL(SUM(" + COST_EXPR + "),0) AS cost " +
            JOIN_PRICE + " WHERE" + RANGE +
            " GROUP BY l.operation ORDER BY totalTokens DESC")
    List<Map<String, Object>> selectByOperation(@Param("start") LocalDateTime start,
                                                @Param("end") LocalDateTime end);

    /** 用量 Top10 用户 */
    @Select("SELECT l.user_id AS userId, " +
            "IFNULL(u.username, CONCAT('用户#', l.user_id)) AS username, " +
            "COUNT(*) AS calls, " +
            "IFNULL(SUM(l.prompt_tokens),0) AS promptTokens, " +
            "IFNULL(SUM(l.completion_tokens),0) AS completionTokens, " +
            "IFNULL(SUM(l.total_tokens),0) AS totalTokens, " +
            "IFNULL(SUM(" + COST_EXPR + "),0) AS cost " +
            "FROM llm_usage_log l " +
            "LEFT JOIN llm_model_price p ON l.model = p.model " +
            "LEFT JOIN sys_user u ON u.id = l.user_id " +
            "WHERE l.created_at >= #{start} AND l.created_at <= #{end} " +
            "GROUP BY l.user_id, u.username ORDER BY totalTokens DESC LIMIT 10")
    List<Map<String, Object>> selectTopUsers(@Param("start") LocalDateTime start,
                                             @Param("end") LocalDateTime end);

    /** 导出明细(含成本与用户名) */
    @Select("SELECT l.created_at AS createdAt, l.user_id AS userId, " +
            "IFNULL(u.username,'') AS username, IFNULL(l.model,'') AS model, " +
            "IFNULL(l.operation,'') AS operation, " +
            "IFNULL(l.prompt_tokens,0) AS promptTokens, " +
            "IFNULL(l.completion_tokens,0) AS completionTokens, " +
            "IFNULL(l.total_tokens,0) AS totalTokens, " +
            COST_EXPR + " AS cost " +
            "FROM llm_usage_log l " +
            "LEFT JOIN llm_model_price p ON l.model = p.model " +
            "LEFT JOIN sys_user u ON u.id = l.user_id " +
            "WHERE l.created_at >= #{start} AND l.created_at <= #{end} " +
            "ORDER BY l.created_at DESC")
    List<Map<String, Object>> selectDetailForExport(@Param("start") LocalDateTime start,
                                                    @Param("end") LocalDateTime end);
}
