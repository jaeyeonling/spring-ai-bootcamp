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
        // TokenWindowChunker: 고정 크기 슬라이딩 윈도우 + 오버랩
        // chunkSize=100 단어, overlap=20 단어
        return new TokenWindowChunker(100, 20);
    }
}
