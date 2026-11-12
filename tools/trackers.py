#!/usr/bin/env python3
"""Builds the tracker list the launcher carries inside it.

    tools/trackers.py path/to/trackers.json     from a file you already have
    tools/trackers.py --fetch                   download a fresh copy first

The list comes from Exodus Privacy (https://exodus-privacy.eu.org), who
publish it under the Open Database License 1.0. Their full file is over half
a megabyte, most of it long descriptions the launcher never shows. This
keeps the four things the scanner needs -- an id, a name, what kind of
tracker it is, and the code signatures that give it away -- and writes them
to app/src/main/assets/trackers.tsv, which is what ships.

That file is committed. Building the app never runs this script and never
touches the network: the launcher has no network permission, and its build
does not need one either. Run this by hand when you want a newer snapshot,
read the summary it prints, and commit the result.

The download is the only thing in this project that talks to the internet,
and it happens on the machine of whoever runs it, never on a phone. It sends
nothing but an ordinary web request.
"""
import datetime
import json
import os
import re
import sys
import urllib.request

SOURCE = "https://reports.exodus-privacy.eu.org/api/trackers"
OUT = os.path.join(os.path.dirname(__file__), "..", "app", "src", "main", "assets", "trackers.tsv")

# What a signature may contain: the characters of a Java package or class
# name, and the dot between them. Exodus writes signatures as regular
# expressions, but in practice every one is a plain name, and the scanner
# reads them as plain names. Anything else would silently never match, so it
# is refused here, where a person is watching, rather than on a phone.
PLAIN = re.compile(r"^[A-Za-z0-9_$.\-]+$")


def load(argv):
    if len(argv) == 2 and argv[1] == "--fetch":
        request = urllib.request.Request(SOURCE, headers={"User-Agent": "tracker-list-snapshot"})
        with urllib.request.urlopen(request, timeout=60) as response:
            return json.loads(response.read().decode("utf-8"))
    if len(argv) == 2 and os.path.isfile(argv[1]):
        with open(argv[1], encoding="utf-8") as handle:
            return json.load(handle)
    sys.exit(__doc__)


def main(argv):
    raw = load(argv)
    trackers = raw.get("trackers", raw)
    items = list(trackers.values()) if isinstance(trackers, dict) else list(trackers)

    rows, skipped, refused = [], [], []
    for item in sorted(items, key=lambda t: int(t["id"])):
        name = " ".join(str(item.get("name", "")).split())
        signatures = []
        for part in str(item.get("code_signature") or "").split("|"):
            part = part.strip().replace("\\.", ".")
            if not part:
                continue
            if not PLAIN.match(part) or len(part.strip(".")) < 4:
                refused.append((name, part))
                continue
            signatures.append(part)
        if not name or not signatures:
            skipped.append(name or "(no name)")
            continue
        categories = ",".join(sorted(str(c).strip() for c in item.get("categories") or []))
        for field in (name, categories):
            if "\t" in field or "\n" in field:
                sys.exit(f"a tab or newline in {field!r}: the file format cannot hold it")
        rows.append((int(item["id"]), name, categories, "|".join(dict.fromkeys(signatures))))

    today = datetime.date.today().isoformat()
    lines = [
        "# Tracker signatures from Exodus Privacy, https://exodus-privacy.eu.org",
        "# Made available under the Open Database License 1.0:",
        "# https://opendatacommons.org/licenses/odbl/1-0/",
        "# This file is a cut-down copy (id, name, categories, code signatures)",
        "# made by tools/trackers.py. It is offered under the same licence.",
        f"# snapshot\t{today}",
        "# id\tname\tcategories\tsignatures",
    ]
    lines += ["\t".join(str(field) for field in row) for row in rows]
    os.makedirs(os.path.dirname(OUT), exist_ok=True)
    with open(OUT, "w", encoding="utf-8", newline="\n") as handle:
        handle.write("\n".join(lines) + "\n")

    count = sum(len(row[3].split("|")) for row in rows)
    print(f"{len(rows)} trackers, {count} signatures, {os.path.getsize(OUT)} bytes")
    print(f"written to {os.path.relpath(OUT)} (snapshot {today})")
    if skipped:
        print(f"left out, no code signature to look for: {len(skipped)}")
        for name in skipped:
            print(f"  {name}")
    if refused:
        print(f"signatures refused as not plain names: {len(refused)}")
        for name, part in refused:
            print(f"  {name}: {part!r}")


if __name__ == "__main__":
    main(sys.argv)
