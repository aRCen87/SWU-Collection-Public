"""Verified dates shared by the Android fallback and future catalogue builds."""
import json
from datetime import date
from pathlib import Path
ASSET = Path(__file__).resolve().parents[2] / 'app/src/main/assets/set-release-dates.json'

def valid_date(value):
    if not isinstance(value,str) or len(value)!=10:return ''
    try:
        parsed=date.fromisoformat(value)
        return value if parsed.isoformat()==value else ''
    except ValueError:return ''

def load_release_dates(path=ASSET):
    root=json.loads(Path(path).read_text(encoding='utf-8'))
    assert root['format']=='SWU_SET_RELEASE_DATES' and root['version']==1
    dates={code:valid_date(item['date']) for code,item in root['sets'].items()}
    assert all(dates.values()), 'Invalid verified release date'
    return dates

def release_date_for(code,verified,upstream='',previous=''):
    return valid_date(verified.get(code,'')) or valid_date(upstream) or valid_date(previous)
