#!/usr/bin/env python3
"""Export the public VG catalog to an existing website, without backend code or secrets."""
from pathlib import Path
import argparse
import shutil
parser=argparse.ArgumentParser()
parser.add_argument('--destination',required=True,type=Path)
args=parser.parse_args()
root=Path(__file__).resolve().parents[1]
dest=args.destination.resolve()
dest.mkdir(parents=True,exist_ok=True)
shutil.copy2(root/'catalogo-nuovo.html',dest/'index.html')
# Keep the complete public asset set in step with the catalogue HTML.
# No management entry, database code or credential files are exported.
public_types={'.js','.css','.svg','.png','.jpg','.jpeg','.webp','.woff','.woff2'}
for source in sorted(root.glob('shop-*')):
    if source.is_dir():
        if source.name in {'shop-icons','shop-data'}:
            shutil.copytree(source,dest/source.name,dirs_exist_ok=True)
    elif source.suffix.lower() in public_types:
        shutil.copy2(source,dest/source.name)
for name in ['customer-profile-ui.js','vg-save-feedback.js','vg-push.js','vg-push-sw.js','supabaseClient.js','admin-notifiche.html']:
    shutil.copy2(root/name,dest/name)
print(f'Exported public catalog to {dest}')
