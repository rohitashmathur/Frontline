# V13 Independent Playtest Summary Tool

This stdlib-only sidecar reads PlaytestLog CSV exports. It does not modify the
game, inputs, builds or git. Python uses csv, argparse, json and statistics; no
package install is needed. The human protocol is in playtest-kit-v13.md.

## Run

From the Frontline workspace, in PowerShell:

```powershell
& 'C:/Users/mathu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B tools/playtest_summary.py inputs/S01.csv inputs/S02.csv --output-dir output/playtest-v13
```

Or pass a directory containing exports (CSV files only, non-recursive):

```powershell
& 'C:/Users/mathu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B tools/playtest_summary.py inputs --output-dir output/playtest-v13
```

Use a separate output directory, not the directory containing inputs. The explicit
output directory is required. Existing generated merged.csv, attempts.csv and
summary.md there are replaced; other files are untouched. Inputs are all validated
before outputs are written. A filesystem write failure can leave partial outputs;
rerun after resolving it. Symlink output targets, hardlinked input aliases and
input overwrite are rejected.

## Anonymous Scopes And Deduplication

Default: EACH input file is an independent scope, labelled with an input ordinal
and its filename. No personal information, account or device ID is required.
Use anonymous filenames: they appear in provenance. Absolute input paths are not
embedded in reports. Repeating the same resolved input path selects it once.

Overlapping checkpoints or repeated exports from a known continuous session MUST
be explicitly assigned the same scope, one --session per selected CSV:

```powershell
& 'C:/Users/mathu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B tools/playtest_summary.py inputs/S01-checkpoint.csv inputs/S01-final.csv --session 'S01-D01=inputs/S01-checkpoint.csv' --session 'S01-D01=inputs/S01-final.csv' --output-dir output/playtest-v13
```

IDs allow ASCII letters/digits/dot/underscore/hyphen, maximum 128 characters.
Assign DIFFERENT IDs to independent sessions/devices, cloned/restored profiles or
device-reset epochs. If the same battle intentionally continues across exports,
use one scope and note the continuation; do not treat every file as a new player.

Within a scope, exact original field/value rows are merged by occurrence number:
two identical pauses in one export remain two pauses; another overlapping export
does not add them again. Original header order and quoting are irrelevant; missing
columns versus present empty columns are distinct. No fuzzy timestamp matching is
performed. Exports with different schemas or changed values are not exact copies.

Identical exports in DIFFERENT scopes are retained and prominently reported, not
silently collapsed. Raw attempt IDs appearing in different scopes are also
reported and kept separate. Current GameScene generates UUIDs; a raw UUID alone
still does not prove device/session ownership. Multiple distinct starts under one
scoped identity are ambiguous, including reset/reuse collisions; the tool does
not guess a split and excludes these from metrics. Re-export with correct scopes
after checking observer notes. Startless collisions cannot always be detected.

## Outputs

- merged.csv: union of original columns with every raw parsed value preserved.
  pt_session_id gives scope; pt_sources_json lists input label and 1-based DATA
  record number for every occurrence; pt_context_json is parsed detail;
  pt_flags_json lists warnings; pt_attempt_key links usable rows to attempts.csv.
  Inputs remain byte-for-byte unchanged; merged CSV quoting/order is regenerated.
- attempts.csv: one row per scoped attempt identity AND build/code/rules/AI/mode/
  difficulty context segment. Includes observed start/result/abandon/pause counts,
  explicit result, status, completeness, metric eligibility, first-dispatch timing,
  optional rating and flags. An upgraded/restored battle may span segments; starts
  are not invented in later segments. Sector/objective/seed/run-association changes
  within a segment flag ambiguous battle context rather than combining metrics.
- summary.md: source row counts and 450/500 warnings, missing/legacy/malformed/
  unknown rows, duplicate/collision notices, context-separated descriptive metrics,
  tutorial and navigation tables, and sample limits. Empty exports produce headers
  and a zero-sample summary, not fabricated attempts.

Output order is deterministic for the same selected inputs/scopes/content,
regardless of CLI input order. There is no generation-time timestamp. Events sort
by timestamp, scope and first source record; equal-timestamp order follows the
deterministically sorted source files. Renaming files can change source ordinals,
default scopes and tie order. Clock reversal is flagged and disables wall timing.

## Event Contract

Read against PlaytestLog.java, GameScene.java, GameModel.java, CommandScreens.java
and MainActivity.java in the shared workspace. The CSV base header has 23 fields
through detail; newer metadata adds battle_mode/run_id/run_node/run_perks/
terminal_reason. Older metadata and unknown extra columns are retained.

detail begins build=...;build_code=...;attempt_id=... on contextual events. Empty
attempt_id is normal for menu events. It can contain key/value detail and a bare
payload, such as won, lost, first_attempt, replay or back. Run detail run_id is the
whole-run ID; CSV run_id is a battle association. They are not interchangeable.
Menu navigation may carry the previous battle model because hasBattle stays true.

| Metric / state | Evidence and denominator |
| --- | --- |
| Mode starts S | Eligible explicit attempt_start, CSV mode; home_tap/run_start/node_start are not battle starts |
| Results R | One unambiguous attempt_result, matching bare won/lost and numeric outcome (1=won, 2=lost); includes result-only segments |
| Victory | Explicit won with objective_type=0; includes Run/Logistics battle victories, not whole-run completion |
| Objective success | Explicit won with objective_type=1/2/3, including timed Hold/Keep and Troop Budget; not inferred from progress/timer |
| Loss | Explicit lost with outcome=2; no loss inferred from pause, elapsed time, snapshot outcome, king_loss or a timeout |
| Abandon | Explicit abandon with no result; if surrender logs abandon then an explicit loss, result takes precedence and both markers are retained |
| Paused | No terminal event; most recent pause/resume is pause; still incomplete, never loss |
| First dispatch | first_launch.active_seconds must be finite/nonnegative and match elapsed_seconds; this is active battle time, not tutorial/observer wall time |
| Dispatch coverage | Valid paired active timing / observed eligible starts S in the same segment; missing event is unknown, not a failed command |
| Dispatch wall time | Timestamp difference from explicit start to first_launch; includes tutorial/pause time; no missing-start or reversed-clock estimate |
| Tutorial T | Observed tutorial_start, paired within scope/build independently of battle identity; split first_attempt, V13 first_mode target and replay, with captured start build/code/rules/AI/mode/difficulty |
| Complete/skip | Only matched semantics=v11 tutorial_complete/tutorial_skip with step 1-5, divided by starts T; back-out/no terminal stays incomplete |
| Ratings | Optional single valid battle_feedback rating after a known result; rated/R, plus too_easy/about_right/too_hard counts; Not now creates no rating |
| Home navigation | Explicit home_tap target counts / all usable home taps H per build/code; not arrivals, task success or people |
| Language navigation | Explicit language_change from/to event counts per build/code; not comprehension or translation-quality evidence |

All result fractions use R, not started battles or all players. Incomplete
result-only segments can contribute an explicit result but remain visibly
incomplete. Means/medians/ranges use only the stated timing sample n; no percentiles
or significance claims are generated. Optional feedback coverage is self-selected.

No usable identity means no attempt reconstruction. Legacy rows without context,
unknown events and malformed details remain in merged.csv. A nonempty bounded
ASCII attempt token is accepted for older exports; no UUID-only assumption is made.
Missing build/context is labelled legacy or malformed, never filled from filename.
Malformed related rows taint that attempt's metric eligibility. Conflicting or
repeated result events, invalid/multiple dispatch events and conflicting ratings
are reported instead of selecting a convenient value.

## Errors And Limits

Invalid UTF-8, broken CSV quoting, missing/duplicate/empty headers, reserved pt_
headers, unequal field counts, invalid timestamp or empty/invalid event cause a
clear CLI error (exit 2), before existing reports change. UTF-8 BOM is supported.
Quoted commas, quotes and multiline values use the stdlib CSV parser. Invalid
semantic context (difficulty mismatch, bad numeric context, duplicate detail keys,
partial build identity, unsupported mode) is retained and flagged, not repaired.

The tool is not a complete Java model validator and cannot prove an export's
authenticity. The 500-row rolling cap drops earlier rows; exactly 500 means POSSIBLE
truncation, not proof of how many rows were lost. Below 500 also does not establish
completeness after clearing, disabled logging or restoring an old battle. Export
each session and at the 450 warning, before reset/upgrade/clear.

Unknown or legacy tutorial terminals are unmatched, not silently treated as
completion/skip. Multiple starts or contradictory context prevent causal timing
claims. Identical events from genuinely different sessions assigned the SAME
scope can still merge; scoping correctness requires observer records. Without an
exported event sequence ID, exact equal-timestamp ordering is inherently limited.

Run metrics count battles, not whole runs; run_end/node_end are preserved only.
Logistics map/configuration and mission configuration versions are not completely
exported, so this is not a map-by-map balance evaluator. No session duration,
retention, crash rate, participant count or missing-event cause is inferred.

## Focused Tests

Run only this tool's own unittest module:

```powershell
& 'C:/Users/mathu/.cache/codex-runtimes/codex-primary-runtime/dependencies/python/python.exe' -B -m unittest discover -s tests/tools -p test_playtest_summary.py -v
```

Tests create their own temporary CSVs/reports. They do not run game/build tests,
edit existing docs or touch git. No permanent sample fixture is required.
