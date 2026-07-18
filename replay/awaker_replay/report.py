"""휴리스틱 후보 vs teacher 룰 윈도우 채점 리포트 (이슈 07 AC)."""

from __future__ import annotations

from dataclasses import dataclass

from .replay import Transition
from .windows import Window


@dataclass(frozen=True)
class Score:
    windows: int
    agree: int
    false_positive: int   # 후보 양성, teacher 음성 (오탐)
    false_negative: int   # 후보 음성, teacher 양성 (미탐)
    teacher_positive: int

    @property
    def agreement(self) -> float:
        return self.agree / self.windows if self.windows else 0.0

    def summary(self) -> str:
        return (
            f"휴리스틱 채점: 윈도우 {self.windows}개 (teacher 양성 {self.teacher_positive})\n"
            f"  일치율 {self.agreement:.1%} / 오탐 {self.false_positive} / 미탐 {self.false_negative}"
        )


def teacher_window_labels(windows: list[Window], transitions: list[Transition]) -> list[bool]:
    """replay 전이로부터 각 윈도우 종료 시점의 teacher 상태를 라벨링한다."""
    labels = []
    ti = 0
    positive = False
    for w in windows:
        while ti < len(transitions) and transitions[ti].at_ms <= w.end_ms:
            positive = transitions[ti].state == "enter"
            ti += 1
        labels.append(positive)
    return labels


def score(teacher: list[bool], candidate: list[bool]) -> Score:
    assert len(teacher) == len(candidate)
    agree = fp = fn = 0
    for t, c in zip(teacher, candidate):
        if t == c:
            agree += 1
        elif c and not t:
            fp += 1
        else:
            fn += 1
    return Score(
        windows=len(teacher),
        agree=agree,
        false_positive=fp,
        false_negative=fn,
        teacher_positive=sum(teacher),
    )
