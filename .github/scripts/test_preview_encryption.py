import base64
from pathlib import Path
import tempfile
import unittest
from cryptography.exceptions import InvalidTag
from cryptography.hazmat.primitives import hashes,serialization
from cryptography.hazmat.primitives.asymmetric import rsa,padding
from cryptography.hazmat.primitives.ciphers.aead import AESGCM
from encrypt_private_preview import encrypt

class PreviewEncryptionTests(unittest.TestCase):
    def test_official_candidate_remains_private_and_is_bound_to_the_official_package(self):
        private=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        with tempfile.TemporaryDirectory() as d:
            root=Path(d);pub=root/'public.pem';apk=root/'candidate.apk';out=root/'encrypted'
            pub.write_bytes(private.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
            apk.write_bytes(b'official candidate fixture')
            envelope=encrypt(pub,apk,out,'fr.swucollection.app')
            self.assertEqual('fr.swucollection.app',envelope['package'])
            key=private.decrypt(base64.b64decode(envelope['wrapped_key']),padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
            nonce=base64.b64decode(envelope['nonce']);ciphertext=(out/'SWU-Collection-Candidate.aesgcm').read_bytes()
            self.assertEqual(apk.read_bytes(),AESGCM(key).decrypt(nonce,ciphertext,b'fr.swucollection.app'))
            with self.assertRaises(InvalidTag): AESGCM(key).decrypt(nonce,ciphertext,b'fr.swucollection.app.test')
            self.assertFalse(any(p.suffix=='.apk' for p in out.iterdir()))
            with self.assertRaises(ValueError): encrypt(pub,apk,out,'other.app')
    def test_only_recipient_key_recovers_apk_and_tampering_is_rejected(self):
        private=rsa.generate_private_key(public_exponent=65537,key_size=2048)
        with tempfile.TemporaryDirectory() as d:
            root=Path(d); pub=root/'public.pem'; apk=root/'test.apk'; out=root/'encrypted'
            pub.write_bytes(private.public_key().public_bytes(serialization.Encoding.PEM,serialization.PublicFormat.SubjectPublicKeyInfo))
            apk.write_bytes(b'private APK fixture'*100)
            envelope=encrypt(pub,apk,out)
            key=private.decrypt(base64.b64decode(envelope['wrapped_key']),padding.OAEP(mgf=padding.MGF1(hashes.SHA256()),algorithm=hashes.SHA256(),label=None))
            nonce=base64.b64decode(envelope['nonce']); ciphertext=(out/'SWU-Collection-Test.aesgcm').read_bytes()
            self.assertEqual(AESGCM(key).decrypt(nonce,ciphertext,b'fr.swucollection.app.test'),apk.read_bytes())
            damaged=ciphertext[:-1]+bytes([ciphertext[-1]^1])
            with self.assertRaises(InvalidTag): AESGCM(key).decrypt(nonce,damaged,b'fr.swucollection.app.test')
            self.assertFalse(any('PRIVATE KEY' in p.read_text(errors='ignore') for p in out.iterdir()))
