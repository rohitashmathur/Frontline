import contextlib
from collections import Counter
import csv
import importlib.util
import io
import json
from pathlib import Path
import subprocess
import sys
import tempfile
import unittest


ROOT = Path(__file__).resolve().parents[2]
SPEC = importlib.util.spec_from_file_location("frontline_playtest_summary", ROOT / "tools" / "playtest_summary.py")
tool = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = tool
SPEC.loader.exec_module(tool)
HEADERS = tool.BASE_COLUMNS + tool.METADATA_COLUMNS
ATTEMPT_ID = "00000000-0000-4000-8000-000000000001"


def event(name, timestamp=1000, attempt=ATTEMPT_ID, payload="", **changes):
    row = dict(zip(HEADERS, (
        str(timestamp), name, "1", "0", "Normal", "1", "campaign", "11", "1", "42",
        "true", "true", "false", "0.0", "0", "-1", "0.0", "0.0", "0", "", "", "0",
        "build=V13;build_code=13;attempt_id=" + attempt + (";" + payload if payload else ""),
        "0", "", "", "", "0",
    )))
    row.update({key: str(value) for key, value in changes.items()})
    return row


def result(timestamp=3000, won=True, **changes):
    row = event("attempt_result", timestamp, payload="won" if won else "lost",
                outcome="1" if won else "2", terminal_reason="6" if won else "3")
    row.update({key: str(value) for key, value in changes.items()})
    return row


def menu_event(name, timestamp=1000, payload="", **changes):
    return event(name, timestamp, attempt="", payload=payload, mode="menu", sector="", sector_index="",
                 difficulty="unknown", difficulty_id="", rules_version="0", ai_version="0", seed="unknown",
                 seed_known="false", history_known="false", outcome="", battle_mode="", terminal_reason="", **changes)


class PlaytestSummaryTests(unittest.TestCase):
    def setUp(self):
        self.temp = tempfile.TemporaryDirectory(prefix="frontline-summary-test-")
        self.root = Path(self.temp.name)
        self.output = self.root / "report"

    def tearDown(self):
        self.temp.cleanup()

    def export(self, filename, rows, headers=HEADERS):
        path = self.root / filename
        path.parent.mkdir(parents=True, exist_ok=True)
        with path.open("w", encoding="utf-8", newline="") as handle:
            writer = csv.DictWriter(handle, fieldnames=headers, lineterminator="\r\n")
            writer.writeheader()
            writer.writerows({column: row.get(column, "") for column in headers} for row in rows)
        return path

    def run_summary(self, paths, sessions=()):
        tool.summarize(paths, self.output, sessions)
        with (self.output / "attempts.csv").open(encoding="utf-8", newline="") as handle:
            attempts = list(csv.DictReader(handle))
        with (self.output / "merged.csv").open(encoding="utf-8", newline="") as handle:
            merged = list(csv.DictReader(handle))
        return attempts, merged, (self.output / "summary.md").read_text(encoding="utf-8")

    def test_victory_dispatch_rating_and_original_fields(self):
        path = self.export("S01.csv", [event("attempt_start"),
            event("first_launch", 2000, payload="active_seconds=0.25;source=0;target=1;troops=10", elapsed_seconds="0.25"),
            result(), event("battle_feedback", 3100, payload="rating=about_right", outcome="1", terminal_reason="6")])
        original = path.read_bytes()
        attempts, merged, summary = self.run_summary([path])
        self.assertEqual(len(attempts), 1)
        attempt = attempts[0]
        self.assertEqual((attempt["status"], attempt["complete"], attempt["rating"]), ("victory", "true", "about_right"))
        self.assertEqual(attempt["first_dispatch_active_seconds"], "0.25")
        self.assertEqual(attempt["first_dispatch_wall_seconds"], "1.0")
        self.assertEqual(merged[1]["detail"], "build=V13;build_code=13;attempt_id=" + ATTEMPT_ID + ";active_seconds=0.25;source=0;target=1;troops=10")
        self.assertIn("mean=0.250", summary)
        self.assertIn("Rated/R", summary)
        self.assertEqual(path.read_bytes(), original)

    def test_all_modes_and_objective_success_separate_from_victory(self):
        rows = []
        for index, mode in enumerate(("campaign", "challenge", "daily", "run", "logistics")):
            changes = {"mode": mode, "battle_mode": {"run": "1", "logistics": "2"}.get(mode, "0")}
            if mode in {"challenge", "daily"}:
                changes.update(objective_type="1", objective_target="2", objective_seconds="30", challenge_id="0")
            if mode == "daily":
                changes["daily_date"] = "2026-10-11"
            if mode == "run":
                changes.update(run_id="run-x/0", run_node="0", run_perks="0")
            identity = "id-" + mode
            rows.extend([event("attempt_start", 1000 + index * 100, attempt=identity, **changes),
                         result(1010 + index * 100, detail="build=V13;build_code=13;attempt_id=" + identity + ";won", **changes)])
        attempts, _, summary = self.run_summary([self.export("modes.csv", rows)])
        self.assertEqual({a["mode"]: a["status"] for a in attempts}, {
            "campaign": "victory", "challenge": "objective_success", "daily": "objective_success",
            "run": "victory", "logistics": "victory"})
        self.assertIn("Run mode figures are individual battles", summary)

    def test_explicit_loss_abandon_pause_and_unknown_timeout(self):
        paths = [self.export("loss.csv", [event("attempt_start"), result(won=False)]),
                 self.export("abandon.csv", [event("attempt_start"), event("abandon", 2000, payload="restart")]),
                 self.export("pause.csv", [event("attempt_start"), event("pause", 2000)]),
                 self.export("timeout.csv", [event("attempt_start"), event("turn_timeout", 900000, outcome="2", terminal_reason="3")])]
        attempts, merged, summary = self.run_summary(paths)
        self.assertEqual({a["status"] for a in attempts}, {"loss", "abandon", "paused", "incomplete"})
        self.assertEqual(sum(a["status"] == "loss" for a in attempts), 1)
        self.assertIn("unknown_event", next(r["pt_flags_json"] for r in merged if r["event"] == "turn_timeout"))
        self.assertIn("timeout is inferred as a loss", summary)

    def test_result_only_and_start_only_are_incomplete_not_invented(self):
        paths = [self.export("orphan.csv", [result(won=False)]), self.export("unfinished.csv", [event("attempt_start")])]
        attempts, _, summary = self.run_summary(paths)
        orphan = next(a for a in attempts if a["status"] == "loss")
        self.assertEqual((orphan["start_count"], orphan["complete"]), ("0", "false"))
        self.assertIn("missing_start", orphan["flags_json"])
        unfinished = next(a for a in attempts if a["status"] == "incomplete")
        self.assertIn("missing_end", unfinished["flags_json"])
        self.assertIn("Incomplete segments (missing start or end): 2", summary)

    def test_snapshot_outcome_and_run_end_are_not_battle_results(self):
        rows = [event("attempt_start"), event("node_end", 2000, payload="outcome=2;terminal_reason=3", outcome="2", terminal_reason="3"),
                event("run_end", 2001, payload="run_id=r;status=5;battles_played=1", outcome="2", terminal_reason="3")]
        attempts, _, _ = self.run_summary([self.export("run.csv", rows)])
        self.assertEqual(attempts[0]["status"], "incomplete")
        self.assertEqual(attempts[0]["result_count"], "0")

    def test_all_objective_types_need_explicit_result(self):
        paths = []
        for objective in (1, 2, 3):
            changes = dict(mode="challenge", objective_type=objective, objective_target="1",
                           objective_seconds="30", deployment_budget="100", challenge_id="0")
            paths.append(self.export("success-{}.csv".format(objective), [event("attempt_start", **changes), result(**changes)]))
            paths.append(self.export("progress-{}.csv".format(objective), [event("attempt_start", **changes),
                event("pause", 60000, elapsed_seconds="60", objective_progress="30", **changes)]))
        attempts, _, _ = self.run_summary(paths)
        self.assertEqual(Counter(a["status"] for a in attempts), {"objective_success": 3, "paused": 3})
        self.assertEqual(sum(a["status"] == "loss" for a in attempts), 0)

    def test_overlap_deduplicates_only_explicit_same_scope(self):
        start, pause, end = event("attempt_start"), event("pause", 2000), result(4000)
        first = self.export("early.csv", [start, pause])
        second = self.export("later.csv", [pause, end])
        copy = self.export("copy.csv", [start, pause])
        sessions = ["S01=" + str(path) for path in (first, second, copy)]
        attempts, merged, summary = self.run_summary([first, second, copy], sessions)
        self.assertEqual(len(merged), 3)
        self.assertEqual(len(attempts), 1)
        self.assertEqual(attempts[0]["status"], "victory")
        self.assertEqual(len(json.loads(next(r["pt_sources_json"] for r in merged if r["event"] == "pause"))), 3)
        self.assertIn("Overlap occurrences removed within explicit scopes: 3", summary)

    def test_identical_exports_and_cross_device_id_not_silently_collapsed(self):
        first = self.export("D01.csv", [event("attempt_start"), result()])
        second = self.export("D02.csv", [event("attempt_start"), result()])
        attempts, merged, summary = self.run_summary([first, second])
        self.assertEqual((len(attempts), len(merged)), (2, 4))
        self.assertIn("Identical exports in distinct scopes retained separately", summary)
        self.assertIn("Raw attempt IDs present in multiple scopes: 1", summary)

    def test_repeated_identical_rows_in_one_export_preserve_multiplicity(self):
        pause = event("pause", 2000)
        first = self.export("a.csv", [event("attempt_start"), pause, pause])
        second = self.export("b.csv", [event("attempt_start"), pause])
        attempts, merged, _ = self.run_summary([first, second], ["S01=" + str(first), "S01=" + str(second)])
        self.assertEqual(len(merged), 3)
        self.assertEqual(attempts[0]["pause_count"], "2")

    def test_reset_collision_in_same_scope_excluded_from_metrics(self):
        first = self.export("a.csv", [event("attempt_start"), result()])
        second = self.export("b.csv", [event("attempt_start", 5000), result(6000)])
        attempts, _, summary = self.run_summary([first, second], ["S01=" + str(first), "S01=" + str(second)])
        self.assertEqual(len(attempts), 1)
        self.assertIn("multiple_starts_identity_collision", attempts[0]["flags_json"])
        self.assertEqual(attempts[0]["metric_eligible"], "false")
        self.assertEqual(attempts[0]["status"], "ambiguous_identity_or_context")
        self.assertIn("Ambiguous identity/context segments: 1", summary)

    def test_build_rules_ai_mode_and_difficulty_stay_separate(self):
        base = [event("attempt_start"), result()]
        paths = [self.export("a.csv", base)]
        for index, changes in enumerate((
            {"detail": "build=V12;build_code=12;attempt_id=id-v12"},
            {"rules_version": "10"}, {"ai_version": "2"}, {"difficulty": "Hard", "difficulty_id": "2"},
            {"mode": "logistics", "battle_mode": "2"},
        )):
            rows = [dict(row, **changes) for row in base]
            if "detail" in changes:
                rows[1]["detail"] += ";won"
            paths.append(self.export("context-{}.csv".format(index), rows))
        attempts, _, summary = self.run_summary(paths)
        self.assertEqual(len(attempts), 6)
        self.assertEqual(len({tuple(a[k] for k in ("build", "rules_version", "ai_version", "mode", "difficulty")) for a in attempts}), 6)
        self.assertIn("V12/12", summary)

    def test_upgrade_continuation_does_not_invent_second_start(self):
        rows = [event("attempt_start"), result(detail="build=V14;build_code=14;attempt_id=" + ATTEMPT_ID + ";won")]
        attempts, _, _ = self.run_summary([self.export("upgrade.csv", rows)])
        self.assertEqual(len(attempts), 2)
        self.assertEqual(sum(int(a["start_count"]) for a in attempts), 1)
        self.assertTrue(all("context_changed_for_identity" in a["flags_json"] for a in attempts))
        self.assertTrue(all(a["complete"] == "false" for a in attempts))

    def test_malformed_context_missing_identity_and_legacy_are_preserved(self):
        rows = [event("attempt_start", detail=""),
                event("first_launch", 1500, detail="build=V13;build_code=13;attempt_id=;active_seconds=0.2"),
                event("pause", 2000, detail="build=V13;build_code=no;attempt_id=abc"),
                event("resume", 2200, detail="build=V13;build_code=13;attempt_id=abc;attempt_id=def"),
                event("future_event", 2300, payload="new=value,quoted\"value")]
        path = self.export("legacy.csv", rows, tool.BASE_COLUMNS + ("future_column",))
        attempts, merged, summary = self.run_summary([path])
        self.assertEqual(len(merged), 5)
        self.assertIn("legacy_context", merged[0]["pt_flags_json"])
        self.assertIn("missing_attempt_identity", merged[0]["pt_flags_json"])
        self.assertIn("malformed_context", merged[2]["pt_flags_json"])
        self.assertIn("malformed_context", merged[3]["pt_flags_json"])
        self.assertEqual(merged[4]["detail"], rows[4]["detail"])
        self.assertEqual(len(attempts), 1)
        self.assertEqual(attempts[0]["status"], "incomplete")
        self.assertIn("unknown_event: 1", summary)

    def test_malformed_context_on_related_row_taints_attempt_metrics(self):
        rows = [event("attempt_start"), result(), event("pause", 4000, difficulty_id="9")]
        attempts, merged, _ = self.run_summary([self.export("bad.csv", rows)])
        self.assertIn("malformed_rows_excluded", attempts[0]["flags_json"])
        self.assertEqual(attempts[0]["metric_eligible"], "false")
        self.assertEqual(merged[-1]["pt_attempt_key"], "")

    def test_conflicting_result_and_multiple_launch_not_selected_arbitrarily(self):
        rows = [event("attempt_start"),
                event("first_launch", 1500, payload="active_seconds=1", elapsed_seconds="1"),
                event("first_launch", 1600, payload="active_seconds=2", elapsed_seconds="2"), result(), result(3100, won=False)]
        attempts, _, _ = self.run_summary([self.export("conflict.csv", rows)])
        self.assertEqual(attempts[0]["status"], "unknown_result")
        self.assertEqual(attempts[0]["observed_result"], "conflicting")
        self.assertEqual(attempts[0]["first_dispatch_active_seconds"], "")
        self.assertIn("multiple_first_dispatch_events", attempts[0]["flags_json"])

    def test_inconsistent_result_payload_does_not_count_as_loss(self):
        rows = [event("attempt_start"), event("attempt_result", 2000, payload="lost", outcome="1", terminal_reason="6")]
        attempts, _, _ = self.run_summary([self.export("inconsistent.csv", rows)])
        self.assertEqual(attempts[0]["status"], "unknown_result")

    def test_abandon_marker_does_not_override_explicit_result(self):
        rows = [event("attempt_start"), event("abandon", 2000, payload="run_restart"), result(2001, won=False), event("abandon", 3000, payload="new_run", outcome="2", terminal_reason="3")]
        attempts, _, _ = self.run_summary([self.export("surrender.csv", rows)])
        self.assertEqual(attempts[0]["status"], "loss")
        self.assertEqual(attempts[0]["abandon_count"], "2")
        self.assertIn("abandon_also_observed", attempts[0]["flags_json"])

    def test_pause_resume_requests_and_cancel_do_not_end_attempt(self):
        rows = [event("attempt_start"), event("pause", 2000), event("restart_request", 2500),
                event("restart_cancel", 2600), event("resume", 2700, payload="back")]
        attempts, _, _ = self.run_summary([self.export("paused.csv", rows)])
        self.assertEqual(attempts[0]["status"], "incomplete")
        self.assertEqual(attempts[0]["result_count"], "0")

    def test_tutorial_first_replay_skip_incomplete_and_orphan(self):
        rows = [menu_event("tutorial_start", payload="first_attempt"),
                menu_event("tutorial_complete", 2000, payload="step=5;semantics=v11"),
                menu_event("tutorial_start", 3000, payload="replay"),
                menu_event("tutorial_skip", 4000, payload="step=2;semantics=v11"),
                menu_event("tutorial_start", 5000, payload="replay"),
                menu_event("tutorial_start", 6000, payload="replay"),
                menu_event("tutorial_complete", 7000, payload="step=5;semantics=legacy"),
                menu_event("tutorial_skip", 8000, payload="step=3;semantics=v11"),
                menu_event("tutorial_skip", 9000, payload="step=3;semantics=v11")]
        path = self.export("tutorial.csv", rows)
        attempts, _, summary = self.run_summary([path])
        exports, _ = tool.load_exports([(path, "scope", "test")])
        records, orphan = tool.tutorial_records(exports[0]["rows"])
        self.assertEqual(len(attempts), 0)
        self.assertEqual(Counter(r["end"] for r in records), {"complete": 1, "skip": 2, "missing": 1})
        self.assertEqual(orphan["tutorial_complete"], 1)
        self.assertEqual(orphan["tutorial_skip"], 1)
        self.assertIn("first_attempt | 1 | 1 | 0 | 0", summary)
        self.assertIn("replay | 3 | 0 | 2 | 1", summary)

    def test_v13_first_mode_intent_is_separate_from_replay_and_battle_starts(self):
        rows = [menu_event("tutorial_start", payload="first_mode=run"),
                menu_event("tutorial_skip", 2000, payload="step=1;semantics=v11"),
                menu_event("tutorial_start", 3000, payload="first_mode=logistics"),
                menu_event("tutorial_complete", 4000, payload="step=5;semantics=v11")]
        attempts, merged, summary = self.run_summary([self.export("v13-tutorial.csv", rows)])
        self.assertEqual(len(attempts), 0)
        self.assertIn("first_mode:run | 1 | 0 | 1 | 0", summary)
        self.assertIn("first_mode:logistics | 1 | 1 | 0 | 0", summary)
        self.assertNotIn("malformed_context", summary)

    def test_optional_navigation_and_not_now_has_no_rating(self):
        rows = [menu_event("home_tap", payload="target=daily"), menu_event("home_tap", 1100, payload="target=settings"),
                menu_event("language_change", 1200, payload="from=en;to=hi"), event("attempt_start", 2000), result(3000)]
        attempts, _, summary = self.run_summary([self.export("nav.csv", rows)])
        self.assertEqual(attempts[0]["rating"], "")
        self.assertIn("daily | 1/2", summary)
        self.assertIn("en -> hi | 1", summary)
        self.assertIn("0/1 | 0 | 0 | 0", summary)

    def test_tutorial_contexts_do_not_collapse_difficulty_or_rules(self):
        paths = []
        for name, changes in (("normal", {}), ("hard", {"difficulty": "Hard", "difficulty_id": "2"}),
                              ("legacy-rules", {"rules_version": "10"}),
                              ("logistics", {"mode": "logistics", "battle_mode": "2"})):
            paths.append(self.export(name + ".csv", [event("tutorial_start", payload="first_attempt", **changes),
                event("tutorial_complete", 2000, payload="step=5;semantics=v11", **changes)]))
        _, _, summary = self.run_summary(paths)
        self.assertEqual(summary.count("first_attempt | 1 | 1 | 0 | 0"), 4)
        self.assertIn("Hard/2 | first_attempt", summary)
        self.assertIn("10/1 | campaign", summary)
        self.assertIn("logistics | Normal/1 | first_attempt", summary)

    def test_cap450_and500_report_possible_not_proven_dropped_rows(self):
        paths = [self.export("450.csv", [menu_event("home_tap", index, payload="target=settings") for index in range(450)]),
                 self.export("500.csv", [event("pause", index) for index in range(500)])]
        attempts, merged, summary = self.run_summary(paths)
        self.assertEqual(len(merged), 950)
        self.assertEqual(attempts[0]["status"], "paused")
        self.assertIn("missing_start", attempts[0]["flags_json"])
        self.assertIn("500 cap reached; earlier rows MAY be dropped", summary)
        self.assertIn("450+ warning", summary)

    def test_clock_reversal_omits_wall_timing(self):
        rows = [event("attempt_start", 2000), event("pause", 1000),
                event("first_launch", 3000, payload="active_seconds=0.5", elapsed_seconds="0.5")]
        attempts, _, summary = self.run_summary([self.export("clock.csv", rows)])
        self.assertEqual(attempts[0]["first_dispatch_wall_seconds"], "")
        self.assertIn("source_clock_reversal", summary)
        self.assertEqual(attempts[0]["metric_eligible"], "false")

    def test_semantic_context_errors_are_retained_and_excluded(self):
        rows = [event("attempt_start", difficulty="Normal", difficulty_id="2"),
                event("attempt_start", 2000, mode="future"), event("first_launch", 3000, elapsed_seconds="NaN")]
        attempts, merged, _ = self.run_summary([self.export("semantic.csv", rows)])
        self.assertEqual(len(attempts), 0)
        self.assertTrue(all("malformed_context" in row["pt_flags_json"] for row in merged))

    def test_invalid_csv_rejected_before_existing_outputs_change(self):
        good = self.export("good.csv", [event("attempt_start")])
        self.run_summary([good])
        old = {path.name: path.read_bytes() for path in self.output.iterdir()}
        cases = {
            "missing": "timestamp_ms,event,detail\r\n1,foo,x\r\n",
            "duplicate": ",".join(HEADERS + ("event",)) + "\r\n",
            "short": ",".join(HEADERS) + "\r\n1,foo\r\n",
            "long": ",".join(HEADERS) + "\r\n" + ",".join(["x"] * (len(HEADERS) + 1)) + "\r\n",
            "quote": ",".join(HEADERS) + '\r\n"unterminated',
            "timestamp": None,
            "oversize_timestamp": None,
            "utf8": None,
        }
        for name, content in cases.items():
            with self.subTest(name=name):
                bad = self.root / (name + ".csv")
                if name == "timestamp":
                    self.export(bad.name, [event("attempt_start", timestamp="bad")])
                elif name == "oversize_timestamp":
                    self.export(bad.name, [event("attempt_start", timestamp="9" * 5000)])
                elif name == "utf8":
                    bad.write_bytes(b"\xff\xfe\x00")
                else:
                    bad.write_text(content, encoding="utf-8", newline="")
                with self.assertRaises(tool.SummaryError):
                    tool.summarize([good, bad], self.output)
                self.assertEqual(old, {path.name: path.read_bytes() for path in self.output.iterdir()})

    def test_directory_bom_future_column_multiline_and_determinism(self):
        row = event("future_event", payload="note=line1\nline2,\"quoted\"")
        row["extra"] = "untouched"
        first = self.export("inputs/a.csv", [row], HEADERS + ("extra",))
        second = self.export("inputs/b.csv", [event("attempt_start", 2000)])
        first.write_bytes(b"\xef\xbb\xbf" + first.read_bytes())
        attempts, merged, _ = self.run_summary([first.parent])
        self.assertEqual(len(merged), 2)
        self.assertEqual(merged[0]["extra"], "untouched")
        self.assertEqual(merged[0]["detail"], row["detail"])
        before = {path.name: path.read_bytes() for path in self.output.iterdir()}
        self.run_summary([second, first])
        self.assertEqual(before, {path.name: path.read_bytes() for path in self.output.iterdir()})

    def test_output_cannot_overwrite_input_and_mapping_errors(self):
        path = self.export("merged.csv", [event("attempt_start")])
        original = path.read_bytes()
        with self.assertRaises(tool.SummaryError):
            tool.summarize([path], self.root)
        self.assertEqual(path.read_bytes(), original)
        for assignments in (["bad mapping"], ["S01=missing.csv"], ["S01=" + str(path), "S02=" + str(path)]):
            with self.subTest(assignments=assignments), self.assertRaises(tool.SummaryError):
                tool.summarize([path], self.output, assignments)

    def test_header_only_empty_export_and_empty_directory(self):
        attempts, merged, summary = self.run_summary([self.export("empty.csv", [])])
        self.assertEqual((attempts, merged), ([], []))
        self.assertIn("Merged rows: 0", summary)
        empty = self.root / "empty-dir"
        empty.mkdir()
        with self.assertRaises(tool.SummaryError):
            tool.summarize([empty], self.output)

    def test_output_hardlink_cannot_change_original_input(self):
        path = self.export("source.csv", [event("attempt_start")])
        original = path.read_bytes()
        self.output.mkdir()
        try:
            (self.output / "merged.csv").hardlink_to(path)
        except OSError as error:
            self.skipTest("Hardlinks unsupported on this filesystem: " + str(error))
        with self.assertRaises(tool.SummaryError):
            tool.summarize([path], self.output)
        self.assertEqual(path.read_bytes(), original)

    def test_no_dispatch_fallback_or_invalid_rating_imputation(self):
        rows = [event("attempt_start"), event("first_launch", 2000, elapsed_seconds="3.0"), result(),
                event("battle_feedback", 3100, payload="rating=5", outcome="1", terminal_reason="6")]
        attempts, _, summary = self.run_summary([self.export("invalid-optional.csv", rows)])
        self.assertEqual(attempts[0]["first_dispatch_active_seconds"], "")
        self.assertEqual(attempts[0]["rating"], "")
        self.assertIn("invalid_first_dispatch_timing", attempts[0]["flags_json"])
        self.assertIn("invalid_or_multiple_ratings", attempts[0]["flags_json"])
        self.assertIn("n=0; unavailable", summary)

    def test_legacy_metadata_results_and_no_terminal_reason_loss(self):
        path = self.export("v1-metadata.csv", [event("attempt_start"), result(won=False)], tool.BASE_COLUMNS)
        attempts, merged, _ = self.run_summary([path])
        self.assertEqual(attempts[0]["status"], "loss")
        self.assertTrue(all("legacy_metadata" in row["pt_flags_json"] for row in merged))
        none = self.export("bad-terminal.csv", [event("attempt_start"), result(won=False, terminal_reason="0")])
        attempts, _, _ = self.run_summary([none])
        self.assertEqual(attempts[0]["status"], "unknown_result")

    def test_missing_identity_is_never_reconstructed_from_adjacent_rows(self):
        rows = [event("attempt_start", attempt=""), result(detail="won")]
        attempts, merged, summary = self.run_summary([self.export("anonymous-legacy.csv", rows)])
        self.assertEqual(attempts, [])
        self.assertTrue(all(row["pt_attempt_key"] == "" for row in merged))
        self.assertIn("missing_attempt_identity: 2", summary)

    def test_duplicate_ratings_and_orphan_dispatch_do_not_inflate_coverage(self):
        rows = [event("first_launch", 1000, payload="active_seconds=4.0", elapsed_seconds="4.0"), result(),
                event("battle_feedback", 3100, payload="rating=too_easy", outcome="1", terminal_reason="6"),
                event("battle_feedback", 3200, payload="rating=too_hard", outcome="1", terminal_reason="6")]
        attempts, _, summary = self.run_summary([self.export("orphan-timing.csv", rows)])
        self.assertEqual(attempts[0]["first_dispatch_active_seconds"], "4.0")
        self.assertEqual(attempts[0]["first_dispatch_wall_seconds"], "")
        self.assertEqual(attempts[0]["rating"], "")
        self.assertIn("Active sample/S", summary)
        self.assertIn("0/0 | n=0; unavailable", summary)

    def test_docs_are_ascii(self):
        for name in ("playtest-kit-v13.md", "playtest-summary-v13.md"):
            with self.subTest(name=name):
                (ROOT / "docs" / name).read_bytes().decode("ascii")

    def test_cli_requires_output_and_reports_invalid_inputs(self):
        path = self.export("S01.csv", [event("attempt_start"), result()])
        process = subprocess.run([sys.executable, "-B", str(ROOT / "tools" / "playtest_summary.py"), str(path),
                                  "--output-dir", str(self.output)], capture_output=True, text=True)
        self.assertEqual(process.returncode, 0, process.stderr)
        self.assertIn("2 merged rows, 1 attempt segments", process.stdout)
        error = io.StringIO()
        with contextlib.redirect_stderr(error), self.assertRaises(SystemExit) as raised:
            tool.main([str(path)])
        self.assertEqual(raised.exception.code, 2)
        self.assertIn("--output-dir", error.getvalue())


if __name__ == "__main__":
    unittest.main()
