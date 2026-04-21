package com.jaeyeonling.stage2.m10;

import java.util.List;

/**
 * LLM 구조화 응답 스키마.
 *
 * Spring AI의 BeanOutputConverter가 이 레코드의 필드를 기반으로
 * JSON 스키마를 자동 생성하여 프롬프트에 주입합니다.
 */
public record ChatAnswer(
    String answer,              // 답변 텍스트
    String category,            // 분류: orders / shipping / returns / payment / general
    double confidence,          // 신뢰도: 0.0 ~ 1.0
    List<String> sourceFiles,   // 참조한 문서 목록
    boolean requiresHumanAgent  // 상담사 연결 필요 여부
) {}
