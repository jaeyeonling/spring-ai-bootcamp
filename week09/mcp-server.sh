#!/bin/bash
# Week09 MCP 서버 실행 스크립트
# 변경 시 자동 재빌드 후 java -jar로 실행합니다.
# stdout은 JSON-RPC 전용 — 로그는 logback.xml에 의해 /tmp/week09-mcp-server.log로 출력됩니다.

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
ROOT_DIR="$(cd "$SCRIPT_DIR/.." && pwd)"
JAR="$SCRIPT_DIR/build/libs/week09-0.0.1-SNAPSHOT.jar"

# 소스 변경 시 재빌드 (jar가 없으면 빌드)
if [ ! -f "$JAR" ]; then
  "$ROOT_DIR/gradlew" -p "$ROOT_DIR" -q :week09:bootJar
fi

exec java -jar "$JAR"
