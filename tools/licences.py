#!/usr/bin/env python3
"""Lists every library inside the release app, with its licence.

    tools/licences.py           check, and print a summary
    tools/licences.py --write   also rewrite docs/dependencies.md

The README lists the libraries the app asks for by name. Each of those
brings others with it, and those end up in the app too. This asks Gradle
for the full list that goes into the release build and reads each
library's licence from the description it is published with (its POM
file, or its parent's).

It fails, and says which, if any library is under a licence not on the
list below. Every one so far is Apache 2.0 except one, Google's protocol
buffers library, repackaged by AndroidX DataStore, which is BSD-3-Clause.
Both sit happily inside an Apache-2.0 app. A new licence is not
necessarily a problem, but it must be looked at by a person before it
ships, which is what the failure is for.

Run it from the project folder, after a build has downloaded the
libraries. It reads Gradle's local cache and does not go online.
"""
import glob
import os
import re
import subprocess
import sys
from collections import Counter
from xml.etree import ElementTree as ET

# Each licence, and the ways publishers spell its name. A name is matched
# after lower-casing and dropping a leading "the", so "The Apache Software
# License, Version 2.0" and "Apache-2.0" both count as Apache 2.0.
ALLOWED = {
    "Apache-2.0": ["apache software license, version 2.0", "apache license, version 2.0",
                   "apache license 2.0", "apache 2.0", "apache-2.0"],
    "BSD-3-Clause": ["bsd-3-clause", "bsd 3-clause", "bsd 3-clause license", "new bsd license"],
}
OUT = os.path.join("docs", "dependencies.md")
CACHE = os.path.expanduser("~/.gradle/caches/modules-2/files-2.1")
NS = "{http://maven.apache.org/POM/4.0.0}"


def release_libraries():
    gradle = "./gradlew.bat" if os.name == "nt" else "./gradlew"
    tree = subprocess.run(
        [gradle, ":app:dependencies", "--configuration", "releaseRuntimeClasspath", "-q"],
        capture_output=True, text=True, check=True,
    ).stdout
    found = set()
    for line in tree.splitlines():
        m = re.search(r"[+\\]--- ([^\s:]+):([^\s:]+):?(\S*)(?: -> (\S+))?", line)
        if not m or m.group(1) == "project":
            continue
        version = (m.group(4) or m.group(3)).split(" ")[0]
        if version and not version.startswith("{"):
            found.add((m.group(1), m.group(2), version))
    return sorted(found)


def licence_names(group, artifact, version, depth=0):
    poms = glob.glob(f"{CACHE}/{group}/{artifact}/{version}/*/{artifact}-{version}.pom")
    if not poms or depth > 4:
        return []
    root = ET.parse(poms[0]).getroot()
    names = [(l.findtext(f"{NS}name") or "").strip() for l in root.iter(f"{NS}license")]
    if names:
        return names
    parent = root.find(f"{NS}parent")
    if parent is None:
        return []
    return licence_names(parent.findtext(f"{NS}groupId"), parent.findtext(f"{NS}artifactId"),
                         parent.findtext(f"{NS}version"), depth + 1)


def classify(names):
    def plain(name):
        name = name.strip().lower()
        return name[4:] if name.startswith("the ") else name
    for spdx, spellings in ALLOWED.items():
        if any(plain(n) in spellings for n in names):
            return spdx
    return None


def main(argv):
    rows, problems = [], []
    for group, artifact, version in release_libraries():
        names = licence_names(group, artifact, version)
        spdx = classify(names)
        if spdx is None:
            problems.append(f"{group}:{artifact}:{version}  {names or 'no licence found'}")
        rows.append((group, artifact, version, spdx or ", ".join(names) or "unknown"))

    counts = Counter(r[3] for r in rows)
    print(f"{len(rows)} libraries in the release app: " +
          ", ".join(f"{n} {spdx}" for spdx, n in counts.most_common()))

    if "--write" in argv:
        lines = [
            "# Libraries inside the app",
            "",
            "Every library that ends up in the release build, including the ones",
            "the app's own dependencies bring with them, and the licence each is",
            "published under. Made by `tools/licences.py --write` from Gradle's",
            "dependency list; do not edit by hand.",
            "",
            f"In all: **{len(rows)} libraries**, " +
            ", ".join(f"{n} under {spdx}" for spdx, n in counts.most_common()) + ".",
            "Both licences allow use in an Apache-2.0 app. The BSD-3-Clause one asks",
            "that its notice travel with the app: it is inside the APK, and a copy is",
            "in [licenses/protobuf-BSD-3-Clause.txt](../licenses/protobuf-BSD-3-Clause.txt).",
            "",
            "Not libraries, but also bundled: the Inter typeface (SIL Open Font",
            "License 1.1) and the Exodus Privacy tracker list (Open Database License",
            "1.0). See [licenses/README.md](../licenses/README.md).",
            "",
            "| Library | Version | Licence |",
            "|---|---|---|",
        ]
        lines += [f"| `{g}:{a}` | {v} | {l} |" for g, a, v, l in rows]
        with open(OUT, "w", encoding="utf-8") as handle:
            handle.write("\n".join(lines) + "\n")
        print(f"written {OUT}")

    if problems:
        print("licences that need a person to look at them:")
        for p in problems:
            print("  " + p)
        return 1
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
