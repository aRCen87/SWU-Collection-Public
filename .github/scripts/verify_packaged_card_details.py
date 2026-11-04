"""Verify the card-detail asset as shipped, after AAPT has transformed assets."""
import gzip
import json
import sys
import zipfile
from pathlib import Path
from card_art_identity import language_marker


def verify(apk_path):
    with zipfile.ZipFile(apk_path) as apk:
        # This is the primary asset name opened by CardDetails.loadAsset.
        payload = apk.read("assets/card-details.json")
    bundled = gzip.decompress(
        Path("app/src/main/assets/card-details.json.gz").read_bytes()
    )
    if payload != bundled:
        raise ValueError("Packaged card details differ from source data")
    root = json.loads(payload)
    if root.get("format") != "SWU_CARD_DETAILS" or root.get("version") != 1:
        raise ValueError("Unsupported card details")
    if not root.get("cards"):
        raise ValueError("Empty card details")
    for card in root["cards"].values():
        if not all(isinstance(card[key], str) for key in ("family", "en")):
            raise ValueError("Invalid card identity")
        for key in ("aspects", "traits"):
            if not isinstance(card[key], list) or not all(
                isinstance(value, str) for value in card[key]
            ):
                raise ValueError("Invalid card facets")
        if card.get("cost") is not None and not isinstance(card["cost"], int):
            raise ValueError("Invalid card cost")
    with zipfile.ZipFile(apk_path) as apk:
        artwork = apk.read("assets/card-art-languages.json")
    expected_artwork = gzip.decompress(Path("app/src/main/assets/card-art-languages.json.gz").read_bytes())
    if artwork != expected_artwork:
        raise ValueError("Packaged localized artwork differs from source")
    art = json.loads(artwork)
    if art.get("format") != "SWU_CARD_ART_LANGUAGES" or not art.get("cards"):
        raise ValueError("Invalid localized artwork")
    urls = art.get("urls")
    if not isinstance(urls,list) or not all(isinstance(face,str) and (not face or face.startswith("https://cdn.starwarsunlimited.com/")) for face in urls):
        raise ValueError("Invalid localized URL dictionary")
    def verify_faces(reference,faces):
        if set(faces) != {"FR","EN","DE","ES","IT"}:
            raise ValueError("Missing artwork language: " + reference)
        for language,pair in faces.items():
            if len(pair)!=2 or not all(type(index) is int and 0<=index<len(urls) for index in pair):
                raise ValueError("Invalid localized face: " + reference + " " + language)
            for index in pair:
                marker=language_marker(urls[index])
                if marker and marker!=language:
                    raise ValueError("Wrong artwork language: " + reference + " " + language)
    for reference,faces in art["cards"].items():verify_faces(reference,faces)
    for reference,variants in art.get("printArt",{}).items():
        for variant,faces in variants.items():verify_faces(reference+":"+variant,faces)
    with zipfile.ZipFile(apk_path) as apk:
        print_payload=apk.read("assets/card-print-finishes.json")
    if print_payload != gzip.decompress(Path("app/src/main/assets/card-print-finishes.json.gz").read_bytes()):
        raise ValueError("Packaged print metadata differs from source")
    prints=json.loads(print_payload)
    if prints.get("format")!="SWU_PRINT_FINISHES" or not prints.get("cards"):
        raise ValueError("Invalid packaged print metadata")
    print(f"Verified packaged localized artwork: {len(art['cards'])} references")
    print(f"Verified packaged card details: {len(root['cards'])} cards")


if __name__ == "__main__":
    verify(sys.argv[1])
