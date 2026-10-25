"""Reject ephemeral or unexpected debug keys before publishing CI artifacts."""
import re
import sys
from pathlib import Path

expected = '478027445899b9793ad274d32a41cc2f29c489e50bddec5ad8facb5cdd2393f4'
certificates = re.findall(r'Signer #\d+ certificate SHA-256 digest:\s*([0-9a-fA-F]+)', Path(sys.argv[1]).read_text())
if [value.lower() for value in certificates] != [expected]:
    raise SystemExit('Wrong test APK identity; refusing to distribute an incompatible build')
print('PASS fixed test APK identity')
