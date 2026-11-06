"""Encrypt a tested APK to a recipient key. The private key never enters CI."""
import base64
import hashlib
import json
import os
import sys
from pathlib import Path
from cryptography.hazmat.primitives import hashes, serialization
from cryptography.hazmat.primitives.asymmetric import padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM


def encrypt(public_key_path, apk_path, output):
    output = Path(output)
    output.mkdir(parents=True, exist_ok=True)
    data = Path(apk_path).read_bytes()
    key = AESGCM.generate_key(bit_length=256)
    nonce = os.urandom(12)
    associated = b'fr.swucollection.app.test'
    encrypted = AESGCM(key).encrypt(nonce, data, associated)
    public = serialization.load_pem_public_key(Path(public_key_path).read_bytes())
    wrapped = public.encrypt(key, padding.OAEP(mgf=padding.MGF1(hashes.SHA256()), algorithm=hashes.SHA256(), label=None))
    (output/'SWU-Collection-Test.aesgcm').write_bytes(encrypted)
    metadata = {'format':1, 'package':'fr.swucollection.app.test',
                'source_sha':os.environ.get('GITHUB_SHA','local-test'),
                'apk_sha256':hashlib.sha256(data).hexdigest(), 'apk_bytes':len(data),
                'nonce':base64.b64encode(nonce).decode(),
                'wrapped_key':base64.b64encode(wrapped).decode()}
    (output/'envelope.json').write_text(json.dumps(metadata, indent=2)+'\n')
    return metadata

if __name__ == '__main__':
    encrypt(*sys.argv[1:])
