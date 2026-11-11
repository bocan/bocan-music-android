#!/usr/bin/env python3
"""Source-level tag audit for the end-to-end suite (phase 17, slice 2).

The cheap, no-device twin of TagCoverageAudit (androidTest). It scans the app's
Compose sources for interactive call sites and reports any that carry no
`testTag(` (or the `tag =` argument of the settings rows, which apply it), and
it scans the end-to-end tests for the two habits the suite forbids: sleeping
instead of waiting on state, and tapping hard-coded pixel coordinates.

It is a heuristic over source text, not a parser: the device audit is the
contract. A site the heuristic misreads can carry `// e2e-audit: ignore (reason)`
on the line itself.

Usage:
    scripts/e2e/audit-test-tags.py            report findings, exit 0
    scripts/e2e/audit-test-tags.py --strict   report findings, exit 1 if any (make lint)
"""

from __future__ import annotations

import argparse
import re
import sys
from dataclasses import dataclass
from pathlib import Path

REPO = Path(__file__).resolve().parents[2]
APP_SOURCES = REPO / "app/src/main/kotlin"
E2E_TESTS = REPO / "app/src/androidTest/kotlin"

# Glance widgets render through the launcher, not Compose UI semantics; the suite asserts
# the widget's state store instead (phase 17 non-goals).
SKIPPED_DIRS = ("/widget/",)

IGNORE_MARKER = "e2e-audit: ignore"

# Modifier-chain actions: the tag must sit in the same chain, a few lines either side.
MODIFIER_ACTIONS = re.compile(r"\.(clickable|combinedClickable|toggleable|selectable)\b")
CHAIN_WINDOW_BEFORE = 6
CHAIN_WINDOW_AFTER = 2

# Material controls that act when tapped, dragged, or typed into: the tag must be among
# the call's own arguments.
CONTROL_CALLS = re.compile(
    r"(?<![\w.])("
    r"Button|IconButton|FilledIconButton|OutlinedButton|TextButton|ElevatedButton|FilledTonalButton|"
    r"FilterChip|AssistChip|InputChip|SuggestionChip|"
    r"DropdownMenuItem|Tab|NavigationBarItem|NavigationRailItem|"
    r"Slider|RangeSlider|Switch|Checkbox|RadioButton|"
    r"TextField|OutlinedTextField|BasicTextField"
    r")\("
)

# A control whose own handler is null is purely visual; its row owns the action.
NULL_HANDLER = re.compile(r"\bon(Click|CheckedChange)\s*=\s*null\b")
TAGGED = re.compile(r"\btestTag\(|\btag\s*=")

SLEEP = re.compile(r"\bThread\.sleep\(|\bSystemClock\.sleep\(")
PIXELS = re.compile(r"\bOffset\(\s*-?\d+(\.\d+)?f?\s*,\s*-?\d+(\.\d+)?f?\s*\)")


@dataclass(frozen=True)
class Finding:
    path: Path
    line: int
    message: str

    def render(self) -> str:
        try:
            shown = self.path.relative_to(REPO)
        except ValueError:
            shown = self.path
        return f"{shown}:{self.line}: {self.message}"


def call_arguments(text: str, open_paren: int) -> str:
    """The code between the parenthesis at `open_paren` and its match, with string literals blanked."""
    depth = 0
    in_string = False
    code: list[str] = []
    i = open_paren
    while i < len(text):
        char = text[i]
        if in_string:
            if char == "\\":
                i += 2
                continue
            if char == '"':
                in_string = False
                code.append(char)
        elif char == '"':
            in_string = True
            code.append(char)
        elif char == "(":
            depth += 1
            code.append(char)
        elif char == ")":
            depth -= 1
            if depth == 0:
                return "".join(code[1:])
            code.append(char)
        else:
            code.append(char)
        i += 1
    return "".join(code[1:])


def line_of(text: str, offset: int) -> int:
    return text.count("\n", 0, offset) + 1


def is_declaration(text: str, start: int) -> bool:
    """`fun Button(` or `class Tab(`: a definition, not a call."""
    line_start = text.rfind("\n", 0, start) + 1
    before = text[line_start:start]
    return bool(re.search(r"\b(fun|class|object)\s+$", before))


def scan_source(path: Path, text: str) -> list[Finding]:
    findings: list[Finding] = []
    lines = text.splitlines()

    for match in MODIFIER_ACTIONS.finditer(text):
        line = line_of(text, match.start())
        if IGNORE_MARKER in lines[line - 1] or lines[line - 1].lstrip().startswith("import "):
            continue
        window = lines[max(0, line - 1 - CHAIN_WINDOW_BEFORE) : line + CHAIN_WINDOW_AFTER]
        if not any(TAGGED.search(candidate) for candidate in window):
            findings.append(Finding(path, line, f".{match.group(1)} with no testTag in its modifier chain"))

    for match in CONTROL_CALLS.finditer(text):
        if is_declaration(text, match.start()):
            continue
        line = line_of(text, match.start())
        if IGNORE_MARKER in lines[line - 1]:
            continue
        arguments = call_arguments(text, match.end() - 1)
        if NULL_HANDLER.search(arguments) or TAGGED.search(arguments):
            continue
        # A tagged modifier built on the line above and passed in (`modifier = repeatModifier`).
        if line >= 2 and TAGGED.search(lines[line - 2]):
            continue
        findings.append(Finding(path, line, f"{match.group(1)}( with no testTag among its arguments"))

    return findings


def scan_tests(path: Path, text: str) -> list[Finding]:
    findings: list[Finding] = []
    for number, line in enumerate(text.splitlines(), start=1):
        if IGNORE_MARKER in line:
            continue
        if SLEEP.search(line):
            findings.append(Finding(path, number, "sleeps instead of waiting on state (waitUntil, awaitPlayer, awaitState)"))
        if PIXELS.search(line):
            findings.append(Finding(path, number, "hard-coded pixel coordinate; aim at a tagged node instead"))
    return findings


def kotlin_files(root: Path) -> list[Path]:
    return sorted(p for p in root.rglob("*.kt") if not any(skip in p.as_posix() for skip in SKIPPED_DIRS))


def audit(app_sources: Path = APP_SOURCES, e2e_tests: Path = E2E_TESTS) -> list[Finding]:
    findings: list[Finding] = []
    for path in kotlin_files(app_sources):
        findings += scan_source(path, path.read_text(encoding="utf-8"))
    if e2e_tests.exists():
        for path in kotlin_files(e2e_tests):
            findings += scan_tests(path, path.read_text(encoding="utf-8"))
    return findings


def main(argv: list[str]) -> int:
    parser = argparse.ArgumentParser(description=__doc__.splitlines()[0])
    parser.add_argument("--strict", action="store_true", help="exit 1 when there is any finding")
    args = parser.parse_args(argv)

    findings = audit()
    for finding in findings:
        print(finding.render())
    if findings:
        print(f"audit-test-tags: {len(findings)} finding(s); see docs/e2e.md, 'Adding a control'", file=sys.stderr)
        return 1 if args.strict else 0
    print("audit-test-tags: every interactive call site carries a tag")
    return 0


if __name__ == "__main__":
    sys.exit(main(sys.argv[1:]))
