package com.example.speech.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.example.speech.entity.Word;
import org.apache.ibatis.annotations.Mapper;

@Mapper
public interface WordMapper extends BaseMapper<Word> {
}
