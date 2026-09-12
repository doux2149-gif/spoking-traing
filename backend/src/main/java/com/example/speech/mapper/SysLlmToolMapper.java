package com.example.speech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.speech.entity.SysLlmTool;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/** 动态 LLM 工具 Mapper */
@Mapper
public interface SysLlmToolMapper extends BaseMapper<SysLlmTool> {

    /** 查询当前最大 version(用于感知变更)。无数据返回 0。 */
    @Select("SELECT COALESCE(MAX(version), 0) FROM sys_llm_tool")
    long selectMaxVersion();

    /** 按版本号自增(乐观写)。 */
    default int incrementVersionById(@Param("id") Long id, @Param("currentVersion") Long currentVersion) {
        return update(null,
                new com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper<SysLlmTool>()
                        .eq(SysLlmTool::getId, id)
                        .eq(SysLlmTool::getVersion, currentVersion)
                        .set(SysLlmTool::getVersion, currentVersion + 1));
    }
}
