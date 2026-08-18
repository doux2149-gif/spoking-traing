package com.example.speech;

import com.example.speech.config.DeepSeekProperties;
import com.example.speech.config.IflytekIatProperties;
import com.example.speech.config.IflytekTtsProperties;
import org.mybatis.spring.annotation.MapperScan;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableAsync;

@SpringBootApplication
@MapperScan("com.example.speech.mapper")
@EnableAsync
@EnableConfigurationProperties({IflytekIatProperties.class, IflytekTtsProperties.class, DeepSeekProperties.class})
public class SpeechApplication {

    public static void main(String[] args) {
        SpringApplication.run(SpeechApplication.class, args);
    }
}
