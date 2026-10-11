#!/usr/bin/env python3
"""Summarize explicit Frontline playtest events without reconstructing gameplay."""

import argparse
from collections import Counter, defaultdict
import csv
from dataclasses import dataclass, field
import json
import math
from pathlib import Path
import statistics
import sys


BASE_COLUMNS = (
    "timestamp_ms", "event", "sector", "sector_index", "difficulty", "difficulty_id",
    "mode", "rules_version", "ai_version", "seed", "seed_known", "history_known",
    "starting_king_lost", "elapsed_seconds", "objective_type", "objective_target",
    "objective_seconds", "objective_progress", "deployment_budget", "challenge_id",
    "daily_date", "outcome", "detail",
)
METADATA_COLUMNS = ("battle_mode", "run_id", "run_node", "run_perks", "terminal_reason")
MODES = {"campaign", "challenge", "daily", "run", "logistics"}
DIFFICULTIES = {"0": "Easy", "1": "Normal", "2": "Hard"}
RATINGS = ("too_easy", "about_right", "too_hard")
KNOWN_EVENTS = {
    "attempt_start", "attempt_result", "first_launch", "battle_feedback", "pause", "resume",
    "abandon", "retry", "next_sector", "king_gain", "king_loss", "routing_refusal",
    "tutorial_start", "tutorial_step", "tutorial_practice", "tutorial_complete", "tutorial_skip",
    "home_tap", "language_change", "run_start", "run_end", "run_abandon", "node_start",
    "node_end", "council_offer", "perk_pick",
}
KNOWN_EVENTS.update(reason + "_" + action for reason in (
    "new_attempt", "new_logistics", "new_run", "restart", "run_restart", "run_abandon"
) for action in ("request", "confirm", "cancel"))
BARE_DETAILS = {
    "attempt_result": {"won", "lost"}, "tutorial_start": {"first_attempt", "replay"},
    "tutorial_practice": {"capture", "reinforcement"}, "resume": {"back"},
    "abandon": {"new_attempt", "new_logistics", "new_run", "restart", "run_restart", "run_abandon"},
}
ATTEMPT_COLUMNS = (
    "attempt_key", "session_id", "attempt_id", "build", "build_code", "rules_version",
    "ai_version", "mode", "difficulty", "difficulty_id", "rows", "start_count",
    "result_count", "abandon_count", "pause_count", "resume_count", "start_timestamp_ms",
    "last_timestamp_ms", "observed_result", "status", "complete", "metric_eligible",
    "first_dispatch_active_seconds", "first_dispatch_wall_seconds", "rating", "flags_json",
)
MERGE_COLUMNS = ("pt_session_id", "pt_sources_json", "pt_context_json", "pt_flags_json", "pt_attempt_key")


class SummaryError(ValueError):
    """An input or output cannot be processed safely."""


def encoded(value):
    return json.dumps(value, ensure_ascii=True, sort_keys=True, separators=(",", ":"))


def integer(value, minimum=0, maximum=None):
    if not value or len(value) > 19 or not value.isascii() or not value.isdecimal():
        return None
    result = int(value)
    return result if result >= minimum and (maximum is None or result <= maximum) else None


def number(value):
    try:
        result = float(value)
    except (TypeError, ValueError):
        return None
    return result if math.isfinite(result) and 0 <= result <= 86400 else None


def token(value):
    return bool(value) and len(value) <= 128 and value.isascii() and all(
        char.isalnum() or char in "._-/:" for char in value
    )


@dataclass
class Row:
    raw: dict
    scope: str
    sources: list
    order: tuple
    context: dict = field(default_factory=dict)
    bare: list = field(default_factory=list)
    flags: set = field(default_factory=set)
    group: tuple = ()
    attempt_key: str = ""

    @property
    def event(self):
        return self.raw["event"]

    @property
    def timestamp(self):
        return int(self.raw["timestamp_ms"])


def inspect_row(row):
    for part in row.raw["detail"].split(";"):
        if not part:
            if row.raw["detail"]:
                row.flags.add("malformed_detail")
            continue
        if "=" not in part:
            row.bare.append(part)
            if part not in BARE_DETAILS.get(row.event, set()) and row.event in KNOWN_EVENTS:
                row.flags.add("malformed_detail")
            continue
        key, value = part.split("=", 1)
        if not key or not key.isascii() or not all(c.isalnum() or c == "_" for c in key):
            row.flags.add("malformed_detail")
        elif key in row.context:
            row.flags.add("malformed_detail")
        else:
            row.context[key] = value
    if row.event not in KNOWN_EVENTS:
        row.flags.add("unknown_event")
    required = {"build", "build_code", "attempt_id"}
    present = required.intersection(row.context)
    if not present:
        row.flags.add("legacy_context")
    elif present != required or not row.context.get("build") or integer(row.context.get("build_code"), maximum=2147483647) is None:
        row.flags.add("malformed_context")
    if any(part in required for part in row.bare) or "malformed_detail" in row.flags:
        row.flags.add("malformed_context")
    if not all(column in row.raw and row.raw[column] != "" for column in ("battle_mode", "terminal_reason")) and row.raw["mode"] != "menu":
        row.flags.add("legacy_metadata")
    mode = row.raw["mode"]
    if mode not in MODES | {"menu"}:
        row.flags.add("malformed_context")
    if integer(row.raw["rules_version"], maximum=1000) is None or integer(row.raw["ai_version"], maximum=1000) is None:
        row.flags.add("malformed_context")
    if mode != "menu":
        if row.raw["rules_version"] == "0":
            row.flags.add("legacy_rules")
        objective = integer(row.raw["objective_type"], maximum=3)
        sector = integer(row.raw["sector_index"], maximum=59)
        if (row.raw["difficulty_id"] not in DIFFICULTIES
                or DIFFICULTIES.get(row.raw["difficulty_id"]) != row.raw["difficulty"]
                or sector is None or integer(row.raw["sector"], minimum=1, maximum=60) != (sector + 1 if sector is not None else None)
                or objective is None or number(row.raw["elapsed_seconds"]) is None
                or integer(row.raw["outcome"], maximum=2) is None):
            row.flags.add("malformed_context")
        if (mode in {"campaign", "run", "logistics"} and objective != 0
                or mode in {"challenge", "daily"} and objective not in {1, 2, 3}
                or mode == "daily" and not row.raw["daily_date"]
                or mode != "daily" and row.raw["daily_date"]):
            row.flags.add("malformed_context")
        battle_mode = row.raw.get("battle_mode", "")
        if battle_mode and battle_mode != {"run": "1", "logistics": "2"}.get(mode, "0"):
            row.flags.add("malformed_context")
        if row.raw.get("terminal_reason", "") and integer(row.raw["terminal_reason"], maximum=6) is None:
            row.flags.add("malformed_context")
        if not token(row.context.get("attempt_id", "")):
            row.flags.add("missing_attempt_identity")
    elif row.event in {"attempt_start", "attempt_result", "first_launch", "battle_feedback", "pause", "resume", "abandon"}:
        row.flags.update({"malformed_context", "missing_attempt_identity"})
    row.group = (
        row.context.get("build", "unknown"), row.context.get("build_code", "unknown"),
        row.raw["rules_version"], row.raw["ai_version"], mode,
        row.raw["difficulty"], row.raw["difficulty_id"],
    )


def discover(inputs, sessions, output_dir):
    paths = set()
    for name in inputs:
        path = Path(name).resolve()
        if path.is_dir():
            paths.update(child.resolve() for child in path.iterdir() if child.is_file() and child.suffix.lower() == ".csv")
        elif path.is_file():
            paths.add(path)
        else:
            raise SummaryError("Input does not exist or is not a file/directory: " + str(path))
    if not paths:
        raise SummaryError("No CSV files found")
    assigned = {}
    for assignment in sessions:
        scope, separator, name = assignment.partition("=")
        if not separator or not token(scope) or any(char in scope for char in "/:"):
            raise SummaryError("--session must be ANONYMOUS_ID=CSV_PATH (letters, digits, dot, underscore, hyphen)")
        path = Path(name).resolve()
        if path not in paths:
            raise SummaryError("--session path is not a selected CSV input: " + str(path))
        if path in assigned and assigned[path] != "session:" + scope:
            raise SummaryError("Conflicting session IDs for " + str(path))
        assigned[path] = "session:" + scope
    destinations = {(output_dir / name).resolve() for name in ("merged.csv", "attempts.csv", "summary.md")}
    if destinations.intersection(paths):
        raise SummaryError("Output would overwrite an input. Use a separate output directory.")
    if any(destination.exists() and destination.samefile(path) for destination in destinations for path in paths):
        raise SummaryError("Output aliases an input file. Use a separate output directory.")
    if output_dir.exists() and not output_dir.is_dir():
        raise SummaryError("Output path is not a directory: " + str(output_dir))
    ordered = sorted(paths, key=lambda path: (str(path).casefold(), str(path)))
    return [(path, assigned.get(path, "file:{:03d}:{}".format(index, path.name)),
             "input{:03d}:{}".format(index, path.name)) for index, path in enumerate(ordered, 1)]


def load_exports(sources):
    exports = []
    columns = list(BASE_COLUMNS)
    extras = set()
    for index, (path, scope, label) in enumerate(sources):
        rows = []
        try:
            with path.open("r", encoding="utf-8-sig", newline="") as handle:
                reader = csv.reader(handle, strict=True)
                header = next(reader, None)
                if (not header or len(set(header)) != len(header) or any(not name for name in header)
                        or not set(BASE_COLUMNS).issubset(header) or any(name.startswith("pt_") for name in header)):
                    raise SummaryError("{}: invalid header; expected unique Frontline CSV columns including {}".format(label, ", ".join(BASE_COLUMNS)))
                extras.update(set(header) - set(BASE_COLUMNS))
                for record, values in enumerate(reader, 1):
                    if len(values) != len(header):
                        raise SummaryError("{}: record {} (line {}): expected {} columns, got {}".format(label, record, reader.line_num, len(header), len(values)))
                    raw = dict(zip(header, values))
                    if integer(raw["timestamp_ms"], maximum=9223372036854775807) is None or not raw["event"] or any(c in raw["event"] for c in "\r\n\x00"):
                        raise SummaryError("{}: record {}: invalid timestamp_ms or event".format(label, record))
                    row = Row(raw, scope, [{"file": label, "record": record}], (index, record))
                    inspect_row(row)
                    rows.append(row)
        except (OSError, UnicodeError, csv.Error) as error:
            raise SummaryError("{}: invalid/unreadable CSV: {}".format(label, error)) from error
        clock_reversed = any(a.timestamp > b.timestamp for a, b in zip(rows, rows[1:]))
        if clock_reversed:
            for row in rows:
                row.flags.add("source_clock_reversal")
        exports.append({"label": label, "scope": scope, "rows": rows, "clock_reversed": clock_reversed})
    columns.extend(column for column in METADATA_COLUMNS if column in extras)
    columns.extend(sorted(extras - set(METADATA_COLUMNS)))
    return exports, columns


def merge_exports(exports):
    # Multiset union retains repeated rows inside an export, merging occurrence N
    # only with occurrence N in other exports explicitly assigned the same scope.
    merged = {}
    raw_count = 0
    for export in exports:
        occurrences = Counter()
        for row in export["rows"]:
            raw_count += 1
            raw_key = tuple(sorted(row.raw.items()))
            occurrences[raw_key] += 1
            key = (row.scope, raw_key, occurrences[raw_key])
            if key in merged:
                existing = merged[key]
                existing.sources.extend(row.sources)
                existing.flags.update(row.flags)
            else:
                merged[key] = row
    rows = sorted(merged.values(), key=lambda row: (row.timestamp, row.scope, row.order))
    return rows, raw_count - len(rows)


def explicit_result(row):
    if row.event != "attempt_result" or row.bare not in (["won"], ["lost"]):
        return "unknown"
    if row.bare == ["lost"] and row.raw["outcome"] == "2" and row.raw.get("terminal_reason", "") in {"", "1", "2", "3", "4", "5"}:
        return "loss"
    if row.bare == ["won"] and row.raw["outcome"] == "1" and row.raw.get("terminal_reason", "") in {"", "5", "6"}:
        return "victory" if row.raw["objective_type"] == "0" else "objective_success"
    return "unknown"


def assemble_attempts(rows):
    grouped = defaultdict(list)
    identity_rows = defaultdict(list)
    malformed_identities = set()
    for row in rows:
        identity = row.context.get("attempt_id", "")
        if token(identity) and "malformed_context" in row.flags:
            malformed_identities.add((row.scope, identity))
        if row.raw["mode"] in MODES and token(identity) and "malformed_context" not in row.flags:
            key = (row.scope, identity) + row.group
            grouped[key].append(row)
            identity_rows[(row.scope, identity)].append(row)
    attempts = []
    for index, (key, events) in enumerate(sorted(grouped.items()), 1):
        scope, identity, *group = key
        flags = set()
        related = identity_rows[(scope, identity)]
        if len({row.group for row in related}) > 1:
            flags.add("context_changed_for_identity")
        if sum(row.event == "attempt_start" for row in related) > 1:
            flags.add("multiple_starts_identity_collision")
        if (scope, identity) in malformed_identities:
            flags.add("malformed_rows_excluded")
        starts = [row for row in events if row.event == "attempt_start"]
        results = [row for row in events if row.event == "attempt_result"]
        abandons = [row for row in events if row.event == "abandon"]
        launches = [row for row in events if row.event == "first_launch"]
        ratings = [row for row in events if row.event == "battle_feedback"]
        if not starts:
            flags.add("missing_start")
        if not results and not abandons:
            flags.add("missing_end")
        verdicts = {explicit_result(row) for row in results}
        observed = next(iter(verdicts)) if len(verdicts) == 1 else "conflicting" if verdicts else ""
        if results and (len(results) != 1 or observed == "unknown"):
            flags.add("ambiguous_result")
        if results and abandons:
            flags.add("abandon_also_observed")
        if starts and any(row.timestamp < starts[0].timestamp for row in events):
            flags.add("events_before_start")
        signatures = {tuple(row.raw.get(column, "") for column in (
            "sector_index", "objective_type", "objective_target", "objective_seconds", "deployment_budget",
            "challenge_id", "daily_date", "seed", "seed_known", "run_id", "run_node", "run_perks",
        )) for row in events}
        if len(signatures) > 1:
            flags.add("battle_context_changed")
        unsafe = {"multiple_starts_identity_collision", "malformed_rows_excluded", "battle_context_changed", "events_before_start"}
        eligible = not bool(flags & unsafe)
        state_events = [row.event for row in events if row.event in {"pause", "resume"}]
        status = observed if results and "ambiguous_result" not in flags else "unknown_result" if results else (
            "abandon" if abandons else "paused" if state_events and state_events[-1] == "pause" else "incomplete"
        )
        if not eligible:
            status = "ambiguous_identity_or_context"
        active, wall, rating = "", "", ""
        if launches:
            if len(launches) == 1:
                launch = launches[0]
                value = number(launch.context.get("active_seconds"))
                if value is not None and math.isclose(value, number(launch.raw["elapsed_seconds"]), abs_tol=0.001):
                    active = value
                    if len(starts) == 1 and launch.timestamp >= starts[0].timestamp and not any("source_clock_reversal" in row.flags for row in events):
                        wall = (launch.timestamp - starts[0].timestamp) / 1000
                else:
                    flags.add("invalid_first_dispatch_timing")
            else:
                flags.add("multiple_first_dispatch_events")
        if ratings:
            if len(ratings) == 1 and ratings[0].context.get("rating") in RATINGS:
                rating = ratings[0].context["rating"]
                if not results or ratings[0].timestamp < results[0].timestamp:
                    flags.add("rating_without_prior_result")
            else:
                flags.add("invalid_or_multiple_ratings")
        attempt_key = "attempt_{:05d}".format(index)
        for row in events:
            row.attempt_key = attempt_key
        attempts.append(dict(zip(ATTEMPT_COLUMNS, (
            attempt_key, scope, identity, *group, len(events), len(starts), len(results), len(abandons),
            sum(row.event == "pause" for row in events), sum(row.event == "resume" for row in events),
            starts[0].timestamp if starts else "", max(row.timestamp for row in events), observed, status,
            "true" if len(starts) == 1 and status in {"victory", "objective_success", "loss", "abandon"} else "false",
            "true" if eligible else "false", active, wall, rating, encoded(sorted(flags)),
        ))))
    return attempts


def cell(value):
    return str(value).replace("|", "\\|").replace("\r", " ").replace("\n", " ")


def table(lines, headers, records):
    lines.append("| " + " | ".join(headers) + " |")
    lines.append("| " + " | ".join("---" for _ in headers) + " |")
    for record in records:
        lines.append("| " + " | ".join(cell(value) for value in record) + " |")
    lines.append("")


def timing(values):
    if not values:
        return "n=0; unavailable"
    return "n={}; mean={:.3f}; median={:.3f}; min={:.3f}; max={:.3f}".format(
        len(values), statistics.mean(values), statistics.median(values), min(values), max(values)
    )


def tutorial_records(rows):
    records, pending = [], {}
    orphan = Counter()
    for row in rows:
        if row.event not in {"tutorial_start", "tutorial_complete", "tutorial_skip"} or "malformed_context" in row.flags:
            continue
        key = (row.scope, row.context.get("build", "unknown"), row.context.get("build_code", "unknown"))
        if row.event == "tutorial_start":
            purpose = row.bare[0] if row.bare in (["first_attempt"], ["replay"]) else "unknown"
            first_mode = row.context.get("first_mode", "")
            if not row.bare and token(first_mode):
                purpose = "first_mode:" + first_mode
            record = {"group": row.group, "purpose": purpose, "end": "missing"}
            records.append(record)
            pending[key] = record
        elif key in pending and row.context.get("semantics") == "v11" and integer(row.context.get("step"), 1, 5) is not None:
            pending.pop(key)["end"] = "complete" if row.event == "tutorial_complete" else "skip"
        else:
            orphan[row.event] += 1
    return records, orphan


def render_summary(exports, rows, attempts, duplicate_count):
    flags = Counter(flag for row in rows for flag in row.flags)
    lines = ["# V13 Playtest Summary", "", "Observational evidence only. No missing start/end, idle gap, session cutoff or timeout is inferred as a loss.", "",
             "## Inputs And Coverage", "",
             "Input rows: {}. Merged rows: {}. Overlap occurrences removed within explicit scopes: {}.".format(sum(len(export["rows"]) for export in exports), len(rows), duplicate_count),
             "Attempt identity segments: {} (unique scoped identities: {}). Unassigned rows: {}.".format(len(attempts), len({(attempt["session_id"], attempt["attempt_id"]) for attempt in attempts}), sum(not row.attempt_key for row in rows)),
             "Incomplete segments (missing start or end): {}. Ambiguous identity/context segments: {}.".format(sum("missing_start" in json.loads(attempt["flags_json"]) or "missing_end" in json.loads(attempt["flags_json"]) for attempt in attempts), sum(attempt["metric_eligible"] == "false" for attempt in attempts)), ""]
    table(lines, ("Input", "Scope", "Rows", "Coverage warning"), (
        (export["label"], export["scope"], len(export["rows"]),
         "500 cap reached; earlier rows MAY be dropped" if len(export["rows"]) == 500 else
         "450+ warning; export before more play" if 450 <= len(export["rows"]) < 500 else
         "nonstandard >500 rows" if len(export["rows"]) > 500 else "retention completeness unknown") for export in exports
    ))
    for flag in sorted(flags):
        lines.append("- {}: {} merged rows.".format(flag, flags[flag]))
    identical = defaultdict(list)
    for export in exports:
        fingerprint = tuple(tuple(sorted(row.raw.items())) for row in export["rows"])
        if fingerprint:
            identical[fingerprint].append(export)
    for matches in identical.values():
        if len({export["scope"] for export in matches}) > 1:
            lines.append("- Identical exports in distinct scopes retained separately: {}. Assign the same --session only if these are known copies/overlaps.".format(
                ", ".join(cell(export["label"]) for export in matches)))
    identity_scopes = defaultdict(set)
    for attempt in attempts:
        identity_scopes[attempt["attempt_id"]].add(attempt["session_id"])
    repeated = sum(len(scopes) > 1 for scopes in identity_scopes.values())
    lines.extend(["- Raw attempt IDs present in multiple scopes: {}; kept separate (possible copies, restored profiles or collisions).".format(repeated), "",
                  "## Battle Attempts By Context", "",
                  "Segments separate build/build code, rules, AI, CSV mode and difficulty. A restored attempt spanning builds can create incomplete segments; do not sum these as new starts.",
                  "Results denominator R includes only unambiguous explicit attempt_result segments, including result-only segments. Starts S count only observed eligible attempt_start. Abandon and paused are separate, never losses.", ""])
    buckets = defaultdict(list)
    group_columns = ("build", "build_code", "rules_version", "ai_version", "mode", "difficulty", "difficulty_id")
    for attempt in attempts:
        buckets[tuple(attempt[column] for column in group_columns)].append(attempt)
    table(lines, ("Build/code", "Rules/AI", "Mode", "Difficulty/id", "Segments", "Starts S", "Results R", "Victory/R", "Objective success/R", "Loss/R", "Abandon", "Paused", "Incomplete", "Ambiguous"), (
        (group[0] + "/" + group[1], group[2] + "/" + group[3], group[4], group[5] + "/" + group[6], len(items),
         sum(item["start_count"] == 1 and item["metric_eligible"] == "true" for item in items),
         sum(item["status"] in {"victory", "objective_success", "loss"} for item in items),
         *[sum(item["status"] == status for item in items) for status in ("victory", "objective_success", "loss", "abandon", "paused")],
         sum(item["complete"] == "false" for item in items),
         sum(item["metric_eligible"] == "false" or item["status"] == "unknown_result" for item in items))
        for group, items in sorted(buckets.items())
    ))
    lines.extend(["## First Dispatch And Optional Ratings", "",
                  "Timing sample is paired to an observed start in the same eligible segment. Active seconds come only from first_launch.active_seconds; wall seconds include tutorial/pause time and require a non-reversing source clock. Missing dispatch events are not failures. Values are seconds.",
                  "Ratings are optional battle_feedback, not inferred from difficulty. Coverage is rated/R; no rating means unknown, not About right. Only one valid rating after a known result is included.", ""])
    timing_rows = []
    for group, items in sorted(buckets.items()):
        eligible = [item for item in items if item["metric_eligible"] == "true"]
        started = [item for item in eligible if item["start_count"] == 1]
        active = [item["first_dispatch_active_seconds"] for item in started if item["first_dispatch_active_seconds"] != ""]
        wall = [item["first_dispatch_wall_seconds"] for item in started if item["first_dispatch_wall_seconds"] != ""]
        result_items = [item for item in eligible if item["status"] in {"victory", "objective_success", "loss"}]
        rated = [item for item in result_items if item["rating"] and "rating_without_prior_result" not in json.loads(item["flags_json"])]
        timing_rows.append((" / ".join(group), "{}/{}".format(len(active), len(started)), timing(active), timing(wall),
                            "{}/{}".format(len(rated), len(result_items)), *[sum(item["rating"] == rating for item in rated) for rating in RATINGS]))
    table(lines, ("Context", "Active sample/S", "Active timing", "Wall timing", "Rated/R", "Too easy", "About right", "Too hard"), timing_rows)
    tutorials, orphan = tutorial_records(rows)
    tutorial_groups = defaultdict(list)
    for record in tutorials:
        tutorial_groups[record["group"] + (record["purpose"],)].append(record)
    lines.extend(["## Tutorials", "", "Denominator T is observed tutorial_start, paired independently of battle identity within scope/build. First-attempt, V13 first-mode target and replay are separate, using the rules/AI/mode/difficulty captured at tutorial start. A first_mode target is an explicit entry intent, not proof of arriving or playing. Only semantics=v11 terminal events with step 1-5 are paired; backing out is incomplete, not skip.", ""])
    table(lines, ("Build/code", "Rules/AI", "Captured mode", "Difficulty/id", "Purpose", "Starts T", "Complete/T", "Skip/T", "No matched terminal/T"), (
        (group[0] + "/" + group[1], group[2] + "/" + group[3], group[4], group[5] + "/" + group[6], group[7], len(items),
         *[sum(item["end"] == end for item in items) for end in ("complete", "skip", "missing")])
        for group, items in sorted(tutorial_groups.items())
    ))
    lines.extend(["Unmatched/unsupported tutorial terminal rows: complete={}; skip={}. No start is invented.".format(orphan["tutorial_complete"], orphan["tutorial_skip"]), "",
                  "## Navigation (Optional Event Counts)", "", "Counts are taps/changes, not successful navigation or distinct people. home_tap can retain a previous battle's CSV mode; it is not a mode start. Home target shares use all usable home_tap rows in the same build/code as denominator H.", ""])
    taps, changes, home_totals = Counter(), Counter(), Counter()
    invalid_navigation = 0
    for row in rows:
        if row.event not in {"home_tap", "language_change"}:
            continue
        group = (row.context.get("build", "unknown"), row.context.get("build_code", "unknown"))
        if "malformed_context" in row.flags:
            invalid_navigation += 1
        elif row.event == "home_tap" and row.context.get("target"):
            taps[group + (row.context["target"],)] += 1
            home_totals[group] += 1
        elif row.event == "language_change" and row.context.get("from") and row.context.get("to"):
            changes[group + (row.context["from"] + " -> " + row.context["to"],)] += 1
        else:
            invalid_navigation += 1
    table(lines, ("Build/code", "Home target", "Taps/H"), ((key[0] + "/" + key[1], key[2], "{}/{}".format(count, home_totals[key[:2]])) for key, count in sorted(taps.items())))
    table(lines, ("Build/code", "Language change", "Events"), ((key[0] + "/" + key[1], key[2], count) for key, count in sorted(changes.items())))
    lines.extend(["Unusable navigation rows: {}.".format(invalid_navigation), "", "## Sample Limits", "",
                  "- All figures describe exported opt-in events, not a representative population. No participant count is derived from sessions or attempt IDs.",
                  "- Small samples have descriptive mean/median/range only; no balance, causal, significance or percentile claims are made.",
                  "- Missing rows can reflect the 500-row cap, clearing, disabled logging, upgrades, restored battles, crashes or partial exports; the cause is unknown.",
                  "- No process lifecycle events or generic turn timeout exist in this contract. Returning home, retry/request/cancel, node_end, run_end and snapshot outcome fields are not battle results.",
                  "- Run mode figures are individual battles, not whole-run completion. Logistics map identity and mission configuration versions are not fully exported; compare observer notes as well.",
                  "- Raw fields and occurrence provenance remain in merged.csv. Semantic problems are flagged, never repaired. Original inputs are not modified.", ""])
    return "\n".join(lines)


def summarize(inputs, output_dir, sessions=()):
    output_dir = Path(output_dir).resolve()
    sources = discover(inputs, sessions, output_dir)
    exports, columns = load_exports(sources)
    rows, duplicate_count = merge_exports(exports)
    attempts = assemble_attempts(rows)
    summary = render_summary(exports, rows, attempts, duplicate_count)
    # All inputs are validated before creating or replacing any outputs.
    output_dir.mkdir(parents=True, exist_ok=True)
    for name in ("merged.csv", "attempts.csv", "summary.md"):
        target = output_dir / name
        if target.is_symlink() or target.exists() and not target.is_file():
            raise SummaryError("Unsafe output target: " + str(target))
    with (output_dir / "merged.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=columns + list(MERGE_COLUMNS), lineterminator="\n")
        writer.writeheader()
        for row in rows:
            writer.writerow(dict(row.raw, pt_session_id=row.scope, pt_sources_json=encoded(row.sources),
                                 pt_context_json=encoded(row.context), pt_flags_json=encoded(sorted(row.flags)), pt_attempt_key=row.attempt_key))
    with (output_dir / "attempts.csv").open("w", encoding="utf-8", newline="") as handle:
        writer = csv.DictWriter(handle, fieldnames=ATTEMPT_COLUMNS, lineterminator="\n")
        writer.writeheader()
        writer.writerows(attempts)
    (output_dir / "summary.md").write_text(summary, encoding="utf-8", newline="\n")
    return len(rows), len(attempts)


def main(argv=None):
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("inputs", nargs="+", help="Exported CSV files or directories (non-recursive *.csv)")
    parser.add_argument("--output-dir", required=True, help="Explicit directory for merged.csv, attempts.csv and summary.md")
    parser.add_argument("--session", action="append", default=[], metavar="ID=CSV_PATH",
                        help="Scope overlapping exports to the same anonymous device/session; repeat for each CSV")
    args = parser.parse_args(argv)
    try:
        rows, attempts = summarize(args.inputs, args.output_dir, args.session)
    except (SummaryError, OSError) as error:
        parser.error(str(error))
    print("Wrote merged.csv, attempts.csv and summary.md: {} merged rows, {} attempt segments.".format(rows, attempts))
    return 0


if __name__ == "__main__":
    sys.exit(main())
