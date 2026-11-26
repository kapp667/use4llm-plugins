"""Rewrite the machine's paths in summary TSVs as paths relative to their corpus root.

Usage: python3 -I relpaths.py <file.tsv>...   (in place)
USE_HOME/examples/ and USE_SRC/ become nothing, the probes directory becomes probes/, and a case
id built from an absolute probe path loses that path.
"""
import os
import re
import sys

here = os.path.dirname(os.path.dirname(os.path.abspath(__file__)))
roots = [(os.path.join(os.environ["USE_HOME"], "examples") + "/", ""), (os.environ["USE_SRC"].rstrip("/") + "/", ""),
         (os.path.join(here, "probes") + "/", "probes/")]
for path in sys.argv[1:]:
    text = open(path).read()
    for root, rel in roots:
        text = text.replace(root, rel)
    text = re.sub(r"\b([WV])__[A-Za-z0-9._-]*?_translator_probes_", r"\1__probes_", text)
    open(path, "w").write(text)
