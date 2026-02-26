# 힌트 01: 청킹 전략 — 분할 방식이 왜 중요한가

## 단순 분할의 문제

1주차에서는 FAQ를 `###`으로 분할했습니다 — 질문당 하나의 청크. 작동하긴 하지만:

```
### 반품 배송비는 얼마인가요?
환불 금액에서 배송비 2,500원이 공제됩니다.
```

이 청크는 **반품**에 관한 것임을 말해주지 않습니다. "반품 정책이 어떻게 되나요?"라는
질문이 오면 이 청크가 높은 순위를 받지 못할 수 있습니다. "반품"이라는 단어가 한 번밖에
나오지 않고, 반품 섹션에 대한 주변 컨텍스트가 없기 때문입니다.

컨텍스트를 유지하는 청크와 비교해보세요:

```
## 반품 및 교환

### 반품 배송비는 얼마인가요?
환불 금액에서 배송비 2,500원이 공제됩니다.
```

이제 임베딩이 이것이 반품에 관한 내용임을 파악합니다.

## 전략 1: 마크다운 헤더 분할 (기준선)

1주차에 사용한 방식입니다:

```java
Arrays.stream(content.split("(?=### )"))
    .map(String::trim)
    .filter(s -> !s.isBlank())
    .toList();
```

단순하지만 청크가 상위 섹션 컨텍스트를 잃습니다.

## 전략 2: 오버랩이 있는 토큰 창 분할

문서 구조 대신 **토큰 수**에 **오버랩**을 두어 분할합니다.

아이디어: 연속된 창들이 경계에서 일부 토큰을 공유하도록 고정 크기 창으로 문서를 자릅니다.
이렇게 하면 경계에서 정보가 사라지지 않습니다.

```
문서: [A B C D E F G H I J K L M N O]

청크 크기: 5, 오버랩: 2

청크 1: [A B C D E]
청크 2: [D E F G H]      ← D, E를 청크 1과 공유
청크 3: [G H I J K]      ← G, H를 청크 2와 공유
청크 4: [J K L M N]
청크 5: [M N O]
```

### Spring AI의 TokenTextSplitter

Spring AI는 이 기능을 제공하는 `TokenTextSplitter`를 포함합니다:

```java
import org.springframework.ai.transformer.splitter.TokenTextSplitter;

TokenTextSplitter splitter = new TokenTextSplitter(
    300,    // defaultChunkSize — 청크당 목표 토큰 수
    50,     // minChunkSizeChars — 청크를 구성하는 최소 문자 수
    200,    // minChunkLengthToEmbed — 이보다 짧은 청크는 건너뜀
    100,    // maxNumChunks — 최대 청크 수
    true    // keepSeparator
);

List<Document> documents = List.of(new Document(content));
List<Document> chunks = splitter.apply(documents);
```

### 청크 크기와 오버랩이 중요한 이유

| 청크 크기 | 효과 |
|-----------|--------|
| 너무 작음 (50 토큰) | 청크가 컨텍스트를 잃음. "2,500원"은 "반품 배송비" 없이는 의미가 없음 |
| 너무 큼 (1000 토큰) | 청크가 너무 광범위함. 검색이 초점 없는 컨텍스트를 반환 |
| 적절 (200-500 토큰) | 청크당 충분한 컨텍스트, 좋은 검색을 위해 충분히 구체적 |

| 오버랩 | 효과 |
|---------|--------|
| 0 (오버랩 없음) | 청크 경계의 정보가 분리되어 손실 |
| 청크 크기의 20-30% | 좋은 균형 — 경계 컨텍스트 유지 |
| 50% 이상 | 과도한 중복, 더 많은 임베딩 계산 및 저장 필요 |

### 실험

다양한 설정으로 평가를 실행해보세요:

```
ChunkSize=100, Overlap=20  → 정확도: ??%
ChunkSize=300, Overlap=50  → 정확도: ??%
ChunkSize=500, Overlap=100 → 정확도: ??%
```

정답은 하나가 아닙니다 — 문서 구조에 따라 다릅니다.
중요한 것은 차이를 **측정**하는 것이지, 추측이 아닙니다.

## Strategy 패턴 구현

`ChunkingStrategy` 인터페이스를 사용하면 나머지 코드를 변경하지 않고 전략을 교체할 수 있습니다:

```java
public interface ChunkingStrategy {
    List<String> split(String content);
}
```

`ChatService`는 `ChunkingStrategy`를 받아 주입된 구현체를 사용합니다.
이렇게 하면 A/B 테스트가 쉬워집니다 — 어떤 구현체를 활성화할지만 바꾸면 됩니다.
