"""
Score article importance on a 0.0–1.0 scale.
Factors: source reliability, headline urgency keywords, recency.
"""
import re

# Keyword sets and their boost multipliers
_CRITICAL_KEYWORDS = re.compile(
    r"\b(nuclear|attack|war|coup|massacre|genocide|invasion|missile|bomb|explosion|"
    r"assassination|chemical weapon|biological weapon|radiological|pandemic|catastrophe)\b",
    re.IGNORECASE,
)
_HIGH_KEYWORDS = re.compile(
    r"\b(crisis|sanctions|military|conflict|airstrike|offensive|ceasefire|evacuate|"
    r"emergency|outbreak|earthquake|tsunami|hurricane|flood|drought|famine|protest|"
    r"revolution|election|referendum|impeach|arrest|detain|blockade|embargo)\b",
    re.IGNORECASE,
)
_MEDIUM_KEYWORDS = re.compile(
    r"\b(deal|summit|talks|treaty|agreement|diplomatic|tension|dispute|warning|"
    r"economic|inflation|recession|default|collapse|surge|decline)\b",
    re.IGNORECASE,
)

# Source name → base reliability weight (fallback 0.60)
_SOURCE_WEIGHTS: dict[str, float] = {
    "Reuters World": 0.95,
    "BBC World News": 0.95,
    "Associated Press": 0.95,
    "AFP": 0.90,
    "Al Jazeera": 0.85,
    "Deutsche Welle": 0.85,
    "France 24": 0.85,
    "NHK World": 0.85,
    "Foreign Policy": 0.90,
    "Council on Foreign Relations": 0.85,
    "WHO News": 0.90,
    "Human Rights Watch": 0.80,
    "Amnesty International": 0.80,
    "Financial Times Economy": 0.90,
    "Bloomberg Markets": 0.90,
    "Arms Control Association": 0.85,
    "RAND Corporation": 0.85,
    "Chatham House": 0.90,
    "Bellingcat": 0.85,
}


def score_article(title: str, source_name: str, reliability_weight: float | None = None) -> float:
    """Return importance score 0.0–1.0."""
    base = reliability_weight if reliability_weight is not None else _SOURCE_WEIGHTS.get(source_name, 0.60)

    keyword_boost = 0.0
    if _CRITICAL_KEYWORDS.search(title):
        keyword_boost = 0.40
    elif _HIGH_KEYWORDS.search(title):
        keyword_boost = 0.25
    elif _MEDIUM_KEYWORDS.search(title):
        keyword_boost = 0.10

    score = base * 0.6 + keyword_boost
    return min(1.0, score)
