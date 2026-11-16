#!/usr/bin/env python3
"""
Writes app/src/main/assets/reflections.json: the short lines GoalGate shows
after the user answers the pause question.

Every line was written for GoalGate (no quotes from anyone), so there is no
licence to track. Each line belongs to one answer:

    YES    "Yes, I need it for this"     -> help them keep the visit short
    MAYBE  "Maybe, I'm not sure"         -> help them decide
    NO     "No, I'm avoiding something"  -> gentle, honest, never shaming

Rules (checked below): lines are questions or calm observations, never
commands or judgements; no em dashes; Korean is natural 해요체, not a
word-for-word translation. Keep ids stable when editing: the log records
which line was shown, so it is not repeated soon.

Run:  python3 tools/make_reflections.py
"""
import json
import os
import sys

# (id, English, Korean)
YES = [
    ("y01", "What is the one thing you came here to do?", "여기서 할 일 하나는 뭐예요?"),
    ("y02", "How will you know when you're done here?", "언제 끝났다고 할 수 있을까요?"),
    ("y03", "Could you give it a small limit, like five minutes?", "5분처럼 작은 한도를 정해 볼까요?"),
    ("y04", "What will you do right after this?", "이거 끝나고 바로 뭘 할 거예요?"),
    ("y05", "Is there a quicker way to get what you need?", "필요한 걸 더 빨리 얻는 방법이 있을까요?"),
    ("y06", "Who are you here to reach?", "누구에게 연락하려고 왔어요?"),
    ("y07", "If the feed pulls you in, what will bring you back out?", "피드에 빠져들면, 무엇이 다시 나오게 해 줄까요?"),
    ("y08", "What does finished look like for this visit?", "이번에는 어디까지 하면 끝인가요?"),
    ("y09", "Can you go straight to it, without scrolling first?", "스크롤 없이 바로 그곳으로 갈 수 있을까요?"),
    ("y10", "When the reason runs out, will you notice?", "이유가 끝나면 알아챌 수 있을까요?"),
]

MAYBE = [
    ("m01", "What would you do with the next ten minutes if you weren't here?", "여기 오지 않았다면 다음 10분 동안 뭘 했을까요?"),
    ("m02", "What were you doing just before you picked up your phone?", "휴대폰을 집기 직전에 뭘 하고 있었어요?"),
    ("m03", "Is this a break you chose, or one that just started?", "스스로 고른 휴식인가요, 아니면 어쩌다 시작된 휴식인가요?"),
    ("m04", "How do you want to feel when you put the phone down?", "휴대폰을 내려놓을 때 어떤 기분이고 싶어요?"),
    ("m05", "What is the smallest step toward your goal you could take instead?", "대신 목표를 향해 내디딜 수 있는 가장 작은 걸음은 뭘까요?"),
    ("m06", "Could you be tired, hungry or bored?", "혹시 피곤하거나, 배고프거나, 심심한 건 아닐까요?"),
    ("m07", "What would make the next few minutes well spent?", "앞으로 몇 분을 잘 쓰려면 무엇이 필요할까요?"),
    ("m08", "Will you remember this in an hour?", "한 시간 뒤에도 이걸 기억할까요?"),
    ("m09", "What are you hoping to find in there?", "거기서 무엇을 찾고 싶어요?"),
    ("m10", "If you wait two minutes, will you still want it?", "2분만 기다려도 여전히 열고 싶을까요?"),
    ("m11", "Is there one thing you've been putting off today?", "오늘 미뤄 둔 일이 하나 있나요?"),
    ("m12", "Is there someone you'd rather talk to right now?", "지금 더 이야기하고 싶은 사람이 있나요?"),
    ("m13", "If you go in, how long do you want to stay?", "들어간다면 얼마나 있고 싶어요?"),
    ("m14", "What would a rested version of you choose?", "푹 쉰 나라면 무엇을 고를까요?"),
]

NO = [
    ("n01", "What are you stepping away from right now?", "지금 무엇에서 잠시 벗어나고 있나요?"),
    ("n02", "What is the hard part of the thing you're avoiding?", "피하고 있는 일에서 어려운 부분은 뭐예요?"),
    ("n03", "Could you give it just the first two minutes?", "그 일을 딱 2분만 해 볼 수 있을까요?"),
    ("n04", "Away from the screen, what could boredom turn into?", "화면에서 떨어지면 심심함이 무엇으로 바뀔 수 있을까요?"),
    ("n05", "What would make the next step easier?", "다음 걸음을 더 쉽게 만드는 건 뭘까요?"),
    ("n06", "Is there a smaller version of the task you could start?", "더 작게 시작할 수 있는 방법이 있을까요?"),
    ("n07", "What do you need right now: rest, food, air or company?", "지금 필요한 건 뭐예요? 휴식, 음식, 바깥 공기, 아니면 사람?"),
    ("n08", "What could you finish before you check again?", "다시 확인하기 전에 끝낼 수 있는 일은 뭘까요?"),
    ("n09", "How will this choice feel tonight?", "오늘 밤 이 선택은 어떻게 느껴질까요?"),
    ("n10", "What is waiting for you that matters more than this?", "이것보다 더 중요한 무엇이 기다리고 있나요?"),
    ("n11", "If a friend felt this way, what would you tell them?", "친구가 이런 기분이라면 뭐라고 말해 줄 거예요?"),
    ("n12", "Could you stand up and stretch first?", "먼저 일어나서 기지개를 켜 볼까요?"),
    ("n13", "What would it take to begin, just a little?", "아주 조금 시작하려면 무엇이 필요할까요?"),
    ("n14", "Is the thing you're avoiding as big as it feels?", "피하고 있는 그 일, 느껴지는 만큼 큰 일일까요?"),
    ("n15", "Where could your phone rest for the next twenty minutes?", "앞으로 20분 동안 휴대폰을 어디에 둘 수 있을까요?"),
    ("n16", "What would make today feel like a good day?", "오늘을 좋은 하루로 만드는 건 무엇일까요?"),
]

EXPECTED = {"YES": 10, "MAYBE": 14, "NO": 16}
MAX_EN = 80


def main():
    groups = {"YES": YES, "MAYBE": MAYBE, "NO": NO}
    out, ids, en_seen, ko_seen = [], set(), set(), set()
    for answer, rows in groups.items():
        assert len(rows) == EXPECTED[answer], f"{answer}: {len(rows)} lines, expected {EXPECTED[answer]}"
        prefix = answer[0].lower()
        for line_id, en, ko in rows:
            assert line_id.startswith(prefix), f"{line_id} should start with {prefix}"
            assert line_id not in ids, f"duplicate id {line_id}"
            assert en and ko, f"{line_id}: missing text"
            assert en not in en_seen, f"duplicate English: {en}"
            assert ko not in ko_seen, f"duplicate Korean: {ko}"
            assert len(en) <= MAX_EN, f"{line_id}: longer than {MAX_EN} characters"
            for text in (en, ko):
                assert "—" not in text and "–" not in text, f"{line_id}: no dashes"
            assert any("가" <= c <= "힣" for c in ko), f"{line_id}: Korean text has no Hangul"
            ids.add(line_id)
            en_seen.add(en)
            ko_seen.add(ko)
            out.append({"id": line_id, "answer": answer, "en": en, "ko": ko})

    root = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
    path = os.path.join(root, "app", "src", "main", "assets", "reflections.json")
    os.makedirs(os.path.dirname(path), exist_ok=True)
    with open(path, "w", encoding="utf-8") as f:
        json.dump(out, f, ensure_ascii=False, indent=1)
        f.write("\n")
    print(f"Wrote {len(out)} reflection lines to {os.path.relpath(path, root)}")


if __name__ == "__main__":
    sys.exit(main())
