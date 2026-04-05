"""
Naive RAG 벽 검증 스크립트
- 외부 패키지 불필요 (표준 라이브러리만)
- Layer 1/2/3 전체 문서를 청크 분할 후 TF-IDF 유사도 검색
- test_questions.json 각 질문에 대해 top-k 청크 검색
- retrieved context에 expected_answer 핵심 키워드가 있는지로 정답 판정
- tier별 정확도 리포트

실행:
  cd data
  python3 naive_rag_eval.py
  python3 naive_rag_eval.py --topk 10 --verbose
"""

import json
import math
import re
import glob
import argparse
from pathlib import Path
from collections import Counter, defaultdict

# ─── 설정 ─────────────────────────────────────────────────────────────────────

DATA_DIR = Path(__file__).parent
TOP_K = 5
MIN_KEYWORD_MATCH = 1  # expected_answer 키워드 중 몇 개 이상 매치되면 correct

# ─── 텍스트 처리 ────────────────────────────────────────────────────────────────

def tokenize(text: str) -> list[str]:
    """한국어/영어 혼합 텍스트 토크나이저 (표준 라이브러리)"""
    text = text.lower()
    # 순서 중요: 숫자+한국어 복합어 먼저 (14일, 200만원, 30분, 3천원)
    tokens = re.findall(r'[0-9,]+[가-힣]+|[가-힣]{2,}|[0-9]+%|[a-z]{2,}|[0-9]+', text)
    return tokens


def extract_keywords(text: str) -> list[str]:
    """핵심 키워드 추출 — 숫자+단위, 정책 용어 우선"""
    tokens = tokenize(text)
    # 수치 포함 토큰은 가중치 높음 (14일, 20000원, 3% 등)
    important = [t for t in tokens if re.search(r'\d', t)]
    return list(set(tokens)), list(set(important))


# ─── 문서 로딩 ──────────────────────────────────────────────────────────────────

def load_layer1(data_dir: Path) -> list[dict]:
    """Layer 1 FAQ: .md 파일을 Q&A 단위로 청크"""
    chunks = []
    for fpath in sorted((data_dir / "layer1_faq").glob("*.md")):
        text = fpath.read_text(encoding="utf-8")
        # ### 헤딩 단위로 Q&A 분리 (### What is..., ### How do I... 등)
        qa_blocks = re.split(r'\n(?=###\s)', text)
        for i, block in enumerate(qa_blocks):
            if len(block.strip()) < 20:
                continue
            chunks.append({
                "id": f"l1:{fpath.stem}:{i}",
                "layer": "faq",
                "source": fpath.name,
                "text": block.strip()
            })
    return chunks


def load_layer2(data_dir: Path) -> list[dict]:
    """Layer 2 정책 문서: current/deprecated/internal별로 청크"""
    chunks = []
    for subdir in ["current", "deprecated", "internal"]:
        subpath = data_dir / "layer2_policies" / subdir
        if not subpath.exists():
            continue
        for fpath in sorted(subpath.glob("*.md")):
            text = fpath.read_text(encoding="utf-8")
            # 섹션 단위 (## 헤더)로 분리
            sections = re.split(r'\n(?=#{1,3}\s)', text)
            for i, section in enumerate(sections):
                if len(section.strip()) < 30:
                    continue
                chunks.append({
                    "id": f"l2:{subdir}:{fpath.stem}:{i}",
                    "layer": f"policy_{subdir}",
                    "source": f"{subdir}/{fpath.name}",
                    "text": section.strip()
                })
    return chunks


def load_layer3(data_dir: Path) -> list[dict]:
    """Layer 3 상담 로그: JSONL → 대화를 하나의 청크로"""
    chunks = []
    for fpath in sorted((data_dir / "layer3_chatlogs").glob("*.jsonl")):
        with open(fpath, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                log = json.loads(line)
                # 대화 turns를 하나의 텍스트로 합치기
                turns_text = []
                for turn in log.get("turns", []):
                    speaker = turn.get("speaker", "")
                    msg = turn.get("message", "")
                    turns_text.append(f"{speaker}: {msg}")
                full_text = "\n".join(turns_text)

                chunks.append({
                    "id": f"l3:{log.get('conversation_id', '')}",
                    "layer": "chatlog",
                    "source": fpath.name,
                    "agent_id": log.get("agent_id", ""),
                    "agent_accuracy": log.get("agent_accuracy", ""),
                    "primary_intent": log.get("primary_intent", ""),
                    "text": full_text
                })
    return chunks


# ─── TF-IDF 인덱스 ──────────────────────────────────────────────────────────────

class TFIDFIndex:
    def __init__(self, chunks: list[dict]):
        self.chunks = chunks
        self.n_docs = len(chunks)
        self._build_index()

    def _build_index(self):
        # 각 문서의 토큰 빈도
        self.tf = []
        df = Counter()  # 문서 빈도
        for chunk in self.chunks:
            tokens = tokenize(chunk["text"])
            freq = Counter(tokens)
            self.tf.append(freq)
            for term in freq:
                df[term] += 1
        # IDF 계산
        self.idf = {}
        for term, count in df.items():
            self.idf[term] = math.log((self.n_docs + 1) / (count + 1)) + 1

    def score(self, query: str, top_k: int = 5) -> list[tuple[float, dict]]:
        q_tokens = tokenize(query)
        if not q_tokens:
            return []

        scores = []
        for i, chunk in enumerate(self.chunks):
            score = 0.0
            doc_len = sum(self.tf[i].values())
            for term in q_tokens:
                if term in self.tf[i]:
                    tf = self.tf[i][term] / max(doc_len, 1)
                    idf = self.idf.get(term, 0)
                    score += tf * idf
            if score > 0:
                scores.append((score, chunk))

        scores.sort(key=lambda x: -x[0])
        return scores[:top_k]


# ─── 정답 판정 ──────────────────────────────────────────────────────────────────

def judge_answer(retrieved_chunks: list[dict], question: dict) -> tuple[bool, str]:
    """
    정답 판정 (3가지 신호 종합):
    1. expected_answer 키워드가 retrieved context에 포함되는가
    2. expected source_layers 문서가 top-k에 포함되는가 (레이어 재현율)
    3. noise 질문에서 agent_jung이 top-k에 없는가 (오염 여부)
    """
    expected = question.get("expected_answer", "")
    all_tokens, key_tokens = extract_keywords(expected)
    source_layers = question.get("source_layers", [])
    wall_type = question.get("wall_type")

    context = " ".join(c["text"] for c in retrieved_chunks).lower()
    retrieved_layers = set(c["layer"] for c in retrieved_chunks)
    has_jung = any(c.get("agent_id") == "agent_jung" for c in retrieved_chunks)

    # ① 키워드 매칭 (임계값 낮춤: 1개만 맞아도 ok)
    if key_tokens:
        matched = sum(1 for t in key_tokens if t in context)
        keyword_hit = matched >= 1
        reason = f"수치키워드 {matched}/{len(key_tokens)}"
    elif all_tokens:
        matched = sum(1 for t in all_tokens if t in context)
        keyword_hit = matched >= max(1, int(len(all_tokens) * 0.2))
        reason = f"일반키워드 {matched}/{len(all_tokens)}"
    else:
        keyword_hit = False
        reason = "키워드 없음"

    # ② 레이어 재현율: source_layers에 맞는 문서가 검색됐는가
    layer_map = {"faq": "faq", "policy": "policy_current", "chatlog": "chatlog"}
    expected_layer_types = {layer_map[l] for l in source_layers if l in layer_map}
    layer_hit = bool(expected_layer_types & retrieved_layers)

    # ③ noise 오염 패널티: agent_jung 문서가 상위에 오면 감점
    jung_penalty = (wall_type in ("noise", None)) and has_jung

    # 종합 판정
    correct = (keyword_hit or layer_hit) and not jung_penalty
    signals = []
    if keyword_hit:
        signals.append(f"키워드({reason})")
    if layer_hit:
        signals.append(f"레이어({retrieved_layers & expected_layer_types})")
    if jung_penalty:
        signals.append("jung오염")
    if not signals:
        signals.append(reason)

    return correct, " | ".join(signals)


# ─── 메인 평가 ──────────────────────────────────────────────────────────────────

def run_evaluation(top_k: int = TOP_K, verbose: bool = False, layer_filter: str = "all"):
    print(f"\n{'='*60}")
    print(f"Naive RAG 벽 검증 (top_k={top_k}, layers={layer_filter})")
    print(f"{'='*60}\n")

    # 문서 로딩
    print("문서 로딩 중...")
    chunks = []
    if layer_filter in ("all", "faq"):
        l1 = load_layer1(DATA_DIR)
        chunks += l1
        print(f"  Layer 1 FAQ:    {len(l1)}개 청크")
    if layer_filter in ("all", "policy"):
        l2 = load_layer2(DATA_DIR)
        chunks += l2
        print(f"  Layer 2 정책:   {len(l2)}개 청크")
    if layer_filter in ("all", "chatlog"):
        l3 = load_layer3(DATA_DIR)
        chunks += l3
        print(f"  Layer 3 상담:   {len(l3)}개 청크")
    print(f"  총 청크:        {len(chunks)}개\n")

    # 인덱스 빌드
    print("TF-IDF 인덱스 빌드 중...")
    index = TFIDFIndex(chunks)
    print("완료.\n")

    # 테스트 질문 로딩
    with open(DATA_DIR / "test_questions.json", encoding="utf-8") as f:
        questions = json.load(f)
    print(f"테스트 질문: {len(questions)}개\n")

    # 평가 실행
    results = defaultdict(lambda: {"correct": 0, "total": 0, "questions": []})

    for q in questions:
        tier = q["tier"]
        query = q["question_ko"]

        retrieved = [c for _, c in index.score(query, top_k=top_k)]
        is_correct, reason = judge_answer(retrieved, q)

        results[tier]["total"] += 1
        if is_correct:
            results[tier]["correct"] += 1

        has_jung = any(c.get("agent_id") == "agent_jung" for c in retrieved)
        layer_map = {"faq": "faq", "policy": "policy_current", "chatlog": "chatlog"}
        expected_layers = {layer_map[l] for l in q.get("source_layers", []) if l in layer_map}
        retrieved_layers = {c["layer"] for c in retrieved}
        q_result = {
            "id": q["id"],
            "question": query[:50],
            "wall_type": q.get("wall_type"),
            "correct": is_correct,
            "reason": reason,
            "top_sources": [c["source"] for c in retrieved[:3]],
            "jung_contaminated": int(has_jung),
            "layer_hit": int(bool(expected_layers & retrieved_layers)),
        }
        results[tier]["questions"].append(q_result)

        if verbose:
            mark = "✓" if is_correct else "✗"
            wall = q.get("wall_type") or "-"
            print(f"  [{mark}] {q['id']} [{tier}] wall={wall}")
            print(f"       Q: {query[:60]}")
            print(f"       → {reason}")
            for c in retrieved[:2]:
                agent = f" (agent_jung!)" if c.get("agent_id") == "agent_jung" else ""
                print(f"       src: {c['source']}{agent}")
            print()

    # 결과 출력
    print(f"\n{'─'*60}")
    print(f"{'tier':<10} {'정답':<6} {'전체':<6} {'정확도':<8} {'벽 판정'}")
    print(f"{'─'*60}")

    tier_order = ["easy", "medium", "hard"]
    all_correct = 0
    all_total = 0
    for tier in tier_order:
        r = results[tier]
        if r["total"] == 0:
            continue
        acc = r["correct"] / r["total"] * 100
        all_correct += r["correct"]
        all_total += r["total"]

        if tier == "easy":
            verdict = "✓ 정상" if acc >= 60 else "⚠ 너무 낮음"
        elif tier == "medium":
            verdict = "✓ 벽 존재" if 20 <= acc <= 50 else ("⚠ 벽 너무 낮음" if acc > 50 else "⚠ 벽 너무 높음")
        else:  # hard
            verdict = "✓ 벽 충분" if acc <= 35 else "⚠ 벽 부족"

        print(f"  {tier:<8} {r['correct']:<6} {r['total']:<6} {acc:>5.1f}%   {verdict}")

    overall = all_correct / all_total * 100 if all_total else 0
    print(f"{'─'*60}")
    print(f"  {'전체':<8} {all_correct:<6} {all_total:<6} {overall:>5.1f}%")
    print(f"\n{'='*60}")

    # wall_type별 분석
    print("\nwall_type별 정확도:")
    wall_results = defaultdict(lambda: {"correct": 0, "total": 0})
    for tier in tier_order:
        for q in results[tier]["questions"]:
            wt = q["wall_type"] or "null"
            wall_results[wt]["total"] += 1
            if q["correct"]:
                wall_results[wt]["correct"] += 1

    for wt, r in sorted(wall_results.items()):
        acc = r["correct"] / r["total"] * 100
        bar = "█" * int(acc / 5)
        print(f"  {wt:<15} {acc:>5.1f}%  {bar}")

    print(f"\n{'='*60}\n")

    # agent_jung 오염율 분석
    jung_stats = defaultdict(lambda: {"total": 0, "jung_in_topk": 0})
    layer_recall = defaultdict(lambda: {"total": 0, "hit": 0})
    for tier in tier_order:
        for q in results[tier]["questions"]:
            jung_stats[tier]["total"] += 1
            jung_stats[tier]["jung_in_topk"] += q.get("jung_contaminated", 0)
            layer_recall[tier]["total"] += 1
            layer_recall[tier]["hit"] += q.get("layer_hit", 0)

    print("\nagent_jung 오염율 (top-k에 agent_jung 문서 포함):")
    for tier in tier_order:
        r = jung_stats[tier]
        if r["total"] == 0:
            continue
        rate = r["jung_in_topk"] / r["total"] * 100
        print(f"  {tier:<8} {r['jung_in_topk']}/{r['total']}  {rate:.1f}%")

    print("\n레이어 재현율 (올바른 소스 레이어가 top-k에 포함):")
    for tier in tier_order:
        r = layer_recall[tier]
        if r["total"] == 0:
            continue
        rate = r["hit"] / r["total"] * 100
        print(f"  {tier:<8} {r['hit']}/{r['total']}  {rate:.1f}%")
    print()

    # JSON 결과 저장
    output = {
        "config": {"top_k": top_k, "layer_filter": layer_filter, "total_chunks": len(chunks)},
        "summary": {
            tier: {
                "correct": results[tier]["correct"],
                "total": results[tier]["total"],
                "accuracy": round(results[tier]["correct"] / results[tier]["total"] * 100, 1) if results[tier]["total"] else 0
            }
            for tier in tier_order if results[tier]["total"] > 0
        },
        "wall_type": {
            wt: {
                "correct": r["correct"],
                "total": r["total"],
                "accuracy": round(r["correct"] / r["total"] * 100, 1)
            }
            for wt, r in wall_results.items()
        }
    }
    out_path = DATA_DIR / "naive_rag_result.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"결과 저장: {out_path}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--topk", type=int, default=TOP_K)
    parser.add_argument("--verbose", "-v", action="store_true")
    parser.add_argument("--layers", default="all", choices=["all", "faq", "policy", "chatlog"])
    args = parser.parse_args()
    run_evaluation(top_k=args.topk, verbose=args.verbose, layer_filter=args.layers)
