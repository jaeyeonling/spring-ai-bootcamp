"""
초록 코퍼레이션 데이터 확장 스크립트
- Layer 3 상담 로그 3,000+건 생성 (JSONL)
- test_questions.json 50개 추가 (총 100개)

사전 준비:
  pip install openai python-dotenv
  export OPENAI_API_KEY=sk-...

실행:
  python generate_data.py --mode chatlogs   # 상담 로그만
  python generate_data.py --mode questions  # 테스트 질문만
  python generate_data.py --mode all        # 전부
"""

import json
import os
import random
import argparse
import time
from pathlib import Path
from datetime import datetime, timedelta
from openai import OpenAI

client = OpenAI()

# ─── Bitext 27 intents ────────────────────────────────────────────────────────
INTENTS = [
    "cancel_order", "change_order", "place_order", "track_order",
    "delivery_options", "delivery_period", "change_shipping_address", "set_up_shipping_address",
    "check_refund_policy", "get_refund", "track_refund", "check_cancellation_fee",
    "check_payment_methods", "payment_issue",
    "create_account", "delete_account", "edit_account", "recover_password",
    "contact_customer_service", "contact_human_agent",
    "newsletter_subscription",
    "complaint", "review",
    "check_invoice", "get_invoice",
    "loyalty_points", "membership_tiers",
]

AGENTS = ["agent_kim", "agent_lee", "agent_park", "agent_choi", "agent_jung"]
AGENT_ACCURACY = {
    "agent_kim": "correct",
    "agent_lee": "correct",
    "agent_park": "correct",
    "agent_choi": "correct",
    "agent_jung": "partially_correct",  # 신입, 간헐적 오답
}
CHANNELS = ["kakaotalk", "live_chat", "phone"]
TIERS = ["standard", "plus", "vip"]
TIER_WEIGHTS = [0.6, 0.3, 0.1]

CHATLOG_SYSTEM_PROMPT = """
You are generating realistic Korean customer service chat logs for 초록 코퍼레이션
(Cholog Corporation), a Korean e-commerce company.

Generate a single customer service conversation as a JSON object matching this schema:
{
  "conversation_id": "CHAT-YYYY-MM-NNN",
  "timestamp": "ISO8601 timestamp",
  "channel": "kakaotalk|live_chat|phone",
  "customer_tier": "standard|plus|vip",
  "agent_id": "agent_kim|agent_lee|agent_park|agent_choi|agent_jung",
  "turns": [
    {"role": "customer", "text": "..."},
    {"role": "agent", "text": "..."}
  ],
  "resolution": "resolved|partially_resolved|escalated|unresolved",
  "primary_intent": "...",
  "secondary_intent": "...|null",
  "tags": ["tag1", "tag2"],
  "agent_accuracy": "correct|partially_correct|incorrect",
  "accuracy_note": "explanation if not correct, else null"
}

IMPORTANT rules:
- Customer text must be in NATURAL KOREAN (colloquial, not formal)
- Use informal expressions: ㅠㅠ, ;;, 요, 데요, 걸요, etc.
- Agent text should match the agent's profile:
  * agent_kim: 베테랑, 정확하고 간결
  * agent_lee: 친절하지만 가끔 불완전
  * agent_park: 신입, 매뉴얼대로
  * agent_choi: 베테랑, 상세하지만 장황
  * agent_jung: 신입, 가끔 구버전 정책 혼용 (오답 주의)
- For agent_jung, occasionally give WRONG answers based on old policy:
  * Return window: should be 14 days (NOT 7 days as in old v1 policy)
  * Plus tier threshold: should be 200,000 won (NOT 150,000 as in old policy)
  * Standard free shipping: should be 20,000 won (NOT 30,000 as in 2023)
  * Point earning rates: should be 1%/3%/5% (NOT 3%/5%/7% as in old policy)
- 3 to 6 turns typical
- Output ONLY the JSON object, no explanation
"""

QUESTION_SYSTEM_PROMPT = """
You are generating test questions for a RAG (Retrieval-Augmented Generation) evaluation
for 초록 코퍼레이션's customer support chatbot.

Generate a single test question as a JSON object:
{
  "id": "QXXX",
  "question_ko": "Korean question",
  "question_en": "English translation",
  "expected_answer": "Concise expected answer in English",
  "tier": "easy|medium|hard",
  "source_layers": ["faq", "policy", "chatlog"],
  "primary_intent": "one of the 27 Bitext intents",
  "wall_type": "contradiction|conditional|versioning|cross_language|noise|precedent|null"
}

Tier guidelines:
- easy: FAQ single source, direct answer (~70% correct with Naive RAG)
- medium: multiple sources, cross-source contradiction, conditional logic (~20%)
- hard: chat log based, informal Korean, precedent, cross-language failure (~10%)

Company context:
- Return window: 14 days (current v3), was 7 days (v1), 10 days (v2)
- Free shipping: 20,000 won for Standard (was 30,000 in 2023)
- Plus tier: 200,000 won/year (was 150,000), VIP: 800,000 won (was 600,000)
- Point rates: Standard 1%, Plus 3%, VIP 5% (old: 3%/5%/7%)
- Cold-chain delivery: Mon-Thu only, flat 4,000 won (VIP does NOT get free cold-chain)
- Marketplace items: seller's own return policy (NOT Cholog's 14-day policy)
- VIP extended return: up to 30 days at agent discretion (internal policy, not public)

Output ONLY the JSON object.
"""


def generate_chatlog(intent: str, year_month: str, seq: int) -> dict:
    agent = random.choices(AGENTS, weights=[0.30, 0.25, 0.20, 0.15, 0.10])[0]
    tier = random.choices(TIERS, weights=TIER_WEIGHTS)[0]
    channel = random.choice(CHANNELS)

    prompt = f"""
Generate a customer service conversation where:
- primary_intent: {intent}
- channel: {channel}
- customer_tier: {tier}
- agent_id: {agent}
- conversation_id: CHAT-{year_month}-{seq:03d}
- timestamp: 2024-{year_month.split('-')[1]}-{random.randint(1,28):02d}T{random.randint(8,21):02d}:{random.randint(0,59):02d}:00+09:00

Pattern mix (pick ONE randomly):
- 60% accurate resolution
- 20% incomplete or partially correct answer
- 10% topic switch mid-conversation (add secondary_intent)
- 10% emotional/informal customer language with ㅠㅠ ;; etc.
"""

    for attempt in range(3):
        try:
            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": CHATLOG_SYSTEM_PROMPT},
                    {"role": "user", "content": prompt},
                ],
                temperature=0.8,
                max_tokens=800,
            )
            text = response.choices[0].message.content.strip()
            # Strip markdown code blocks if present
            if text.startswith("```"):
                text = text.split("```")[1]
                if text.startswith("json"):
                    text = text[4:]
            return json.loads(text)
        except Exception as e:
            print(f"  retry {attempt+1}/3 for {intent}: {e}")
            time.sleep(2)
    return None


def generate_question(tier: str, existing_count: int) -> dict:
    prompt = f"""
Generate a {tier}-tier test question.
Question ID should be Q{existing_count + 1:03d}.
Make it different from these already-covered intents if possible.
"""
    for attempt in range(3):
        try:
            response = client.chat.completions.create(
                model="gpt-4o-mini",
                messages=[
                    {"role": "system", "content": QUESTION_SYSTEM_PROMPT},
                    {"role": "user", "content": prompt},
                ],
                temperature=0.7,
                max_tokens=400,
            )
            text = response.choices[0].message.content.strip()
            if text.startswith("```"):
                text = text.split("```")[1]
                if text.startswith("json"):
                    text = text[4:]
            return json.loads(text)
        except Exception as e:
            print(f"  retry {attempt+1}/3 for {tier} question: {e}")
            time.sleep(2)
    return None


def generate_chatlogs(target: int = 2700):
    """Generate target number of additional chat log conversations."""
    chatlog_dir = Path("layer3_chatlogs")
    months = [f"2024-{m:02d}" for m in range(1, 13)]

    per_intent = target // len(INTENTS)
    total_generated = 0
    total_failed = 0

    for intent in INTENTS:
        print(f"\n[{intent}] {per_intent}건 생성 중...")
        month = random.choice(months)
        output_file = chatlog_dir / f"{month}.jsonl"

        with open(output_file, "a", encoding="utf-8") as f:
            for i in range(per_intent):
                seq = 900 + total_generated + i  # offset from handwritten 30
                conv = generate_chatlog(intent, month, seq)
                if conv:
                    f.write(json.dumps(conv, ensure_ascii=False) + "\n")
                    total_generated += 1
                else:
                    total_failed += 1
                if (i + 1) % 10 == 0:
                    print(f"  {i+1}/{per_intent} 완료")
                time.sleep(0.3)  # rate limit

    print(f"\n생성 완료: {total_generated}건 / 실패: {total_failed}건")


def generate_questions(target_total: int = 100):
    """Generate additional questions to reach target_total."""
    questions_file = Path("test_questions.json")
    existing = json.loads(questions_file.read_text(encoding="utf-8"))
    current_count = len(existing)
    needed = target_total - current_count

    if needed <= 0:
        print(f"이미 {current_count}개 있음. 추가 불필요.")
        return

    print(f"{current_count}개 → {target_total}개 목표. {needed}개 생성 중...")

    # Target distribution for additional questions
    # Total: 100 = easy 30 + medium 40 + hard 30
    # Current: easy 15 + medium 22 + hard 13 = 50
    # Need:    easy 15 + medium 18 + hard 17 = 50
    easy_current = sum(1 for q in existing if q["tier"] == "easy")
    medium_current = sum(1 for q in existing if q["tier"] == "medium")
    hard_current = sum(1 for q in existing if q["tier"] == "hard")

    to_generate = []
    to_generate += ["easy"] * max(0, 30 - easy_current)
    to_generate += ["medium"] * max(0, 40 - medium_current)
    to_generate += ["hard"] * max(0, 30 - hard_current)
    to_generate = to_generate[:needed]
    random.shuffle(to_generate)

    new_questions = []
    for tier in to_generate:
        q = generate_question(tier, current_count + len(new_questions))
        if q:
            # Fix ID numbering
            q["id"] = f"Q{current_count + len(new_questions) + 1:03d}"
            new_questions.append(q)
            print(f"  Q{q['id']}: [{tier}] {q.get('question_ko', '')[:40]}")
        time.sleep(0.5)

    all_questions = existing + new_questions
    questions_file.write_text(
        json.dumps(all_questions, ensure_ascii=False, indent=2), encoding="utf-8"
    )
    print(f"\n완료: {len(all_questions)}개 저장됨")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description="초록 코퍼레이션 데이터 생성기")
    parser.add_argument(
        "--mode",
        choices=["chatlogs", "questions", "all"],
        default="all",
        help="생성 모드 (default: all)",
    )
    parser.add_argument(
        "--chatlog-target",
        type=int,
        default=2700,
        help="상담 로그 생성 목표 건수 (default: 2700)",
    )
    parser.add_argument(
        "--question-total",
        type=int,
        default=100,
        help="테스트 질문 목표 총 개수 (default: 100)",
    )
    args = parser.parse_args()

    os.chdir(Path(__file__).parent)

    if args.mode in ("chatlogs", "all"):
        generate_chatlogs(args.chatlog_target)

    if args.mode in ("questions", "all"):
        generate_questions(args.question_total)
