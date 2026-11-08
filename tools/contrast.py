#!/usr/bin/env python3
"""Check every text-on-surface colour pair in the palette against WCAG AA.

Run it from the repository root:

    python3 tools/contrast.py

It exits non-zero if any pair falls below its threshold, so it can be a gate
rather than a report.

It reads the colours out of `Palette.kt` and `LauncherTheme.kt` rather than
holding its own copies, because a contrast checker that has to be updated by
hand when a colour changes is a checker that silently goes stale. If either
file is restructured so the values cannot be found, this fails loudly instead
of reporting a clean run over an empty set.

What it cannot check: any text drawn straight onto the wallpaper -- the home
screen clock, the app labels under icons, every line of the minimal home.
Those sit on whatever photo the user chose, so their legibility comes from
the shadow each one carries, not from a pair of known colours. Checking them
means looking at a screen.
"""

import re
import sys
from pathlib import Path

PALETTE = Path("app/src/main/java/com/privacylauncher/designsystem/Palette.kt")
THEME = Path("app/src/main/java/com/privacylauncher/designsystem/LauncherTheme.kt")

# WCAG 2.1 AA.
NORMAL_TEXT = 4.5   # under 18pt, or under 14pt bold
LARGE_TEXT = 3.0    # 18pt and over, and UI component boundaries


def _channel(value: int) -> float:
    c = value / 255.0
    return c / 12.92 if c <= 0.04045 else ((c + 0.055) / 1.055) ** 2.4


def luminance(hex_colour: str) -> float:
    h = hex_colour.lstrip("#")[-6:]
    r, g, b = (int(h[i:i + 2], 16) for i in (0, 2, 4))
    return 0.2126 * _channel(r) + 0.7152 * _channel(g) + 0.0722 * _channel(b)


def contrast(a: str, b: str) -> float:
    la, lb = luminance(a), luminance(b)
    hi, lo = max(la, lb), min(la, lb)
    return (hi + 0.05) / (lo + 0.05)


def read(path: Path) -> str:
    if not path.exists():
        sys.exit(f"{path} not found -- run this from the repository root.")
    return path.read_text(encoding="utf-8")


def named_colours(source: str, obj: str) -> dict[str, str]:
    """`val Name = Color(0xFFRRGGBB)` inside `internal object <obj> { ... }`."""
    block = re.search(rf"internal object {obj} \{{(.*?)\n\}}", source, re.S)
    if not block:
        sys.exit(f"could not find `internal object {obj}` in {PALETTE}")
    found = dict(re.findall(r"val (\w+) = Color\(0x([0-9A-Fa-f]{8})\)", block.group(1)))
    if not found:
        sys.exit(f"`object {obj}` matched but held no Color(0x...) values")
    return found


def accents(source: str) -> dict[str, dict[str, str]]:
    """Each `AccentColor.NAME -> AccentPalette( ... )` arm."""
    out: dict[str, dict[str, str]] = {}
    for name, body in re.findall(
        r"AccentColor\.(\w+) -> AccentPalette\((.*?)\n    \)", source, re.S
    ):
        out[name] = dict(
            re.findall(r"(\w+) = Color\(0x([0-9A-Fa-f]{8})\)", body)
        )
    if not out:
        sys.exit(f"could not find any AccentPalette arms in {PALETTE}")
    return out


def scheme_colour(source: str, scheme: str, key: str) -> str:
    """A literal `key = Color(0xFFRRGGBB)` inside light/darkScheme()."""
    block = re.search(rf"private fun {scheme}\(.*?\n\)", source, re.S)
    if not block:
        sys.exit(f"could not find `{scheme}(` in {THEME}")
    m = re.search(rf"{key} = Color\(0x([0-9A-Fa-f]{{8}})\)", block.group(0))
    if not m:
        sys.exit(f"could not find `{key}` as a literal colour in {scheme}()")
    return m.group(1)


def main() -> int:
    palette_src = read(PALETTE)
    theme_src = read(THEME)
    n = named_colours(palette_src, "Neutral")
    signal = named_colours(palette_src, "Signal")
    acc = accents(palette_src)

    failures: list[str] = []
    checked = 0

    def check(label: str, fg: str, bg: str, need: float = NORMAL_TEXT) -> None:
        nonlocal checked
        checked += 1
        ratio = contrast(fg, bg)
        ok = ratio >= need
        if not ok:
            failures.append(f"{label}: {ratio:.2f} < {need}")
        print(f"  {'pass' if ok else 'FAIL'}  {ratio:5.2f}  {label}")

    for theme, surfaces, body, variant, tone, on_primary in (
        (
            "LIGHT",
            ("Background", "Surface1", "Surface2", "Surface3"),
            "OnBackground", "OnVariant", "onLight", "FFFFFFFF",
        ),
        (
            "DARK",
            ("BackgroundDark", "Surface1Dark", "Surface2Dark", "Surface3Dark"),
            "OnBackgroundDark", "OnVariantDark", "onDark", None,
        ),
    ):
        print(f"\n=== {theme} ===")
        print("body text on every surface")
        for s in surfaces:
            check(f"{body} on {s}", n[body], n[s])
        print("secondary text on every surface")
        for s in surfaces:
            check(f"{variant} on {s}", n[variant], n[s])
        # The accent is used at labelMedium for section headings, so it is
        # small text and needs the full 4.5, not the 3.0 large-text allowance.
        print("accent as small text (section headings) on every surface but the deepest")
        for name, tones in sorted(acc.items()):
            for s in surfaces[:3]:
                check(f"{name} {tone} on {s}", tones[tone], n[s])
        print("filled button: label on the accent")
        fg = on_primary or n["BackgroundDark"]
        for name, tones in sorted(acc.items()):
            check(f"button label on {name}", fg, tones[tone])
        print("status tones on the base background")
        suffix = "Light" if theme == "LIGHT" else "Dark"
        for sname, shex in sorted(signal.items()):
            if sname.endswith(suffix):
                check(f"{sname} on {surfaces[0]}", shex, n[surfaces[0]])
        scheme = "lightScheme" if theme == "LIGHT" else "darkScheme"
        err = scheme_colour(theme_src, scheme, "error")
        check(
            f"error label on error #{err[-6:]}",
            "FFFFFFFF" if theme == "LIGHT" else n["BackgroundDark"],
            err,
        )

    print(f"\n{checked} pairs checked, {len(failures)} below AA.")
    for f in failures:
        print("  -", f)
    print(
        "\nNot covered: text drawn over the wallpaper (the clock, icon labels,"
        "\nthe minimal home). Those carry their own shadow and depend on the"
        "\nuser's wallpaper, so they can only be judged on a screen."
    )
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
