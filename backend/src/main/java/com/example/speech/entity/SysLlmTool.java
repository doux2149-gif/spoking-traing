package com.example.speech.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableField;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import java.time.LocalDateTime;
import lombok.Data;

/**
 * 动态 LLM 工具。
 * <p>由管理端 CRUD 维护, {@link com.example.speech.service.tools.ToolRegistry}
 * 按 version 变化感知后即时生效。</p>
 */
@Data
@TableName("sys_llm_tool")
public class SysLlmTool {
    @TableId(type = IdType.AUTO)
    private Long id;
    private String toolName;
    private String description;
    private String parametersSchema;
    /** HTTP / SCRIPT */
    private String toolType;
    private String httpEndpoint;
    private String httpMethod;
    /** JSON 对象字符串 */
    private String httpHeaders;
    private Integer timeoutMs;
    private Integer enabled;
    private Long version;
    private String remark;
    @TableField("create_time")
    private LocalDateTime createTime;
    @TableField("update_time")
    private LocalDateTime updateTime;
}
