#!/usr/bin/env bash
set -euxo pipefail
apk=app/build/outputs/apk/release/app-release.apk
package=fr.swucollection.app
mkdir -p smoke
trap 'adb logcat -d -s AndroidRuntime:E > smoke/crashes.txt; adb shell wm size reset >/dev/null 2>&1; adb shell settings put system font_scale 1.0 >/dev/null 2>&1; adb shell cmd connectivity airplane-mode disable >/dev/null 2>&1; adb shell svc wifi enable >/dev/null 2>&1; adb shell svc data enable >/dev/null 2>&1' EXIT
adb install -r "$apk"
adb shell pm grant "$package" android.permission.POST_NOTIFICATIONS
adb logcat -c
# Only read-only transfers are retried; user input actions are never repeated.
pull_file() {
  for pull_attempt in 1 2 3 4; do
    if adb pull "$@"; then return 0; fi
    timeout 10 adb wait-for-device || true
    sleep 1
  done
  return 1
}
# UiAutomation may report a null root while Compose updates. Never reuse an old dump.
dump_window() {
  for dump_attempt in 1 2 3 4; do
    adb shell rm -f /sdcard/swu-window.xml
    if adb shell uiautomator dump /sdcard/swu-window.xml && adb shell test -s /sdcard/swu-window.xml; then
      return 0
    fi
    sleep 1
  done
  echo "Unable to read the current Android interface" >&2
  return 1
}
for launch in 1 2; do
  adb shell am force-stop "$package"
  adb shell am start -W -n "$package/.MainActivity" > "smoke/launch-$launch.txt"
  sleep 8
  adb shell pidof "$package"
  dump_window
  pull_file /sdcard/swu-window.xml "smoke/window-$launch.xml"
  python3 - "$launch" <<'PY'
from pathlib import Path
import sys
import xml.etree.ElementTree as ET
root=ET.parse('smoke/window-'+sys.argv[1]+'.xml').getroot()
text=' '.join(n.attrib.get('text','') for n in root.iter('node'))
assert 'SCANNER' in text and 'EXTENSIONS' in text, text
assert 'Récupérer l’ouverture' not in text, text
PY
  adb exec-out screencap -p > "smoke/home-$launch.png"
done
# Check a real twenty-second rotation on the home screen.
python3 - <<'PYQUOTEFIRST'
import json,xml.etree.ElementTree as ET
from pathlib import Path
quotes=["« Que la Force soit avec toi. »","« Fais-le ou ne le fais pas, mais il n’y a pas d’essai. »","« Je suis un Jedi, comme mon père l’a été avant moi. »","« Au secours Obi-Wan Kenobi, vous êtes mon seul espoir. »","« Non, je suis ton père. »","« Il y a toujours un plus gros poisson. »","« L’espoir est à la base de toutes les rébellions. »","« Seuls les Sith sont aussi absolus. »","« Le plus grand maître, l’échec est. »","« C’est un piège ! »","« La peur est le chemin du côté obscur. »","« J’ai un très bon pressentiment. »","« Beaucoup encore il te reste à apprendre. »","« Je sais. »","« Ainsi s’éteint la liberté, sous une pluie d’applaudissements. »","« La Force est avec moi, je fais corps avec la Force. »","« Tu avais raison à mon sujet. »","« Dieu me débranche ! »","« Tu étais l’Élu, c’était toi ! »","« Je t’aime. »","« Je ne suis personne. »","« Chewie, nous sommes chez nous. »","« Personne n’est jamais vraiment parti. »","« En sauvant ceux qu’on aime. »","« Nous sommes l’étincelle qui allumera l’incendie. »","« Je suis tous les Jedi. »","« Je suis tous les Sith. »","« Ce n’est pas parce que tu parles que tu es intelligent. »","« J’ai un mauvais pressentiment. »","« Je n’aime pas le sable. »"]
texts={n.attrib.get('text','') for n in ET.parse('smoke/window-2.xml').iter('node')}
visible=[q for q in quotes if q in texts]
assert len(visible)==1, texts
Path('smoke/home-quote-first.json').write_text(json.dumps({'quotes':quotes,'first':visible[0]}),encoding='utf-8')
PYQUOTEFIRST
sleep 21
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/home-quote-next.xml >/dev/null
python3 - <<'PYQUOTENEXT'
import json,xml.etree.ElementTree as ET
from pathlib import Path
data=json.loads(Path('smoke/home-quote-first.json').read_text(encoding='utf-8'))
texts={n.attrib.get('text','') for n in ET.parse('smoke/home-quote-next.xml').iter('node')}
visible=[q for q in data['quotes'] if q in texts]
assert len(visible)==1 and visible[0]!=data['first'], texts
assert any(' · ' in t and any(f in t for f in ["Un nouvel espoir","L’Empire contre-attaque","Le Retour du Jedi","La Menace fantôme","Rogue One","La Revanche des Sith","Les Derniers Jedi","Solo: A Star Wars Story","L’Attaque des clones","Le Réveil de la Force","L’Ascension de Skywalker"]) for t in texts), texts
PYQUOTENEXT
adb exec-out screencap -p > smoke/home-quote-next.png
# Verify the home buttons remain accessible with enlarged Android text.
adb shell settings put system font_scale 1.5
sleep 3
# Use the empty side gutter: an injected swipe starting on a button can become
# a click when emulator input delivery stalls. Keep all accessibility assertions.
# More frames cover wrapped quotes and buttons at 150% Android font size.
for step in 0 1 2 3 4 5 6 7; do
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml "smoke/home-large-font-$step.xml" >/dev/null
  adb exec-out screencap -p > "smoke/home-large-font-$step.png"
  adb shell input swipe 8 480 8 170 600
  sleep 1
done
python3 - <<'PYHOME'
from pathlib import Path
import xml.etree.ElementTree as ET
labels=set()
for p in Path('smoke').glob('home-large-font-*.xml'):
    labels.update(n.attrib.get('text','') for n in ET.parse(p).iter('node'))
assert {'SCANNER','EXTENSIONS','MA COLLECTION','MES DECKS','TOURNOIS & MÉTA','BADGES','ÉCHANGES','STATISTIQUES'} <= labels,labels
assert any(t in labels for t in ['0 exemplaires · 0 cartes différentes','0 exemplaires • 0 cartes différentes']), labels
PYHOME
adb shell settings put system font_scale 1.0
adb shell am force-stop "$package"
adb shell am start -W -n "$package/.MainActivity" >/dev/null
sleep 8
# Exercise every primary destination using the actual rendered labels and bounds.
for destination in Collection Extensions "Mes listes" Échanges Accueil; do
  dump_window
  pull_file /sdcard/swu-window.xml smoke/navigation.xml
  read -r tap_x tap_y < <(python3 - "$destination" <<'PYCOORD'
import re,sys,xml.etree.ElementTree as ET
nodes=[n for n in ET.parse('smoke/navigation.xml').iter('node') if n.attrib.get('text')==sys.argv[1]]
assert nodes, sys.argv[1]
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].attrib['bounds']))
print((x1+x2)//2,(y1+y2)//2)
PYCOORD
)
  adb shell input tap "$tap_x" "$tap_y"
  sleep 2
  adb shell pidof "$package"
  adb exec-out screencap -p > "smoke/tab-${destination// /-}.png"
  if [ "$destination" = "Collection" ]; then
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/fresh-install-collection.xml >/dev/null
    python3 - <<'PYFRESHINSTALL'
import xml.etree.ElementTree as ET
texts=[n.attrib.get('text','') for n in ET.parse('smoke/fresh-install-collection.xml').iter('node')]
assert any('Ta collection est vide.' in t for t in texts), texts
assert any('0 références et variantes possédées' in t and '0 exemplaires' in t for t in texts), texts
PYFRESHINSTALL
    adb exec-out screencap -p > smoke/fresh-install-collection.png
  fi
done
# Open, save and reopen a deck on the packaged optimized application.
dismiss_badge_celebration() {
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml smoke/badge-auto-window.xml >/dev/null
  if python3 - <<'PYBADGEAUTO'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/badge-auto-window.xml').iter('node'))
sys.exit(0 if any('fait' in n.attrib.get('text','') and 'débloqué' in n.attrib.get('text','') for n in nodes) else 1)
PYBADGEAUTO
  then KEEP_BADGES=1 tap_label "Continuer"; fi
}
tap_label() {
  # Home shortcuts and summaries can place pinned decks beyond six short swipes.
  for attempt in {1..12}; do
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/deck-window.xml >/dev/null
    if [ "${KEEP_BADGES:-0}" != "1" ] && python3 - <<'PYHASBADGE'
import sys,xml.etree.ElementTree as ET
sys.exit(0 if any('fait' in n.attrib.get('text','') and 'débloqué' in n.attrib.get('text','') for n in ET.parse('smoke/deck-window.xml').iter('node')) else 1)
PYHASBADGE
    then dismiss_badge_celebration; fi
    if coordinates=$(python3 - "$1" <<'PYDECK'
import re,sys,xml.etree.ElementTree as ET
root=ET.parse('smoke/deck-window.xml').getroot()
parents={child:parent for parent in root.iter() for child in parent}
# Search text can equal a card or badge title before that result is visible.
# Only labels are action targets; editable values must never stand in for results.
nodes=[n for n in root.iter('node') if n.attrib.get('class')!='android.widget.EditText' and n.attrib.get('text','').casefold()==sys.argv[1].casefold()]
if not nodes: sys.exit(1)
if sys.argv[1] in ['Deck','Réserve','Leader','Base']:
    node=min(nodes,key=lambda n:int(re.findall(r'\d+',n.attrib['bounds'])[1]))
else:
    node=nodes[-1]
# Floating text-field labels are not the editable touch target. Tap the actual
# EditText body so a second search reliably reopens the keyboard after clearing.
ancestor=node
while ancestor in parents:
    ancestor=parents[ancestor]
    if ancestor.attrib.get('class')=='android.widget.EditText':
        node=ancestor
        break
x1,y1,x2,y2=map(int,re.findall(r'\d+',node.attrib['bounds']))
if x2 <= x1 or y2 <= y1: sys.exit(1)
print((x1+x2)//2,(y1+y2)//2)
PYDECK
); then
      read -r tap_x tap_y <<< "$coordinates"
      adb shell input tap "$tap_x" "$tap_y"
      sleep 2
      adb shell pidof "$package"
      if [ "$1" == "Accueil" ] && [ "${KEEP_BADGES:-0}" != "1" ]; then dismiss_badge_celebration; fi
      return
    fi
    adb shell input swipe 160 440 160 330 300
  done
  echo "Missing visible control: $1" >&2
  return 1
}
tap_label "Mes listes"
tap_label "+ Créer un deck"
tap_label "Commencer"
tap_label "Leader"
tap_label "Rechercher une carte"
adb shell input text Krennic
sleep 2
adb shell input keyevent 4
sleep 1
adb shell input swipe 160 440 160 330 300
tap_label "Choisir"
tap_label "Rechercher une carte"
adb shell input text Base%sEcho
sleep 2
adb shell input keyevent 4
sleep 1
adb shell input swipe 160 440 160 330 300
tap_label "Choisir"
tap_label "Leader"
tap_label "Liste"
tap_label "Cartes"
tap_label "Filtres"
adb exec-out screencap -p > smoke/deck-filters.png
tap_label "Voir les cartes"
tap_label "Trier"
tap_label "Coût croissant"
# A draw-card cost filter must not make identity selection empty.
tap_label "Deck"
tap_label "Filtres"
tap_label "Coût : Tous"
tap_label "2"
tap_label "Voir les cartes"
tap_label "Leader"
tap_label "Filtres"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/leader-filters.xml >/dev/null
python3 - <<'PYIDENTITYFILTERS'
import xml.etree.ElementTree as ET
texts=[n.attrib.get('text','') for n in ET.parse('smoke/leader-filters.xml').iter('node')]
assert any(t.startswith('Affinité :') for t in texts),texts
assert not any(t.startswith(('Coût :','Type :','Arène :')) for t in texts),texts
PYIDENTITYFILTERS
adb exec-out screencap -p > smoke/leader-filters.png
tap_label "Voir les cartes"
tap_label "Rechercher une carte"
adb shell input text SOR_001
adb shell input keyevent 4
sleep 2
for attempt in 1 2 3 4 5 6 7; do
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml smoke/deck-reference-search.xml >/dev/null
  if python3 - <<'PYREFERENCEVISIBLE'
import sys,xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/deck-reference-search.xml').iter('node')}
sys.exit(0 if 'SOR 001' in texts else 1)
PYREFERENCEVISIBLE
  then break; fi
  adb shell input swipe 160 440 160 330 300
  sleep 1
done
python3 - <<'PYREFERENCESEARCH'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/deck-reference-search.xml').iter('node')}
assert 'SOR 001' in texts,texts
PYREFERENCESEARCH
adb exec-out screencap -p > smoke/deck-reference-search.png
tap_label "Leader"
tap_label "Base"
tap_label "Deck"
tap_label "Mon deck"
tap_label "Nom du deck"
adb shell input keyevent 123
adb shell input text X
tap_label "Valider"
tap_label "Annuler"
sleep 1
# An unsaved creation must survive a force stop and be offered as a local draft.
adb shell am force-stop "$package"
adb shell am start -W -n "$package/.MainActivity" > smoke/draft-relaunch.txt
sleep 8
tap_label "Mes listes"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/deck-draft-library.xml >/dev/null
python3 - <<'PYDRAFTLIBRARY'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/deck-draft-library.xml').iter('node')}
assert any(t.startswith('Brouillon · ') for t in texts), texts
assert 'Modifier' in texts and 'Supprimer' in texts, texts
PYDRAFTLIBRARY
adb exec-out screencap -p > smoke/deck-draft-library.png
tap_label "Modifier"
tap_label "Reprendre"
tap_label "Analyse & budget"
adb exec-out screencap -p > smoke/deck-analysis.png
tap_label "Fermer"
tap_label "Affinités"
tap_label "Affinités"
tap_label "Exporter"
# Generate actual illustrated and physical-size proxy PDFs through Android's save dialog.
wait_for_pdf_save() {
  for attempt in $(seq 1 30); do
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/pdf-save-window.xml >/dev/null
    if python3 - <<'PYSAVE'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/pdf-save-window.xml').iter('node'))
sys.exit(0 if any(n.attrib.get('text','').casefold()=='save' and 'documentsui' in n.attrib.get('package','') for n in nodes) else 1)
PYSAVE
    then tap_label "Save"; return; fi
    sleep 2
  done
  adb exec-out screencap -p > smoke/pdf-error.png
  echo 'PDF generation did not reach the Android save dialog' >&2
  return 1
}
wait_for_pdf_preview() {
  for attempt in $(seq 1 25); do
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/pdf-preview-window.xml >/dev/null
    if python3 - <<'PYPREVIEWREADY'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/pdf-preview-window.xml').iter('node'))
sys.exit(0 if any(n.attrib.get('content-desc','').startswith('Page ') and 'du PDF' in n.attrib.get('content-desc','') for n in nodes) else 1)
PYPREVIEWREADY
    then adb exec-out screencap -p > smoke/pdf-preview.png; tap_label "Enregistrer le PDF"; return; fi
    sleep 2
  done
  echo 'Actual PDF preview did not render' >&2
  return 1
}
tap_label "PDF, proxies et autres exports"
tap_label "PDF illustré"
adb exec-out screencap -p > smoke/illustrated-pdf-options.png
tap_label "Créer le PDF"
wait_for_pdf_preview
wait_for_pdf_save
tap_label "Fermer l’aperçu"
tap_label "Fermer"
tap_label "Proxies des cartes manquantes"
adb exec-out screencap -p > smoke/proxy-selection.png
tap_label "Créer le PDF"
adb exec-out screencap -p > smoke/proxy-pdf-options.png
tap_label "Créer le PDF"
wait_for_pdf_preview
wait_for_pdf_save
tap_label "Fermer l’aperçu"
tap_label "Fermer"
tap_label "Retour"
for pdf in SWU-Deck-illustre.pdf SWU-Proxies.pdf; do
  pull_file "/sdcard/Download/$pdf" "smoke/$pdf"
done
python3 - <<'PYPDF'
from pathlib import Path
for name in ['SWU-Deck-illustre.pdf','SWU-Proxies.pdf']:
    data=(Path('smoke')/name).read_bytes()
    assert data.startswith(b'%PDF-'), name
    assert b'/Subtype /Image' in data or b'/Subtype/Image' in data, 'No card image embedded in '+name
    assert len(data)>2000, name
PYPDF
tap_label "Copier et ouvrir Melee"
adb shell am start -W -f 0x00020000 -n "$package/.MainActivity" >/dev/null
sleep 2
tap_label "Copier seulement"
adb exec-out screencap -p > smoke/export-melee.png
tap_label "Copier et ouvrir Karabast"
adb shell am start -W -f 0x00020000 -n "$package/.MainActivity" >/dev/null
sleep 2
tap_label "Copier seulement"
adb exec-out screencap -p > smoke/export-karabast.png
tap_label "Fermer"
tap_label "Enregistrer"
# A saved deck can be exported directly without entering its editor.
tap_label "Exporter"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/deck-library-export.xml >/dev/null
python3 - <<'PYLIBRARYEXPORT'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/deck-library-export.xml').iter('node'))
assert 'Copier et ouvrir Melee' in text and 'Copier et ouvrir Karabast' in text,text
PYLIBRARYEXPORT
tap_label "Fermer"
adb shell am force-stop "$package"
adb shell am start -W -n "$package/.MainActivity" > smoke/deck-relaunch.txt
sleep 8
tap_label "Mes listes"
tap_label "Mon deck"
tap_label "Ma sélection"
adb shell input swipe 160 330 160 450 300
sleep 2
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/deck-tabs.xml >/dev/null
python3 - <<'PYTABS'
import re,xml.etree.ElementTree as ET
root=ET.parse('smoke/deck-tabs.xml').getroot()
heights=[]
for label in ['Deck','Réserve','Leader','Base']:
    nodes=[n for n in root.iter('node') if n.attrib.get('text')==label]
    assert nodes, 'Missing deck tab: '+label
    x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].attrib['bounds']))
    assert x2>x1 and y2>y1, 'Empty deck tab: '+label
    heights.append(y2-y1)
assert max(heights)<=min(heights)*1.5, 'Deck tab label wraps: '+str(heights)
PYTABS
adb exec-out screencap -p > smoke/deck-editor.png
# The selected leader opens the existing full card viewer without discarding edits.
tap_description() {
  for attempt in 1 2 3 4 5 6; do
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml smoke/preview-window.xml >/dev/null
  if coordinates=$(python3 - "$1" <<'PYPREVIEW'
import re,sys,xml.etree.ElementTree as ET
nodes=[n for n in ET.parse('smoke/preview-window.xml').iter('node') if n.attrib.get('content-desc','').startswith(sys.argv[1])]
if not nodes: sys.exit(1)
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[-1].attrib['bounds']))
if x2 <= x1 or y2 <= y1: sys.exit(1)
print((x1+x2)//2,(y1+y2)//2)
PYPREVIEW
); then
  read -r tap_x tap_y <<< "$coordinates"
  adb shell input tap "$tap_x" "$tap_y"
  sleep 2
  adb shell pidof "$package"
  return
  fi
  # Card previews are below the deck heading; close/menu controls are above it.
  if [[ "$1" == Agrandir* ]]; then
    adb shell input swipe 160 480 160 330 350
  else
    adb shell input swipe 160 250 160 500 350
  fi
  done
  echo "Missing visible image action: $1" >&2
  return 1
}
# Scan a real catalogue reference into the deck without touching collection quantities.
adb shell pm grant "$package" android.permission.CAMERA
tap_label "Scanner dans ce deck"
scan_deck_reference() {
  tap_label "Saisir une référence"
  tap_label "Extension"
  adb shell input text "$1"
  tap_label "Numéro"
  adb shell input text "$2"
  adb shell input keyevent 4
  tap_label "Rechercher"
}
# Derive gestures from the actual scrollable pane, including its padding.
# The compact scanner pane starts at x=16: a gesture at x=8 hits the margin.
scroll_scanner_pane() {
  local direction="$1"
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml smoke/scanner-scroll-window.xml >/dev/null
  local gesture
  gesture=$(python3 - "$direction" <<'PYSCROLLPANE'
import re, sys, xml.etree.ElementTree as ET
panes=[n for n in ET.parse('smoke/scanner-scroll-window.xml').iter('node')
       if n.attrib.get('scrollable')=='true']
assert len(panes)==1, 'Expected the scanner actions pane, got '+str(len(panes))
x1,y1,x2,y2=map(int,re.findall(r'\d+',panes[0].attrib['bounds']))
assert x2-x1>20 and y2-y1>40, 'Scanner actions pane is too small'
x=(x1+x2)//2
upper=y1+(y2-y1)//5
lower=y2-(y2-y1)//5
start,end=(lower,upper) if sys.argv[1]=='up' else (upper,lower)
print(x,start,x,end)
PYSCROLLPANE
)
  read -r start_x start_y end_x end_y <<< "$gesture"
  adb shell input swipe "$start_x" "$start_y" "$end_x" "$end_y" 500
}
# Gather one unchanged scanner state across the scrollable actions pane.
# At 320dp the preview, collection switch and deck counters cannot fit together.
# Keep every assertion; only scrolling is allowed between these captures.
capture_scanner_state() {
  local capture="$1"
  shift
  rm -f "$capture"
  for capture_step in {1..10}; do
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/scanner-state-window.xml >/dev/null
    if python3 - "$capture" "$@" <<'PYSCANNERSTATE'
from pathlib import Path
import sys, xml.etree.ElementTree as ET
path=Path(sys.argv[1])
root=ET.parse(path).getroot() if path.exists() else ET.Element('scanner-state')
root.append(ET.parse('smoke/scanner-state-window.xml').getroot())
ET.ElementTree(root).write(path, encoding='utf-8')
text=' '.join(n.attrib.get('text','') for n in root.iter('node'))
missing=[label for label in sys.argv[2:] if label not in text]
if missing:
    print('Scanner controls still outside viewport:', missing, file=sys.stderr)
sys.exit(bool(missing))
PYSCANNERSTATE
    then return 0; fi
    # Stay inside the actions pane, away from the fixed camera preview.
    scroll_scanner_pane up
  done
  echo "Missing scanner state in $capture" >&2
  return 1
}
scan_deck_reference JTL 788
# Recognition must not mutate a deck before explicit validation.
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/deck-scan-unconfirmed.xml >/dev/null
# Manual-entry controls can leave the actions pane scrolled below the card
# summary. Keep the confirmation check, then inspect the earlier content in
# that same pane. The fixed camera/header must not receive these gestures.
for summary_step in 1 2 3; do
  scroll_scanner_pane down
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml "smoke/deck-scan-summary-$summary_step.xml" >/dev/null
done
adb exec-out screencap -p > smoke/deck-scan-summary.png
python3 - <<'PYUNCONFIRMED'
from pathlib import Path
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/deck-scan-unconfirmed.xml').iter('node')}
assert 'Valider · Ajouter à « Mon deck »' in texts and 'Annuler' in texts,texts
for capture in Path('smoke').glob('deck-scan-summary-*.xml'):
    texts.update(n.attrib.get('text','') for n in ET.parse(capture).iter('node'))
assert any('JTL 788' in t for t in texts),texts
assert any('Cote' in t for t in texts),texts
PYUNCONFIRMED
tap_label "Annuler"
scan_deck_reference JTL 788
tap_label "Valider · Ajouter à « Mon deck »"
tap_label "Rafale"
adb shell input swipe 160 440 160 610 300
sleep 1
capture_scanner_state smoke/deck-scan-main.xml "Ajouter à « Mon deck »" "Deck 1" "Ajoutée à"
python3 - <<'PYDECKSCAN'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/deck-scan-main.xml').iter('node'))
assert 'Ajouter à « Mon deck »' in text and 'Deck 1' in text and 'Ajoutée à' in text,text
PYDECKSCAN
tap_label "Annuler le dernier ajout"
adb shell input swipe 160 440 160 610 300
sleep 1
tap_label "Ajouter aussi à ma collection"
scan_deck_reference JTL 788
adb shell input swipe 160 440 160 610 300
sleep 1
capture_scanner_state smoke/deck-scan-collection.xml "1 ajout(s) à la collection en attente" "Deck 1"
python3 - <<'PYDECKCOLLECTION'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/deck-scan-collection.xml').iter('node'))
assert '1 ajout(s) à la collection en attente' in text and 'Deck 1' in text,text
PYDECKCOLLECTION
tap_label "Annuler le dernier ajout"
scan_deck_reference SOR 095
# An ambiguous foil finish must not interrupt the camera: review happens at the end.
# Closing manual entry can leave the scrollable controls at the bottom. Reveal
# the collection summary just as for the other deck scans before asserting it.
adb shell input swipe 160 440 160 610 300
sleep 1
capture_scanner_state smoke/deck-scan-pending-finish.xml "Ajouter à « Mon deck »" "1 ajout(s) à la collection en attente"
python3 - <<'PYPENDINGFINISH'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/deck-scan-pending-finish.xml').iter('node'))
assert 'Ajouter à « Mon deck »' in text and '1 ajout(s) à la collection en attente' in text,text
assert 'Vérifier la carte pour la collection' not in text,text
PYPENDINGFINISH
tap_label "Annuler le dernier ajout"
adb shell input swipe 160 440 160 610 300
sleep 1
capture_scanner_state smoke/deck-scan-collection-undo.xml "0 ajout(s) à la collection en attente" "Deck 0"
python3 - <<'PYDECKCOLLECTIONUNDO'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/deck-scan-collection-undo.xml').iter('node'))
assert '0 ajout(s) à la collection en attente' in text and 'Deck 0' in text,text
PYDECKCOLLECTIONUNDO
adb shell input swipe 160 440 160 610 300
sleep 1
tap_label "Ajouter aussi à ma collection"
tap_label "Réserve 0/10"
scan_deck_reference JTL 788
adb shell input swipe 160 440 160 610 300
sleep 1
capture_scanner_state smoke/deck-scan-reserve.xml "Réserve 1/10" "Deck 0"
python3 - <<'PYDECKRESERVE'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/deck-scan-reserve.xml').iter('node'))
assert 'Réserve 1/10' in text and 'Deck 0' in text,text
PYDECKRESERVE
tap_label "Annuler le dernier ajout"
scan_deck_reference SOR 002
tap_label "Annuler"
adb exec-out screencap -p > smoke/deck-scan.png
tap_label "Terminer"
tap_label "Deck"
tap_description "Agrandir Leader"
adb exec-out screencap -p > smoke/deck-card-preview.png
tap_description "Fermer la carte"
# Cover convenient deck access, explicit image downloads and fold-like resizing.
tap_label "Préparer"
tap_label "Exporter la liste d’achat"
wait_for_pdf_save
pull_file /sdcard/Download/SWU-manquantes.txt smoke/SWU-manquantes.txt
python3 - <<'PYSHOP'
from pathlib import Path
text=Path('smoke/SWU-manquantes.txt').read_text(encoding='utf-8')
assert 'Cartes à trouver' in text and 'Krennic' in text and 'SOR' in text, text
PYSHOP
tap_label "Fermer"
density=$(adb shell wm density | python3 -c 'import re,sys; print(re.findall(r"\d+",sys.stdin.read())[-1])')
for width in 320 720; do
  dimensions=$(python3 - "$density" "$width" <<'PYDIM'
import sys
scale=int(sys.argv[1])/160
print(f'{round(int(sys.argv[2])*scale)}x{round(800*scale)}')
PYDIM
)
  adb shell wm size "$dimensions"
  sleep 3
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml "smoke/deck-resize-$width.xml" >/dev/null
  python3 - "$width" <<'PYRESIZE'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/deck-resize-'+sys.argv[1]+'.xml').iter('node'))
labels={n.attrib.get('text','') for n in nodes}
assert {'Construire un deck','Enregistrer','Exporter','Préparer'} <= labels, labels
assert {'Leader','Base','Deck','Réserve'} <= labels, labels
PYRESIZE
  adb exec-out screencap -p > "smoke/deck-width-$width.png"
  if [ "$width" = 320 ]; then
    tap_label "Rechercher une carte"
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/deck-keyboard.xml >/dev/null
    python3 - <<'PYKEYBOARD'
import xml.etree.ElementTree as ET
assert any(n.attrib.get('text')=='Enregistrer' and n.attrib.get('bounds')!='[0,0][0,0]' for n in ET.parse('smoke/deck-keyboard.xml').iter('node'))
PYKEYBOARD
    adb exec-out screencap -p > smoke/deck-keyboard.png
    adb shell input keyevent 4
  fi
done
compact=$(python3 -c "import sys; s=int(sys.argv[1])/160; print(str(round(320*s))+'x'+str(round(800*s)))" "$density")
adb shell wm size "$compact"

adb shell settings put system font_scale 1.5
sleep 3
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/deck-large-font.xml >/dev/null
python3 - <<'PYFONT'
import re,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/deck-large-font.xml').iter('node'))
for label in ['Leader','Base','Deck','Réserve','Enregistrer','Exporter','Préparer']:
    matches=[n for n in nodes if n.attrib.get('text')==label]
    assert matches,label
    assert any((lambda b:b[2]>b[0] and b[3]>b[1])(list(map(int,re.findall(r'\d+',n.attrib['bounds'])))) for n in matches),label
PYFONT
adb exec-out screencap -p > smoke/deck-large-font.png
adb shell settings put system font_scale 1.0
adb shell wm size reset
sleep 3
tap_label "Enregistrer"
tap_label "☆ Épingler"
tap_label "Accueil"
tap_label "Mon deck"
tap_label "Enregistrer"
tap_label "Accueil"
tap_label "Carte, deck, extension…"
tap_label "Carte, deck ou extension"
adb shell input text Krennic
adb shell input keyevent 4
sleep 2
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/global-search.xml >/dev/null
python3 - <<'PYGLOBAL'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/global-search.xml').iter('node'))
assert 'Mon deck' in text and 'possédée(s)' in text, text
PYGLOBAL
# Add a searched card through its sheet, using the explicit zone/identity confirmation.
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/global-card-hit.xml >/dev/null
read -r card_x card_y < <(python3 - <<'PYCARDHIT'
import re,xml.etree.ElementTree as ET
nodes=[n for n in ET.parse('smoke/global-card-hit.xml').iter('node') if 'krennic' in n.attrib.get('text','').casefold() and n.attrib.get('class')!='android.widget.EditText']
assert nodes,'No searched card row'
x1,y1,x2,y2=map(int,re.findall(r'\d+',nodes[0].attrib['bounds']))
print((x1+x2)//2,(y1+y2)//2)
PYCARDHIT
)
adb shell input tap "$card_x" "$card_y"
sleep 2
tap_label "Ajouter à un deck"
tap_label "Mon deck"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/card-add-confirmation.xml >/dev/null
action=$(python3 - <<'PYADDACTION'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/card-add-confirmation.xml').iter('node')}
print(next(label for label in ['Ajouter un exemplaire','Confirmer le remplacement'] if label in texts))
PYADDACTION
)
tap_label "$action"
adb exec-out screencap -p > smoke/card-deck-usage.png
tap_description "Fermer la carte"
tap_label "Mon deck"
tap_label "Enregistrer"
tap_label "Accueil"
tap_description "Ouvrir le menu"
tap_label "Paramètres"
tap_label "Hors connexion  ›"
tap_description "Choisir les images hors connexion"
tap_label "Deck · Mon deck"
tap_label "Télécharger les images"
for attempt in $(seq 1 30); do
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml smoke/offline-images.xml >/dev/null
  if python3 - <<'PYSTORED'
import sys,xml.etree.ElementTree as ET
texts=[n.attrib.get('text','') for n in ET.parse('smoke/offline-images.xml').iter('node')]
sys.exit(0 if 'Images prêtes hors connexion.' in texts else 1)
PYSTORED
  then break; fi
  sleep 2
done
python3 - <<'PYREADY'
import xml.etree.ElementTree as ET
assert any(n.attrib.get('text')=='Images prêtes hors connexion.' for n in ET.parse('smoke/offline-images.xml').iter('node'))
PYREADY
adb exec-out screencap -p > smoke/offline-images.png
adb shell cmd connectivity airplane-mode enable
adb shell svc wifi disable
adb shell svc data disable
sleep 3
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/offline-status.xml >/dev/null
python3 - <<'PYOFFLINE'
import xml.etree.ElementTree as ET
assert not any(n.attrib.get('text')=='Connexion Internet disponible' for n in ET.parse('smoke/offline-status.xml').iter('node'))
PYOFFLINE
tap_label "Mes listes"
tap_label "Préparer"
tap_label "Imprimer les proxies manquants"
adb shell settings put system font_scale 1.5
sleep 3
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/proxies-large-font.xml >/dev/null
python3 - <<'PYPROXYFONT'
import re,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/proxies-large-font.xml').iter('node'))
buttons=[n for n in nodes if n.attrib.get('text')=='Créer le PDF']
assert buttons,('Missing PDF action with enlarged text', [n.attrib.get('text','') for n in nodes if n.attrib.get('text')])
assert any((lambda b:b[2]>b[0] and b[3]>b[1])(list(map(int,re.findall(r'\d+',n.attrib['bounds'])))) for n in buttons)
PYPROXYFONT
adb exec-out screencap -p > smoke/proxies-large-font.png
adb shell settings put system font_scale 1.0
sleep 3
adb shell rm -f /sdcard/Download/SWU-Proxies.pdf
tap_label "Créer le PDF"
tap_label "Créer le PDF"
wait_for_pdf_preview
adb exec-out screencap -p > smoke/offline-proxy-preview.png
wait_for_pdf_save
pull_file /sdcard/Download/SWU-Proxies.pdf smoke/SWU-Proxies-offline.pdf
python3 - <<'PYOFFLINEPDF'
from pathlib import Path
data=Path('smoke/SWU-Proxies-offline.pdf').read_bytes()
assert data.startswith(b'%PDF-') and len(data)>2000
assert b'/Subtype /Image' in data or b'/Subtype/Image' in data
PYOFFLINEPDF
tap_label "Fermer l’aperçu"
tap_label "Fermer"
tap_label "Retour"
tap_label "Fermer"
adb shell cmd connectivity airplane-mode disable
adb shell svc wifi enable
adb shell svc data enable

# Finishing pass: scanner remains usable when the phone is folded or rotated.
adb shell pm grant "$package" android.permission.CAMERA
tap_label "Accueil"
tap_description "Ouvrir le menu"
tap_label "Scanner"
sleep 4
# Destination stays explicit and cancelling the picker keeps the collection scanner.
tap_label "Choisir un deck…"
tap_label "Rester sur la collection"
# Enter the same camera through a saved deck, then return to collection.
tap_label "Choisir un deck…"
tap_label "Mon deck"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/scanner-deck-destination.xml >/dev/null
python3 - <<'PYSCANDESTINATION'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/scanner-deck-destination.xml').iter('node')}
assert 'Ajouter à « Mon deck »' in texts and 'Terminer' in texts,texts
PYSCANDESTINATION
tap_label "Ajouter à ma collection plutôt"
tap_label "Terminer"
tap_label "Enregistrer"
tap_label "Accueil"
tap_description "Ouvrir le menu"
tap_label "Scanner"
# The remaining checks exercise individual card confirmation deliberately.
tap_label "Une par une"
for dimensions in 320x800 720x400; do
  physical=$(python3 - "$density" "$dimensions" <<'PYSCANSIZE'
import sys
w,h=map(int,sys.argv[2].split('x'));scale=int(sys.argv[1])/160
print(f'{round(w*scale)}x{round(h*scale)}')
PYSCANSIZE
)
  adb shell wm size "$physical"
  sleep 4
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml "smoke/scanner-$dimensions.xml" >/dev/null
  python3 - "$dimensions" <<'PYSCANLAYOUT'
import re,sys,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/scanner-'+sys.argv[1]+'.xml').iter('node'))
for label in ['Relancer la lecture','Saisir une référence']:
    visible=[n for n in nodes if n.attrib.get('text')==label]
    assert visible, label
    assert any((lambda b:b[2]>b[0] and b[3]>b[1])(list(map(int,re.findall(r'\d+',n.attrib['bounds'])))) for n in visible),label
assert any(n.attrib.get('text')=='Terminer' for n in nodes)
PYSCANLAYOUT
  adb exec-out screencap -p > "smoke/scanner-$dimensions.png"
done
adb shell wm size reset
sleep 3
tap_label "Saisir une référence"
tap_label "Extension"
adb shell input text JTL
tap_label "Numéro"
adb shell input text 788
adb shell input keyevent 4
tap_label "Rechercher"
# The variant summary and confirmation can occupy separate viewports on 320dp.
# Capture the unchanged pending card while revealing its confirmation buttons.
capture_scanner_state smoke/scanner-variant.xml "JTL 788" "Hyperspace Foil" "Cote" "Valider · Ajouter à ma collection" "Annuler"
python3 - <<'PYSCANVARIANT'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/scanner-variant.xml').iter('node'))
assert 'JTL' in text and '788' in text and 'Hyperspace Foil' in text and 'Valider · Ajouter à ma collection' in text,text
PYSCANVARIANT
adb exec-out screencap -p > smoke/scanner-variant.png
tap_label "Terminer"
# Export a verified portable file through the real Android document provider.
tap_label "Accueil"
tap_description "Ouvrir le menu"
tap_label "Paramètres"
tap_label "Sauvegarder et restaurer  ›"
tap_label "Sauvegarder collection, listes et decks"
wait_for_pdf_save
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/backup-verified.xml >/dev/null
python3 - <<'PYBACKUPVERIFIED'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/backup-verified.xml').iter('node'))
assert 'Sauvegarde enregistrée et vérifiée' in text,text
PYBACKUPVERIFIED
adb exec-out screencap -p > smoke/backup-verified.png

# Verify new rewards on the actual optimized application.
KEEP_BADGES=1
tap_label "Accueil"
sleep 3
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/badge-celebration.xml >/dev/null
python3 - <<'PYBADGEREVEAL'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/badge-celebration.xml').iter('node'))
assert 'débloqué' in text and 'Continuer' in text and 'Voir mon badge' in text,text
PYBADGEREVEAL
adb exec-out screencap -p > smoke/badge-celebration.png
tap_label "Continuer"
KEEP_BADGES=0
tap_description "Ouvrir le menu"
tap_label "Badges"
sleep 3
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/badge-gallery.xml >/dev/null
python3 - <<'PYBADGEGALLERY'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/badge-gallery.xml').iter('node'))
assert 'Ta légende se construit' in text and 'Sagas' in text and 'Rechercher un haut fait' in text,text
PYBADGEGALLERY
adb exec-out screencap -p > smoke/badge-gallery.png
tap_label "Obtenus"
tap_label "Rechercher un haut fait"
adb shell input text Memoire
adb shell input keyevent 4
tap_label "Mémoire sauvegardée"
# The detail is deliberately scrollable; check its permanent-award explanation
# after reaching it, including on the compact emulator display.
for attempt in 1 2 3 4 5 6 7 8; do
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/badge-earned-detail.xml >/dev/null
if python3 - <<'PYBADGEDETAIL'
import sys
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/badge-earned-detail.xml').iter('node'))
sys.exit(0 if 'acquis définitivement' in text else 1)
PYBADGEDETAIL
then break; fi
adb shell input swipe 160 480 160 240 350
done
python3 - <<'PYBADGEDETAILCHECK'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/badge-earned-detail.xml').iter('node'))
assert 'acquis définitivement' in text,text
PYBADGEDETAILCHECK
adb exec-out screencap -p > smoke/badge-earned-detail.png
tap_description "Fermer le haut fait"
tap_description "Effacer la recherche"
tap_label "En cours"
tap_label "Rechercher un haut fait"
adb shell input text Dagobah
adb shell input keyevent 4
tap_label "La leçon de Dagobah"
adb exec-out screencap -p > smoke/badge-dagobah.png
tap_label "Voir les cartes acceptées"
adb exec-out screencap -p > smoke/badge-accepted-cards.png
tap_label "Fermer"
tap_description "Fermer le haut fait"
tap_description "Effacer la recherche"
# Regression: mixed portrait/landscape faces, bases and Showcase leaders in real pins.
search_badge() {
  local badge_query_file="smoke/badge-query-${1// /-}.xml"
  tap_label "Rechercher un haut fait"
  adb shell input text "${1// /%s}"
  adb shell input keyevent 4
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml "$badge_query_file" >/dev/null
  python3 - "$1" "$badge_query_file" <<'PYBADGEQUERY'
import sys,xml.etree.ElementTree as ET
nodes=list(ET.parse(sys.argv[2]).iter('node'))
assert any(n.attrib.get('class')=='android.widget.EditText' and n.attrib.get('text')==sys.argv[1] for n in nodes),[(n.attrib.get('text'),n.attrib.get('focused')) for n in nodes if n.attrib.get('class')=='android.widget.EditText']
PYBADGEQUERY
}
search_badge "Famille reunie"
tap_label "Famille réunie"
sleep 2
adb exec-out screencap -p > smoke/badge-crop-family.png
tap_description "Fermer le haut fait"
tap_description "Effacer la recherche"
search_badge "Une nouvelle generation"
tap_label "Une nouvelle génération"
sleep 2
adb exec-out screencap -p > smoke/badge-crop-showcase.png
tap_description "Fermer le haut fait"
tap_description "Effacer la recherche"
search_badge "La bataille de Hoth"
tap_label "La bataille de Hoth"
sleep 2
adb exec-out screencap -p > smoke/badge-crop-base.png
tap_description "Fermer le haut fait"
tap_description "Effacer la recherche"
tap_label "Sagas"
adb exec-out screencap -p > smoke/badge-sagas.png
# State survives a font-metric change and remains usable with large Android text.
adb shell settings put system font_scale 1.5
sleep 3
adb exec-out screencap -p > smoke/badge-large-font.png
adb shell settings put system font_scale 1.0
adb shell wm size 800x400
sleep 3
adb shell input swipe 600 290 600 110 400
adb exec-out screencap -p > smoke/badge-landscape.png
adb shell wm size reset
adb shell am force-stop "$package"
adb shell am start -W -n "$package/.MainActivity" >/dev/null
sleep 8
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/badge-seen-restart.xml >/dev/null
python3 - <<'PYBADGESEEN'
import xml.etree.ElementTree as ET
text=' '.join(n.attrib.get('text','') for n in ET.parse('smoke/badge-seen-restart.xml').iter('node'))
assert 'débloqué' not in text,text
PYBADGESEEN

# The same physical printing in two languages must produce two owned rows and matching images.
tap_label "Accueil"
tap_description "Ouvrir le menu"
tap_label "Scanner"
for language in Français Anglais Allemand Espagnol Italien; do
  tap_label "Saisir une référence"
  tap_label "Extension"
  adb shell input text SOR
  tap_label "Numéro"
  adb shell input text 236
  if [ "$language" = Français ]; then
    dump_window >/dev/null
    pull_file /sdcard/swu-window.xml smoke/scanner-language-keyboard.xml >/dev/null
    python3 - <<'PYLANGUAGEKEYBOARD'
import re,xml.etree.ElementTree as ET
nodes=list(ET.parse('smoke/scanner-language-keyboard.xml').iter('node'))
assert any(n.attrib.get('text')=='Rechercher' and (lambda b:b[2]>b[0] and b[3]>b[1])(list(map(int,re.findall(r'\d+',n.attrib['bounds'])))) for n in nodes)
PYLANGUAGEKEYBOARD
    adb exec-out screencap -p > smoke/scanner-language-keyboard.png
  fi
  adb shell input keyevent 4
  if [ "$language" != Français ]; then
    tap_label "Français"
    tap_label "$language"
  fi
  tap_label "Rechercher"
  tap_label "Normale"
  sleep 3
  adb exec-out screencap -p > "smoke/scanner-language-$language.png"
  tap_label "Valider · Ajouter à ma collection"
done
tap_label "Terminer"
tap_label "Collection"
tap_label "Nom, extension ou numéro"
adb shell input text R2-D2
adb shell input keyevent 4
sleep 2
# Verify the compact language filter independently of the extension selector.
tap_label "Filtres"
tap_label "Langue : Toutes"
adb exec-out screencap -p > smoke/collection-language-filter-menu.png
tap_label "Toutes"
adb exec-out screencap -p > smoke/collection-language-filters.png
tap_label "Voir les cartes"
# Read actual owned rows, never the filter menu; stop when all five are visible
# across the captures. The bound also covers compact screens and wrapped text.
for step in {0..11}; do
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml "smoke/collection-languages-$step.xml" >/dev/null
  adb exec-out screencap -p > "smoke/collection-languages-$step.png"
  if python3 - <<'PYLANGUAGESVISIBLE'
from pathlib import Path
import sys,xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for file in Path('smoke').glob('collection-languages-*.xml') for n in ET.parse(file).iter('node')}
sys.exit(0 if {'Français','Anglais','Allemand','Espagnol','Italien'} <= texts else 1)
PYLANGUAGESVISIBLE
  then break; fi
  # Stay in the card gutter, clear of locale and quantity buttons.
  adb shell input swipe 20 480 20 300 600
done
python3 - <<'PYLANGUAGES'
from pathlib import Path
import xml.etree.ElementTree as ET
texts=set()
for file in Path('smoke').glob('collection-languages-*.xml'):
    frame_texts={n.attrib.get('text','') for n in ET.parse(file).iter('node')}
    assert any('références et variantes possédées' in t and '5 exemplaires' in t for t in frame_texts),(file,frame_texts)
    texts.update(frame_texts)
assert {'Français','Anglais','Allemand','Espagnol','Italien'} <= texts,texts
PYLANGUAGES

tap_label "Accueil"
competitive_scroll() {
  dump_window >/dev/null
  pull_file /sdcard/swu-window.xml smoke/competitive-scroll.xml >/dev/null
  read -r sx sy ex ey < <(python3 - "$1" <<'PYHUBSCROLL'
import re,sys,xml.etree.ElementTree as ET
nodes=[n for n in ET.parse('smoke/competitive-scroll.xml').iter('node') if n.attrib.get('scrollable')=='true']
assert nodes, 'Missing competitive list viewport'
def bounds(n):return list(map(int,re.findall(r'\d+',n.attrib['bounds'])))
x1,y1,x2,y2=max((bounds(n) for n in nodes),key=lambda b:(b[2]-b[0])*(b[3]-b[1]))
x=x1+min(12,(x2-x1)//10)
a=y1+(y2-y1)//4;b=y1+(y2-y1)*3//4
print(x,b,x,a) if sys.argv[1]=='down' else print(x,a,x,b)
PYHUBSCROLL
)
  adb shell input swipe "$sx" "$sy" "$ex" "$ey" 600
  sleep 1
}
# Verify the new native hub and a real copied list on the optimized APK.
tap_label "Tournois & Méta"
tap_label "Méta"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/competitive-meta.xml >/dev/null
python3 - <<'PYMETAHUB'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/competitive-meta.xml').iter('node')}
assert 'Les decks du moment' in texts and any('listes compatibles' in t for t in texts),texts
PYMETAHUB
adb exec-out screencap -p > smoke/competitive-meta.png
competitive_scroll down
tap_label "Copier"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/competitive-native-copy.xml >/dev/null
python3 - <<'PYNATIVECOPY'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/competitive-native-copy.xml').iter('node')}
assert 'Deck copié · leader, base et réserve compris.' in texts,texts
PYNATIVECOPY
tap_label "Enregistrer"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/competitive-native-save.xml >/dev/null
python3 - <<'PYNATIVESAVE'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/competitive-native-save.xml').iter('node')}
assert 'Deck enregistré dans Mes decks.' in texts,texts
PYNATIVESAVE
adb exec-out screencap -p > smoke/competitive-native-save.png
tap_label "Mes decks"
tap_label "Importer une liste"
tap_label "JSON ou texte du deck"
python3 - <<'PYIMPORTCOMP'
import json,shlex,subprocess
raw=json.dumps({'metadata':{'name':'Tournoi test'},'leader':{'id':'JTL_001','count':1},'base':{'id':'JTL_024','count':1},'deck':[{'id':'JTL_095','count':3}],'sideboard':[{'id':'JTL_096','count':1}]},separators=(',',':'))
subprocess.run(['adb','shell','input','text',shlex.quote(raw.replace(' ','%s'))],check=True)
PYIMPORTCOMP
adb shell input keyevent 4
tap_label "Ouvrir le deck"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/competitive-summary.xml >/dev/null
python3 - <<'PYSUMMARYHUB'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/competitive-summary.xml').iter('node')}
assert '6 exemplaire(s) à trouver sur 6' in texts,texts
PYSUMMARYHUB
tap_label "Manquantes"
competitive_scroll down
sleep 1
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/competitive-missing.xml >/dev/null
python3 - <<'PYMISSHUB'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/competitive-missing.xml').iter('node')}
assert any('3 à trouver' in t for t in texts),texts
PYMISSHUB
adb exec-out screencap -p > smoke/competitive-missing.png
for step in 1 2 3 4; do competitive_scroll up; done
tap_label "Enregistrer dans Mes decks"
tap_label "Copier le deck"
tap_label "Ouvrir dans Mes decks"
dump_window >/dev/null
pull_file /sdcard/swu-window.xml smoke/competitive-saved.xml >/dev/null
python3 - <<'PYSAVEDHUB'
import xml.etree.ElementTree as ET
texts={n.attrib.get('text','') for n in ET.parse('smoke/competitive-saved.xml').iter('node')}
assert 'Tournoi test' in texts,texts
PYSAVEDHUB
adb shell input keyevent 4
sleep 2
tap_label "Accueil"

adb logcat -d -s AndroidRuntime:E > smoke/crashes.txt
python3 - <<'PYCRASH'
from pathlib import Path
crashes = Path('smoke/crashes.txt').read_text(encoding='utf-8', errors='replace')
if 'FATAL EXCEPTION' in crashes:
    print(crashes)
    raise SystemExit(1)
PYCRASH



