"""
Map article text to ISO 3166-1 alpha-2 country codes.
Uses pycountry as a base database + a comprehensive alias dict.
Returns a deduplicated list of matched country codes.
"""
import re
from functools import lru_cache

import pycountry

# ── Extra aliases not covered by pycountry official names ──────────────────
_EXTRA_ALIASES: dict[str, str | None] = {
    # Common informal names / demonyms
    "us": "US", "usa": "US", "america": "US", "american": "US", "americans": "US",
    "uk": "GB", "britain": "GB", "british": "GB", "england": "GB", "english": "GB",
    "scotland": "GB", "scottish": "GB", "wales": "GB", "welsh": "GB",
    "russia": "RU", "russian": "RU", "russians": "RU", "kremlin": "RU",
    "iran": "IR", "iranian": "IR", "iranians": "IR", "tehran": "IR",
    "taiwan": "TW", "taiwanese": "TW",
    "north korea": "KP", "north korean": "KP", "dprk": "KP", "pyongyang": "KP",
    "south korea": "KR", "south korean": "KR", "korean": "KR", "seoul": "KR",
    "china": "CN", "chinese": "CN", "beijing": "CN", "shanghai": "CN",
    "india": "IN", "indian": "IN", "indians": "IN", "delhi": "IN", "mumbai": "IN",
    "pakistan": "PK", "pakistani": "PK", "islamabad": "PK",
    "afghanistan": "AF", "afghan": "AF", "kabul": "AF",
    "israel": "IL", "israeli": "IL", "israelis": "IL", "tel aviv": "IL",
    "palestine": "PS", "palestinian": "PS", "palestinians": "PS", "gaza": "PS", "west bank": "PS",
    "ukraine": "UA", "ukrainian": "UA", "ukrainians": "UA", "kyiv": "UA", "kiev": "UA",
    "germany": "DE", "german": "DE", "berlin": "DE",
    "france": "FR", "french": "FR", "paris": "FR",
    "italy": "IT", "italian": "IT", "rome": "IT",
    "spain": "ES", "spanish": "ES", "madrid": "ES",
    "japan": "JP", "japanese": "JP", "tokyo": "JP",
    "brazil": "BR", "brazilian": "BR", "brasilia": "BR",
    "canada": "CA", "canadian": "CA", "ottawa": "CA",
    "australia": "AU", "australian": "AU", "canberra": "AU",
    "mexico": "MX", "mexican": "MX", "mexico city": "MX",
    "saudi arabia": "SA", "saudi": "SA", "riyadh": "SA",
    "turkey": "TR", "turkish": "TR", "ankara": "TR", "erdogan": "TR",
    "egypt": "EG", "egyptian": "EG", "cairo": "EG",
    "iraq": "IQ", "iraqi": "IQ", "baghdad": "IQ",
    "syria": "SY", "syrian": "SY", "damascus": "SY",
    "lebanon": "LB", "lebanese": "LB", "beirut": "LB",
    "yemen": "YE", "yemeni": "YE", "sanaa": "YE",
    "venezuela": "VE", "venezuelan": "VE", "caracas": "VE",
    "cuba": "CU", "cuban": "CU", "havana": "CU",
    "myanmar": "MM", "burmese": "MM", "burma": "MM", "naypyidaw": "MM",
    "ethiopia": "ET", "ethiopian": "ET", "addis ababa": "ET",
    "somalia": "SO", "somali": "SO", "mogadishu": "SO",
    "sudan": "SD", "sudanese": "SD", "khartoum": "SD",
    "south sudan": "SS",
    "nigeria": "NG", "nigerian": "NG", "abuja": "NG", "lagos": "NG",
    "kenya": "KE", "kenyan": "KE", "nairobi": "KE",
    "congo": "CD", "drc": "CD", "kinshasa": "CD",
    "mali": "ML", "malian": "ML", "bamako": "ML",
    "niger": "NE",
    "burkina faso": "BF",
    "indonesia": "ID", "indonesian": "ID", "jakarta": "ID",
    "philippines": "PH", "philippine": "PH", "filipino": "PH", "manila": "PH",
    "vietnam": "VN", "vietnamese": "VN", "hanoi": "VN",
    "thailand": "TH", "thai": "TH", "bangkok": "TH",
    "bangladesh": "BD", "bangladeshi": "BD", "dhaka": "BD",
    "sri lanka": "LK", "sri lankan": "LK", "colombo": "LK",
    "nepal": "NP", "nepalese": "NP", "kathmandu": "NP",
    "poland": "PL", "polish": "PL", "warsaw": "PL",
    "hungary": "HU", "hungarian": "HU", "budapest": "HU",
    "romania": "RO", "romanian": "RO", "bucharest": "RO",
    "serbia": "RS", "serbian": "RS", "belgrade": "RS",
    "greece": "GR", "greek": "GR", "athens": "GR",
    "sweden": "SE", "swedish": "SE", "stockholm": "SE",
    "norway": "NO", "norwegian": "NO", "oslo": "NO",
    "finland": "FI", "finnish": "FI", "helsinki": "FI",
    "denmark": "DK", "danish": "DK", "copenhagen": "DK",
    "netherlands": "NL", "dutch": "NL", "amsterdam": "NL",
    "belgium": "BE", "belgian": "BE", "brussels": "BE",
    "switzerland": "CH", "swiss": "CH", "bern": "CH",
    "austria": "AT", "austrian": "AT", "vienna": "AT",
    "portugal": "PT", "portuguese": "PT", "lisbon": "PT",
    "czechia": "CZ", "czech": "CZ", "prague": "CZ",
    "slovakia": "SK", "slovak": "SK", "bratislava": "SK",
    "colombia": "CO", "colombian": "CO", "bogota": "CO",
    "argentina": "AR", "argentinian": "AR", "buenos aires": "AR",
    "chile": "CL", "chilean": "CL", "santiago": "CL",
    "peru": "PE", "peruvian": "PE", "lima": "PE",
    "morocco": "MA", "moroccan": "MA", "rabat": "MA",
    "algeria": "DZ", "algerian": "DZ", "algiers": "DZ",
    "tunisia": "TN", "tunisian": "TN", "tunis": "TN",
    "libya": "LY", "libyan": "LY", "tripoli": "LY",
    "qatar": "QA", "qatari": "QA", "doha": "QA",
    "uae": "AE", "emirati": "AE", "dubai": "AE", "abu dhabi": "AE",
    "kuwait": "KW", "kuwaiti": "KW",
    "bahrain": "BH", "bahraini": "BH",
    "oman": "OM", "omani": "OM", "muscat": "OM",
    "jordan": "JO", "jordanian": "JO", "amman": "JO",
    "azerbaijan": "AZ", "azerbaijani": "AZ", "baku": "AZ",
    "armenia": "AM", "armenian": "AM", "yerevan": "AM",
    "georgia": "GE", "georgian": "GE", "tbilisi": "GE",
    "kazakhstan": "KZ", "kazakhstani": "KZ", "astana": "KZ",
    "uzbekistan": "UZ", "uzbek": "UZ",
    "belarus": "BY", "belarusian": "BY", "minsk": "BY",
    "moldova": "MD", "moldovan": "MD", "chisinau": "MD",
    "haiti": "HT", "haitian": "HT",
    "bolivia": "BO", "bolivian": "BO", "la paz": "BO",
    "ecuador": "EC", "ecuadorian": "EC", "quito": "EC",
    "paraguay": "PY", "paraguayan": "PY",
    "uruguay": "UY", "uruguayan": "UY", "montevideo": "UY",
    "ghana": "GH", "ghanaian": "GH", "accra": "GH",
    "senegal": "SN", "senegalese": "SN", "dakar": "SN",
    "ivory coast": "CI", "ivorian": "CI", "cote d'ivoire": "CI",
    "cameroon": "CM", "cameroonian": "CM", "yaounde": "CM",
    "tanzania": "TZ", "tanzanian": "TZ", "dar es salaam": "TZ",
    "mozambique": "MZ", "mozambican": "MZ", "maputo": "MZ",
    "zimbabwe": "ZW", "zimbabwean": "ZW", "harare": "ZW",
    "zambia": "ZM", "zambian": "ZM", "lusaka": "ZM",
    "angola": "AO", "angolan": "AO", "luanda": "AO",
    "south africa": "ZA", "south african": "ZA", "johannesburg": "ZA", "pretoria": "ZA",
    "new zealand": "NZ", "kiwi": "NZ", "wellington": "NZ",
    "singapore": "SG", "singaporean": "SG",
    "malaysia": "MY", "malaysian": "MY", "kuala lumpur": "MY",
    "hong kong": "HK",
    "nato": None,  # multinational — no single code
    "eu": None,
    "europe": None,
    "asia": None,
    "africa": None,
    "un": None,
}


@lru_cache(maxsize=1)
def _build_pattern() -> tuple[re.Pattern, dict[str, str | None]]:
    """Build a compiled regex pattern from all country names + aliases."""
    mapping: dict[str, str | None] = {}

    # Base from pycountry
    for country in pycountry.countries:
        mapping[country.name.lower()] = country.alpha_2
        if hasattr(country, "common_name"):
            mapping[country.common_name.lower()] = country.alpha_2
        if hasattr(country, "official_name"):
            mapping[country.official_name.lower()] = country.alpha_2

    # Override with aliases (aliases take priority for ambiguous names)
    mapping.update(_EXTRA_ALIASES)

    # Sort by length descending so multi-word names match before substrings
    sorted_keys = sorted(mapping.keys(), key=len, reverse=True)
    pattern = re.compile(
        r"\b(" + "|".join(re.escape(k) for k in sorted_keys) + r")\b",
        re.IGNORECASE,
    )
    return pattern, mapping


def extract_countries(text: str) -> list[str]:
    """Return deduplicated ISO alpha-2 codes found in text. Skips None entries."""
    if not text:
        return []
    pattern, mapping = _build_pattern()
    found: list[str] = []
    seen: set[str] = set()
    for match in pattern.finditer(text):
        key = match.group(1).lower()
        code = mapping.get(key)
        if code and code not in seen:
            seen.add(code)
            found.append(code)
    return found
