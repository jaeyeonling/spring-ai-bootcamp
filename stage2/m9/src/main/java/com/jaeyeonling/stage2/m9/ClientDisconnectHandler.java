package com.jaeyeonling.stage2.m9;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import reactor.core.Disposable;

/**
 * 클라이언트 연결 끊김을 감지하고 LLM 호출을 취소하는 핸들러.
 *
 * 클라이언트가 스트리밍 중 연결을 끊으면 불필요한 토큰 생성을
 * 중단하여 비용을 절약합니다.
 */
@Component
public class ClientDisconnectHandler {

    private static final Logger log = LoggerFactory.getLogger(ClientDisconnectHandler.class);

    /**
     * 활성 스트리밍 요청을 취소합니다.
     *
     * TODO: 구현하세요
     * - Disposable을 통해 진행 중인 Flux 구독을 취소합니다.
     * - 취소 시 로그를 남깁니다.
     * - 이미 완료된 요청은 무시합니다.
     *
     * @param subscription 취소할 Flux 구독
     */
    public void cancelStream(Disposable subscription) {
        throw new UnsupportedOperationException("구현하세요");
    }

    /**
     * 클라이언트 연결 상태를 확인합니다.
     *
     * TODO: 구현하세요
     * - 주어진 구독이 아직 활성 상태인지 확인합니다.
     *
     * @param subscription 확인할 구독
     * @return 연결이 활성 상태이면 true
     */
    public boolean isConnected(Disposable subscription) {
        throw new UnsupportedOperationException("구현하세요");
    }
}
