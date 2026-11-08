"""Reject a missing or misrouted official configuration before signing or publishing."""
from pathlib import Path
import xml.etree.ElementTree as ET

def verify(path):
    root = ET.parse(path).getroot()
    values = {row.attrib.get('name'): row.text or '' for row in root.findall('string')}
    if values.get('swu_official_project_id') != 'swu-collection-official':
        raise ValueError('The official app must use its dedicated official Firebase project.')
    if not values.get('swu_official_api_key', '').strip() or not values.get('swu_official_app_id', '').strip():
        raise ValueError('The official backend configuration is incomplete.')
    if any(name.startswith('swu_test_') for name in values):
        raise ValueError('Test resources must not be injected into the official source set.')

if __name__ == '__main__':
    verify(Path('app/src/release/res/values/community_config.xml'))
    print('Dedicated official backend configuration verified.')
