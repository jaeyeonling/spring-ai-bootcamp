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
        // 실험 6: MarkdownHeaderChunker 복원 + 프롬프트 개선 (Few-shot + CoT)
        // 실험 4 대비 프롬프트만 변경하는 대조 실험
        return new MarkdownHeaderChunker();
    }
}
