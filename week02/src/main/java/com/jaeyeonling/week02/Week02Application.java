package com.jaeyeonling.week02;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.Bean;

@SpringBootApplication
public class Week02Application {

    public static void main(String[] args) {
        SpringApplication.run(Week02Application.class, args);
    }

    @Bean
    public ChunkingStrategy chunkingStrategy() {
        // 기준선: Week 01과 동일한 ### 분할
        // TokenWindowChunker로 교체하여 비교 가능
        return new MarkdownHeaderChunker();
    }
}
