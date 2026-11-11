"""Tests for audit-test-tags.py: run with `python3 -m unittest discover -s scripts/e2e` (make lint does)."""

from __future__ import annotations

import importlib.util
import unittest
from pathlib import Path

_SPEC = importlib.util.spec_from_file_location("audit_test_tags", Path(__file__).with_name("audit-test-tags.py"))
audit = importlib.util.module_from_spec(_SPEC)
_SPEC.loader.exec_module(audit)

SOURCE = Path("Screen.kt")


def messages(text: str) -> list[str]:
    return [finding.message for finding in audit.scan_source(SOURCE, text)]


class SourceScanTests(unittest.TestCase):
    def test_an_untagged_button_is_a_finding(self):
        self.assertEqual(messages("Button(onClick = onPair) { Text(label) }"), ["Button( with no testTag among its arguments"])

    def test_a_tagged_button_passes(self):
        self.assertEqual(messages("Button(onClick = onPair, modifier = Modifier.testTag(E2ETags.Sync.PAIR)) { Text(label) }"), [])

    def test_a_tag_argument_passes(self):
        self.assertEqual(messages("SettingsToggleRow(label = x, checked = y, onCheckedChange = z, tag = E2ETags.Sync.PERIODIC)"), [])

    def test_a_tagged_modifier_built_on_the_line_above_passes(self):
        text = "val repeatModifier = Modifier.testTag(E2ETags.NowPlaying.REPEAT)\nIconButton(onClick = onCycle, modifier = repeatModifier) {}"
        self.assertEqual(messages(text), [])

    def test_a_visual_only_control_with_a_null_handler_passes(self):
        self.assertEqual(messages("Switch(checked = enabled, onCheckedChange = null)"), [])
        self.assertEqual(messages("RadioButton(selected = selected, onClick = null)"), [])

    def test_an_untagged_clickable_chain_is_a_finding(self):
        text = "ListItem(\n    headlineContent = { Text(name) },\n    modifier = Modifier.fillMaxWidth().clickable { onOpen(id) }\n)"
        self.assertEqual(messages(text), [".clickable with no testTag in its modifier chain"])

    def test_a_tagged_clickable_chain_passes(self):
        text = "modifier = Modifier\n    .fillMaxWidth()\n    .testTag(E2ETags.Rows.ARTIST)\n    .clickable { onOpen(id) }"
        self.assertEqual(messages(text), [])

    def test_imports_declarations_and_marked_lines_are_skipped(self):
        text = (
            "import androidx.compose.foundation.clickable\n"
            "fun BackButton(onBack: () -> Unit) = IconButton(onClick = onBack, modifier = Modifier.testTag(BACK)) {}\n"
            "Text(x, modifier = Modifier.clickable { go() }) // e2e-audit: ignore (hidden from semantics)\n"
        )
        self.assertEqual(messages(text), [])

    def test_a_tag_inside_a_string_does_not_count(self):
        self.assertEqual(messages('Button(onClick = { log("testTag(") }) {}'), ["Button( with no testTag among its arguments"])


class TestScanTests(unittest.TestCase):
    def test_sleeping_and_pixel_coordinates_are_findings(self):
        text = "Thread.sleep(500)\nperformTouchInput { click(Offset(120f, 40f)) }\nwaitUntil { ready }"
        found = [(f.line, f.message.split(" ")[0]) for f in audit.scan_tests(SOURCE, text)]
        self.assertEqual(found, [(1, "sleeps"), (2, "hard-coded")])


class RepositoryTests(unittest.TestCase):
    def test_the_app_has_no_findings(self):
        self.assertEqual([f.render() for f in audit.audit()], [])


if __name__ == "__main__":
    unittest.main()
