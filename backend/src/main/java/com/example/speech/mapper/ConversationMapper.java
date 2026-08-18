package com.example.speech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.example.speech.entity.Conversation;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

@Mapper
public interface ConversationMapper extends BaseMapper<Conversation> {

    /** 分页查询用户的会话列表 */
    @Select("SELECT * FROM conversation WHERE user_id = #{userId} " +
            "ORDER BY update_time DESC")
    IPage<Conversation> selectPageByUserId(Page<Conversation> page, @Param("userId") Long userId);
}
