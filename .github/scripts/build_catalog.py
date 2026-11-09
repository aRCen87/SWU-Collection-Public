#!/usr/bin/env python3
import json
import urllib.request
from collections import OrderedDict, Counter
from datetime import datetime, timezone
from catalog_dates import load_release_dates, release_date_for

SOURCE = "https://raw.githubusercontent.com/Team-Zura/swu-cards-json/main/data/v1/all-cards.json"
DEST = "app/src/main/assets/catalog-fr.json"
SET_NAMES = {
  "SOR": "Étincelle de Rébellion",
  "SHD": "Ombres de la Galaxie",
  "TWI": "Crépuscule de la République",
  "JTL": "Passage en vitesse lumière",
  "LOF": "Légendes de la Force",
  "SEC": "Secrets du pouvoir",
  "LAW": "Un temps sans loi",
  "ASH": "Cendres de l’Empire",
  "HMW": "Mondes Natals",
  "IC27": "Icons — Édition 2027",
  "HMWP": "Jeu organisé hebdomadaire — Mondes Natals",
  "IBH": "Bataille d’introduction : Hoth",
  "C24": "Exclusivité convention 2024",
  "C25": "Exclusivité convention 2025",
  "C26": "Exclusivité convention 2026",
  "JTLP": "Jeu organisé hebdomadaire — Passage en vitesse lumière",
  "LOFP": "Jeu organisé hebdomadaire — Légendes de la Force",
  "SECP": "Jeu organisé hebdomadaire — Secrets du pouvoir",
  "LAWP": "Jeu organisé hebdomadaire — Un temps sans loi",
  "ASHP": "Jeu organisé hebdomadaire — Cendres de l’Empire",
  "P25": "Promos 2025",
  "P26": "Promos 2026",
  "J24": "Programme juges 2024",
  "J25": "Programme juges 2025",
  "G25": "Coffret cadeau 2025",
  "GG": "Gamegenic",
  "TS26": "Twin Suns 2026",
  "MV26": "Promo du film The Mandalorian et Grogu"
}
TYPE_FR = {"Leader":"Leader","Unit":"Unité","Event":"Événement","Base":"Base","Upgrade":"Amélioration","Token":"Jeton"}
RARITY_FR = {"Special":"Spéciale","Common":"Commune","Uncommon":"Peu commune","Rare":"Rare","Legendary":"Légendaire"}

request = urllib.request.Request(SOURCE, headers={"User-Agent":"SWU-Collection-catalog-sync"})
with urllib.request.urlopen(request, timeout=120) as response:
    source = json.load(response)

def french_image_url(card):
    """Only accept an explicitly French HTTPS image, never substitute English art."""
    for field in ("images", "image", "imageUrls", "imageUrl"):
        value = card.get(field)
        if not isinstance(value, dict):
            continue
        french = value.get("fr") or value.get("FR")
        if isinstance(french, dict):
            french = french.get("front") or french.get("url") or french.get("image")
        if isinstance(french, str) and french.startswith("https://"):
            return french
    return ""

unique = OrderedDict()
for card in source:
    name = (card.get("name") or {}).get("fr")
    code = (card.get("set") or {}).get("code", "").upper()
    number = str(card.get("number", "")).strip()
    if number.isdigit():
        number = number.zfill(3)
    if not name or not code or not number:
        continue
    key = (code, number)
    if key in unique:
        continue
    unique[key] = {
        "s": code, "n": number, "name": name,
        "sub": (card.get("subtitle") or {}).get("fr", ""),
        "type": TYPE_FR.get(card.get("type"), card.get("type", "")),
        "rarity": RARITY_FR.get(card.get("rarity"), card.get("rarity", "")),
        "imageUrl": french_image_url(card), "backImageUrl": ""
    }


# Official French source, including collector-numbered variants.
def official_set_cards(set_code):
    import urllib.parse
    result = []
    page = 1
    expected = None
    received = 0
    while True:
        query = urllib.parse.urlencode({
            "locale": "fr",
            "filters[expansion][code][$eq]": set_code,
            "pagination[page]": str(page),
            "pagination[pageSize]": "100"
        })
        req = urllib.request.Request(
            "https://admin.starwarsunlimited.com/api/card-list?" + query,
            headers={"User-Agent": "SWU-Collection/1.0", "Accept": "application/json"}
        )
        with urllib.request.urlopen(req, timeout=45) as response:
            payload = json.load(response)
        batch = payload.get("data", [])
        received += len(batch)
        meta = payload.get("meta", {}).get("pagination", {})
        if expected is None:
            expected = meta.get("total", 0)
        if not batch:
            break
        for entry in batch:
            item = entry.get("attributes", entry)
            expansion = ((item.get("expansion") or {}).get("data") or {}).get("attributes") or {}
            if item.get("locale") != "fr" or expansion.get("code") != set_code:
                continue
            # Icons tokens reuse leader numbers 1/2. Their serials contain 't';
            # only numbered collectible prints belong in this catalogue.
            if set_code == "IC27":
                serial = str(item.get("serialCode", "")).lower()
                if not serial.startswith("ic27") or not serial[4:].isdigit():
                    continue
            number = item.get("cardNumber")
            title = item.get("title")
            if not number or not title:
                continue
            def rel_name(field):
                return (((item.get(field) or {}).get("data") or {}).get("attributes") or {}).get("name", "")
            art = (((item.get("artFront") or {}).get("data") or {}).get("attributes") or {})
            formats = art.get("formats") or {}
            image = ((formats.get("card") or {}).get("url") or art.get("url") or "")
            if not image.startswith("https://cdn.starwarsunlimited.com/"):
                image = ""
            back_art = (((item.get("artBack") or {}).get("data") or {}).get("attributes") or {})
            back_formats = back_art.get("formats") or {}
            back_image = ((back_formats.get("card") or {}).get("url") or back_art.get("url") or "")
            if not back_image.startswith("https://cdn.starwarsunlimited.com/"):
                back_image = ""
            result.append({
                "s": set_code, "n": str(number).zfill(3), "name": title,
                "sub": item.get("subtitle") or "",
                "type": rel_name("type"), "rarity": rel_name("rarity"),
                "imageUrl": image, "backImageUrl": back_image
            })
        if page >= meta.get("pageCount", 0):
            break
        page += 1
        if page > 100:
            raise RuntimeError("Official card pagination exceeds safety limit")
    # Tokens were deliberately excluded above; pagination must still be complete.
    if received != expected or expected < 1:
        raise RuntimeError(f"Incomplete official response: {received}/{expected}")
    if set_code != "IC27" and len(result) != expected:
        raise RuntimeError(f"Incomplete localized response: {len(result)}/{expected}")
    if set_code == "IC27":
        main_numbers = {str(number).zfill(3) for number in range(1, 195)}
        if not main_numbers.issubset({card["n"] for card in result}):
            raise RuntimeError("Incomplete Icons response: missing main collector numbers")
    # Multiple official records can share a collector number; prefer entries with artwork.
    deduplicated = OrderedDict()
    for card in result:
        number = card["n"]
        previous = deduplicated.get(number)
        if previous is None or (bool(card["imageUrl"]), bool(card["backImageUrl"])) > (bool(previous["imageUrl"]), bool(previous["backImageUrl"])):
            deduplicated[number] = card
    return list(deduplicated.values())

# Keep the upstream French metadata, and overlay verified official French images.
# A failed request for one expansion must never erase the other expansions.
for set_code in ("SOR", "SHD", "TWI", "JTL", "LOF", "SEC", "LAW", "ASH", "HMW", "IC27"):
    try:
        official_cards = official_set_cards(set_code)
        image_count = 0
        back_count = 0
        for card in official_cards:
            key = (card["s"], card["n"])
            previous = unique.get(key)
            if previous:
                # Preserve existing French names and metadata when official fields are absent.
                for field in ("name", "sub", "type", "rarity"):
                    if not card.get(field):
                        card[field] = previous.get(field, "")
                if not card["imageUrl"]:
                    card["imageUrl"] = previous.get("imageUrl", "")
                if not card["backImageUrl"]:
                    card["backImageUrl"] = previous.get("backImageUrl", "")
            unique[key] = card
            image_count += bool(card["imageUrl"])
            back_count += bool(card["backImageUrl"])
        print(f"Official French {set_code}: {len(official_cards)} cards, {image_count} fronts, {back_count} backs.")
    except Exception as exc:
        print(f"WARNING: official French {set_code} unavailable: {exc}. Keeping existing entries.")

# Supplementary and promotional sets: try official French art when published.
# Only overlay matching catalogue identities: never invent or add promo cards.
promo_codes = ("ASHP", "C24", "C25", "C26", "G25", "GG", "IBH", "J24", "J25", "JTLP", "LAWP", "LOFP", "MV26", "P25", "P26", "SECP", "TS26")
for set_code in promo_codes:
    try:
        official_cards = official_set_cards(set_code)
        matched = 0
        fronts = 0
        backs = 0
        for card in official_cards:
            key = (set_code, card["n"])
            existing = unique.get(key)
            if existing is None:
                continue
            matched += 1
            if card["imageUrl"] and not existing.get("imageUrl"):
                existing["imageUrl"] = card["imageUrl"]
                fronts += 1
            if card["backImageUrl"] and not existing.get("backImageUrl"):
                existing["backImageUrl"] = card["backImageUrl"]
                backs += 1
        print(f"Official French promo {set_code}: {matched} matched, {fronts} new fronts, {backs} new backs.")
    except Exception as exc:
        print(f"INFO: official French promo {set_code} unavailable: {exc}.")

# Never replace the shipped catalogue with an incomplete upstream response.
# A temporary official API outage must not delete HMW or other existing cards.
with open(DEST, "r", encoding="utf-8") as existing_file:
    existing_catalog = json.load(existing_file)
existing_cards = existing_catalog.get("cards", [])
# Retain existing verified French artwork when an official endpoint is unavailable.
for old_card in existing_cards:
    old_number = str(old_card.get("n", "")).strip()
    if old_number.isdigit():
        old_number = old_number.zfill(3)
    key = (old_card.get("s"), old_number)
    old_card["n"] = old_number
    if key not in unique:
        unique[key] = old_card
    else:
        for image_field in ("imageUrl", "backImageUrl"):
            if not unique[key].get(image_field) and old_card.get(image_field):
                unique[key][image_field] = old_card[image_field]
existing_codes = {card.get("s") for card in existing_cards}
if not existing_codes.issubset({key[0] for key in unique}):
    missing_codes = sorted(existing_codes - {key[0] for key in unique})
    raise RuntimeError(f"Refusing incomplete catalogue update; missing sets: {missing_codes}")
# Compare distinct normalized identities, not raw row counts (which may include
# both "1" and "001" for the same card).
old_keys = {(c.get("s"), str(c.get("n", "")).zfill(3) if str(c.get("n", "")).isdigit() else str(c.get("n", ""))) for c in existing_cards}
new_keys = set(unique)
lost_keys = old_keys - new_keys
if lost_keys:
    raise RuntimeError(f"Refusing catalogue update: {len(lost_keys)} distinct references missing; sample: {sorted(lost_keys)[:10]}")
print(f"Catalogue reconciliation: {len(existing_cards)} old rows, {len(old_keys)} distinct old references, {len(new_keys)} distinct new references.")

cards = list(unique.values())
normalized_keys = [(c["s"], str(c["n"]).zfill(3) if str(c["n"]).isdigit() else str(c["n"])) for c in cards]
if len(normalized_keys) != len(set(normalized_keys)):
    raise RuntimeError("Duplicate normalized set/collector number in catalogue")
print(f"Validated {len(normalized_keys)} distinct normalized set/collector numbers.")
# Do not silently publish an obsolete catalogue that omits the newest main set.
# A missing HMW in the upstream feed requires a different verified French source;
# never manufacture placeholder card names or numbers.
if not any(c["s"] == "HMW" for c in cards):
    print("ATTENTION : source amont sans Mondes Natals (HMW).")

# Image coverage audit: never fill missing French artwork with English assets.
print("=== Audit des illustrations françaises par extension ===")
print("Extension | Références | Rectos FR | Versos disponibles | Leaders sans verso")
for code in sorted({card["s"] for card in cards}):
    subset = [card for card in cards if card["s"] == code]
    fronts = sum(bool(card["imageUrl"]) for card in subset)
    backs = sum(bool(card["backImageUrl"]) for card in subset)
    leaders_missing_back = sum(
        card["type"] in ("Leader", "Chef") and not card["backImageUrl"]
        for card in subset
    )
    print(f"{code} | {len(subset)} | {fronts} | {backs} | {leaders_missing_back}")
    if fronts < len(subset):
        print(f"  ATTENTION : {len(subset) - fronts} recto(s) français manquant(s) pour {code}.")
    if leaders_missing_back:
        print(f"  ATTENTION : {leaders_missing_back} leader(s) sans verso pour {code}.")
print("=== Fin de l'audit ===")

release_dates = load_release_dates()
previous_sets = {item["code"]: item for item in existing_catalog.get("sets", [])}
counts = Counter(card["s"] for card in cards)
sets = []
for code, count in counts.items():
    source_set = next((c.get("set", {}) for c in source if c.get("set", {}).get("code") == code), {})
    original = source_set.get("name", code)
    release_date = release_date_for(code, release_dates, source_set.get("releaseDate", ""), previous_sets.get(code, {}).get("releaseDate", ""))
    sets.append({"code": code, "name": SET_NAMES.get(code, original), "releaseDate": release_date, "cardCount": count})

catalog = {
    "format": "SWU_CATALOG_FR", "version": 1,
    "source": "Team-Zura/swu-cards-json (CC0 1.0)",
    "updatedAt": datetime.now(timezone.utc).isoformat(),
    "sets": sets, "cards": cards
}
with open(DEST, "w", encoding="utf-8") as output:
    json.dump(catalog, output, ensure_ascii=False, separators=(",", ":"))
print(f"Catalogue français généré : {len(cards)} numéros uniques, {len(sets)} extensions.")
if "HMW" not in counts:
    print("AVERTISSEMENT : Mondes Natals (HMW) absente de la source actuelle ; ne pas annoncer le catalogue comme complet.")
elif len({c["n"] for c in cards if c["s"] == "HMW" and str(c["n"]).isdigit() and int(c["n"]) <= 272}) < 272:
    print("AVERTISSEMENT : Mondes Natals (HMW) présente mais les 272 références principales ne sont pas toutes disponibles en français.")

