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
for name in ['shop-catalog.css','shop-parallelo-logo.svg','shop-hero.webp','shop-scarpe.webp','shop-accessori.webp']:
    shutil.copy2(root/name,dest/name)
shutil.copytree(root/'shop-icons',dest/'shop-icons',dirs_exist_ok=True)
print(f'Exported public catalog to {dest}')
