"""Sign VG delivery APKs with the owner's persistent PRIVATE identity.

The private config is held outside Git as VG-Android-signing-private.json.
Never deliver the CI test-signed artifact directly to a user.
"""
import argparse
import base64
import hashlib
import json
import os
from pathlib import Path
import subprocess
import tempfile

parser = argparse.ArgumentParser(description=__doc__)
parser.add_argument('--config', required=True, type=Path)
parser.add_argument('--input', required=True, type=Path)
parser.add_argument('--output', required=True, type=Path)
parser.add_argument('--apksig-jar', required=True, type=Path)
args = parser.parse_args()
if hashlib.sha256(args.apksig_jar.read_bytes()).hexdigest() != 'c070ed1394629d74641aa0906f60b2ffa1ee77e6366a1f93437f59717b1aeb89':
    raise SystemExit('Unexpected apksig library; use the pinned Google Maven 8.13.2 artifact')
config = json.loads(args.config.read_text())
identity = json.loads(Path(__file__).with_name('delivery-identity.json').read_text())
if config.get('packageName') != 'com.vg.gestionale.seconda':
    raise SystemExit('Private signing config is for a different application')
if config.get('certificateSha256') != identity['certificateSha256']:
    raise SystemExit('This is not the pinned VG delivery identity; do not generate or rotate keys silently')
if args.input.resolve() == args.output.resolve():
    raise SystemExit('Refusing to overwrite the input APK')
fingerprint = config['certificateSha256']
if len(fingerprint) != 64 or any(c not in '0123456789abcdef' for c in fingerprint):
    raise SystemExit('Invalid delivery identity fingerprint')
with tempfile.TemporaryDirectory(prefix='vg-signing-') as folder:
    key = Path(folder) / 'private.p12'
    key.write_bytes(base64.b64decode(config['keystoreBase64'], validate=True))
    key.chmod(0o600)
    env = dict(os.environ, VG_DELIVERY_PASSWORD=config['password'])
    subprocess.run(['java', '--class-path', str(args.apksig_jar.resolve()),
        str(Path(__file__).with_name('VgDeliverySigner.java').resolve()),
        str(args.input.resolve()), str(args.output.resolve()), str(key),
        config['alias'], fingerprint], env=env, check=True)
