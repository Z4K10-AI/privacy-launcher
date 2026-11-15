#!/usr/bin/env python3
"""Checks that every link in the project's Markdown files goes somewhere.

    tools/checklinks.py

For each [text](target) in README.md, CHANGELOG.md, the files under docs/,
licenses/ and the top-level guides: a file target must exist, and an
anchor (#section) must match a heading in the file it points into, using
GitHub's way of turning a heading into an anchor. Web links are not
followed; this runs offline.
"""
import glob
import os
import re
import sys

def slug(heading):
    s = heading.strip().lower()
    s = re.sub(r"[`*_]", "", s)
    s = re.sub(r"[^\w\- ]", "", s)
    return s.replace(" ", "-")

def anchors(path):
    found, inside_code = set(), False
    for line in open(path, encoding="utf-8"):
        if line.startswith("```"):
            inside_code = not inside_code
            continue
        m = None if inside_code else re.match(r"^#{1,6} (.*)$", line)
        if m:
            found.add(slug(m.group(1)))
    return found

files = ["README.md", "CHANGELOG.md", "CONTRIBUTING.md", "SECURITY.md", "CODE_OF_CONDUCT.md"]
files += sorted(glob.glob("docs/*.md")) + sorted(glob.glob("licenses/*.md"))
files = [f for f in files if os.path.exists(f)]
bad = 0
for path in files:
    text = open(path, encoding="utf-8").read()
    text = re.sub(r"```.*?```", "", text, flags=re.S)
    for m in re.finditer(r"\[[^\]]+\]\(([^)\s]+)\)", text):
        target = m.group(1)
        if re.match(r"^[a-z]+:", target):
            continue
        file_part, _, anchor = target.partition("#")
        dest = os.path.normpath(os.path.join(os.path.dirname(path), file_part)) if file_part else path
        if not os.path.exists(dest):
            print(f"{path}: no such file: {target}")
            bad += 1
            continue
        if anchor and dest.endswith(".md") and anchor not in anchors(dest):
            print(f"{path}: no heading for #{anchor} in {dest}")
            bad += 1
print(f"{len(files)} files checked, {bad} broken links")
sys.exit(1 if bad else 0)
