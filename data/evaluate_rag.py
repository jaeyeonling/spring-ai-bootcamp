"""
Naive RAG 벽 검증 — 실제 OpenAI embeddings + Qdrant 파이프라인

실행:
  cd data
  .venv/bin/python evaluate_rag.py          # 전체 실행
  .venv/bin/python evaluate_rag.py --skip-ingest  # 인제스트 건너뛰기 (재실행 시)
  .venv/bin/python evaluate_rag.py --verbose      # 질문별 상세 출력

요구사항:
  .venv/bin/pip install openai qdrant-client
  Qdrant: docker run -d -p 6333:6333 -p 6334:6334 qdrant/qdrant
  .env 파일에 OPENAI_API_KEY=sk-...
"""

import json
import os
import re
import glob
import time
import argparse
from pathlib import Path
from dotenv import load_dotenv, dotenv_values

# ─── 설정 ─────────────────────────────────────────────────────────────────────

DATA_DIR = Path(__file__).parent
ROOT_DIR = DATA_DIR.parent

QDRANT_URL = "http://localhost:6333"
COLLECTION = "naive-rag-eval"
EMBED_MODEL = "text-embedding-3-small"
CHAT_MODEL = "gpt-4o-mini"
TOP_K = 5
CHUNK_SIZE = 500  # 글자 수 기준 청킹

# .env 파일에서 API 키 로드
env_path = ROOT_DIR / ".env"
env_vars = dotenv_values(env_path)
OPENAI_API_KEY = env_vars.get("OPENAI_API_KEY") or os.environ.get("OPENAI_API_KEY")

# ─── 클라이언트 초기화 ───────────────────────────────────────────────────────

from openai import OpenAI
from qdrant_client import QdrantClient
from qdrant_client.models import Distance, VectorParams, PointStruct

openai_client = OpenAI(api_key=OPENAI_API_KEY)
qdrant = QdrantClient(url=QDRANT_URL)


# ─── 청킹 ────────────────────────────────────────────────────────────────────

def chunk_text(text: str, source: str, layer: str, extra_meta: dict = None) -> list[dict]:
    """텍스트를 CHUNK_SIZE 글자 단위로 분할, 앞뒤 50자 오버랩"""
    chunks = []
    step = CHUNK_SIZE - 50
    for i in range(0, len(text), step):
        chunk = text[i:i + CHUNK_SIZE].strip()
        if len(chunk) < 50:
            continue
        meta = {"source": source, "layer": layer}
        if extra_meta:
            meta.update(extra_meta)
        chunks.append({"text": chunk, "meta": meta})
    return chunks


# ─── 문서 로딩 ───────────────────────────────────────────────────────────────

def load_all_documents() -> list[dict]:
    docs = []

    # Layer 1: FAQ (영어 markdown)
    for fpath in sorted((DATA_DIR / "layer1_faq").glob("*.md")):
        text = fpath.read_text(encoding="utf-8")
        docs.extend(chunk_text(text, fpath.name, "faq"))

    # Layer 2: 정책 문서 (current/deprecated/internal)
    for subdir in ["current", "deprecated", "internal"]:
        subpath = DATA_DIR / "layer2_policies" / subdir
        if not subpath.exists():
            continue
        for fpath in sorted(subpath.glob("*.md")):
            text = fpath.read_text(encoding="utf-8")
            docs.extend(chunk_text(text, f"{subdir}/{fpath.name}", f"policy_{subdir}"))

    # Layer 3: 상담 로그 (JSONL)
    for fpath in sorted((DATA_DIR / "layer3_chatlogs").glob("*.jsonl")):
        with open(fpath, encoding="utf-8") as f:
            for line in f:
                line = line.strip()
                if not line:
                    continue
                log = json.loads(line)
                turns = [f"{t.get('role', t.get('speaker', ''))}: {t.get('text', t.get('message', ''))}" for t in log.get("turns", [])]
                text = "\n".join(turns)
                extra = {
                    "agent_id": log.get("agent_id", ""),
                    "agent_accuracy": log.get("agent_accuracy", ""),
                    "primary_intent": log.get("primary_intent", ""),
                }
                docs.extend(chunk_text(text, fpath.name, "chatlog", extra))

    return docs


# ─── 임베딩 & 인제스트 ────────────────────────────────────────────────────────

def embed_batch(texts: list[str]) -> list[list[float]]:
    """OpenAI embeddings API — 배치 처리"""
    resp = openai_client.embeddings.create(model=EMBED_MODEL, input=texts)
    return [r.embedding for r in resp.data]


def ingest(docs: list[dict], batch_size: int = 100):
    """Qdrant에 문서 임베딩 후 저장"""
    # 컬렉션 생성 (이미 존재하면 삭제 후 재생성)
    try:
        qdrant.delete_collection(COLLECTION)
    except Exception:
        pass
    qdrant.create_collection(
        COLLECTION,
        vectors_config=VectorParams(size=1536, distance=Distance.COSINE)
    )

    print(f"인제스트 시작: {len(docs)}개 청크")
    points = []
    for i in range(0, len(docs), batch_size):
        batch = docs[i:i + batch_size]
        texts = [d["text"] for d in batch]
        vectors = embed_batch(texts)
        for j, (doc, vec) in enumerate(zip(batch, vectors)):
            points.append(PointStruct(
                id=i + j,
                vector=vec,
                payload={"text": doc["text"], **doc["meta"]}
            ))
        print(f"  {min(i + batch_size, len(docs))}/{len(docs)} 임베딩 완료")
        time.sleep(0.5)  # rate limit 방지

    qdrant.upsert(collection_name=COLLECTION, points=points)
    print(f"인제스트 완료: {len(points)}개 청크 저장됨\n")


# ─── 검색 & 생성 ─────────────────────────────────────────────────────────────

def retrieve(question: str, top_k: int = TOP_K) -> list[dict]:
    """질문을 임베딩 → Qdrant 검색 → top-k 청크 반환"""
    q_vec = openai_client.embeddings.create(model=EMBED_MODEL, input=[question]).data[0].embedding
    results = qdrant.query_points(
        collection_name=COLLECTION,
        query=q_vec,
        limit=top_k,
        with_payload=True
    )
    return [r.payload for r in results.points]


def generate_answer(question: str, context_chunks: list[dict]) -> str:
    """RAG 답변 생성"""
    context = "\n\n---\n\n".join(c["text"] for c in context_chunks)
    system = (
        "You are a customer support assistant for Cholog Corporation.\n"
        "Answer the question based ONLY on the provided context.\n"
        "If the answer is not in the context, say 'I cannot find this information.'\n\n"
        f"Context:\n{context}"
    )
    resp = openai_client.chat.completions.create(
        model=CHAT_MODEL,
        messages=[
            {"role": "system", "content": system},
            {"role": "user", "content": question},
        ],
        temperature=0.0,
        max_tokens=300,
    )
    return resp.choices[0].message.content.strip()


def judge(question: str, answer: str, expected: str) -> bool:
    """LLM 심판: answer가 expected의 핵심 정보를 포함하는지 판단"""
    prompt = f"""Does the answer contain the key information from the expected answer?

Question: {question}
Expected answer: {expected}
Actual answer: {answer}

Reply with exactly one word: YES or NO"""

    resp = openai_client.chat.completions.create(
        model=CHAT_MODEL,
        messages=[{"role": "user", "content": prompt}],
        temperature=0.0,
        max_tokens=5,
    )
    verdict = resp.choices[0].message.content.strip().upper()
    return "YES" in verdict


# ─── 평가 실행 ────────────────────────────────────────────────────────────────

def run_evaluation(skip_ingest: bool = False, verbose: bool = False):
    print(f"\n{'='*60}")
    print("Naive RAG 벽 검증 — OpenAI + Qdrant 파이프라인")
    print(f"{'='*60}\n")

    if not OPENAI_API_KEY:
        print("ERROR: OPENAI_API_KEY가 설정되지 않았습니다. .env 파일을 확인하세요.")
        return

    # 인제스트
    if not skip_ingest:
        docs = load_all_documents()
        print(f"문서 로딩: {len(docs)}개 청크")
        layers = {}
        for d in docs:
            l = d["meta"]["layer"]
            layers[l] = layers.get(l, 0) + 1
        for l, c in sorted(layers.items()):
            print(f"  {l}: {c}개")
        print()
        ingest(docs)
    else:
        info = qdrant.get_collection(COLLECTION)
        print(f"인제스트 건너뜀. 기존 컬렉션: {info.points_count}개 포인트\n")

    # 테스트 질문 로드
    with open(DATA_DIR / "test_questions.json", encoding="utf-8") as f:
        questions = json.load(f)
    print(f"테스트 질문: {len(questions)}개\n")

    # 평가
    results = {"easy": [], "medium": [], "hard": []}
    total_cost_tokens = 0

    for i, q in enumerate(questions):
        tier = q["tier"]
        question_ko = q["question_ko"]
        expected = q["expected_answer"]
        wall_type = q.get("wall_type") or "-"

        # 검색
        chunks = retrieve(question_ko)

        # 생성
        answer = generate_answer(question_ko, chunks)

        # 판정
        correct = judge(question_ko, answer, expected)
        total_cost_tokens += 1  # 근사치

        # 노이즈 오염 여부
        jung_in_topk = any(c.get("agent_id") == "agent_jung" for c in chunks)

        results[tier].append({
            "id": q["id"],
            "correct": correct,
            "wall_type": wall_type,
            "jung_contaminated": jung_in_topk,
            "answer_snippet": answer[:80],
        })

        mark = "✓" if correct else "✗"
        if verbose or (i % 10 == 0):
            jung = " [jung!]" if jung_in_topk else ""
            print(f"[{mark}] {q['id']} [{tier}] wall={wall_type}{jung}")
            if verbose:
                print(f"     Q: {question_ko[:60]}")
                print(f"     A: {answer[:80]}")
            print()

        time.sleep(0.2)  # rate limit

    # 결과 집계
    print(f"\n{'─'*60}")
    print(f"{'tier':<10} {'정답':<6} {'전체':<6} {'정확도':<8} {'벽 판정'}")
    print(f"{'─'*60}")

    tier_order = ["easy", "medium", "hard"]
    all_correct = all_total = 0
    tier_accuracies = {}
    for tier in tier_order:
        r = results[tier]
        if not r:
            continue
        n_correct = sum(1 for x in r if x["correct"])
        n_total = len(r)
        acc = n_correct / n_total * 100
        all_correct += n_correct
        all_total += n_total
        tier_accuracies[tier] = acc

        if tier == "easy":
            verdict = "✓ 정상" if acc >= 70 else f"⚠ 낮음 (목표 70%+)"
        elif tier == "medium":
            verdict = "✓ 벽 존재" if 25 <= acc <= 40 else ("⚠ 벽 약함" if acc > 40 else "⚠ 벽 너무 높음")
        else:
            verdict = "✓ 벽 충분" if acc <= 35 else f"⚠ 벽 부족 (목표 ≤35%)"

        print(f"  {tier:<8} {n_correct:<6} {n_total:<6} {acc:>5.1f}%   {verdict}")

    overall = all_correct / all_total * 100
    print(f"{'─'*60}")
    print(f"  {'전체':<8} {all_correct:<6} {all_total:<6} {overall:>5.1f}%")
    print(f"\n{'='*60}")

    # agent_jung 오염율
    print("\nagent_jung 오염율 (top-k에 agent_jung 문서 포함):")
    for tier in tier_order:
        r = results[tier]
        if not r:
            continue
        jung_count = sum(1 for x in r if x["jung_contaminated"])
        print(f"  {tier:<8} {jung_count}/{len(r)}  {jung_count/len(r)*100:.1f}%")

    # wall_type별 분석
    from collections import Counter, defaultdict
    wall_stats = defaultdict(lambda: {"correct": 0, "total": 0})
    for tier in tier_order:
        for item in results[tier]:
            wt = item["wall_type"]
            wall_stats[wt]["total"] += 1
            if item["correct"]:
                wall_stats[wt]["correct"] += 1
    print("\nwall_type별 정확도:")
    for wt, s in sorted(wall_stats.items()):
        acc = s["correct"] / s["total"] * 100
        bar = "█" * int(acc / 5)
        print(f"  {wt:<15} {acc:>5.1f}%  {bar}")

    # 결과 저장
    output = {
        "pipeline": {"embed_model": EMBED_MODEL, "chat_model": CHAT_MODEL, "top_k": TOP_K},
        "summary": {
            tier: {
                "correct": sum(1 for x in results[tier] if x["correct"]),
                "total": len(results[tier]),
                "accuracy": round(tier_accuracies.get(tier, 0), 1)
            }
            for tier in tier_order if results[tier]
        },
        "wall_verdict": {
            "easy_ok": tier_accuracies.get("easy", 0) >= 70,
            "medium_ok": 25 <= tier_accuracies.get("medium", 0) <= 40,
            "hard_ok": tier_accuracies.get("hard", 0) <= 35,
        },
        "details": results,
    }
    out_path = DATA_DIR / "rag_eval_result.json"
    with open(out_path, "w", encoding="utf-8") as f:
        json.dump(output, f, ensure_ascii=False, indent=2)
    print(f"\n결과 저장: {out_path}")

    # 벽 검증 최종 판정
    v = output["wall_verdict"]
    all_ok = all(v.values())
    print(f"\n{'='*60}")
    print(f"벽 검증 최종 판정: {'✓ PASS — main 머지 가능' if all_ok else '✗ FAIL — 데이터 조정 필요'}")
    for k, ok in v.items():
        print(f"  {k}: {'✓' if ok else '✗'}")
    print(f"{'='*60}\n")


if __name__ == "__main__":
    parser = argparse.ArgumentParser()
    parser.add_argument("--skip-ingest", action="store_true")
    parser.add_argument("--verbose", "-v", action="store_true")
    args = parser.parse_args()
    run_evaluation(skip_ingest=args.skip_ingest, verbose=args.verbose)
