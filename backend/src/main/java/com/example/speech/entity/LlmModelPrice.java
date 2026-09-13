package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDateTime;

/**
 * LLM 模型单价配置, 单价单位: 元/百万 token。
 */
@Data
@TableName("llm_model_price")
public class LlmModelPrice {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String model;
    @TableField("input_price")
    private BigDecimal inputPrice;
    @TableField("output_price")
    private BigDecimal outputPrice;
    private Integer enabled;
    private String remark;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
