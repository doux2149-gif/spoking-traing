package com.example.speech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.speech.entity.ConversationMessage;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface ConversationMessageMapper extends BaseMapper<ConversationMessage> {
}
