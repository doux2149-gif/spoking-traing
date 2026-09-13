package com.example.speech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.speech.entity.SysUserOnline;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Update;

@Mapper
public interface SysUserOnlineMapper extends BaseMapper<SysUserOnline> {

    /** 节流更新最后活跃时间(仅当上一次更新早于指定时间时才写库) */
    @Update("UPDATE sys_user_online SET last_active_time = #{now} WHERE token_id = #{tokenId} AND last_active_time < #{threshold}")
    int touchActiveTime(@Param("tokenId") String tokenId,
                        @Param("now") java.time.LocalDateTime now,
                        @Param("threshold") java.time.LocalDateTime threshold);
}
