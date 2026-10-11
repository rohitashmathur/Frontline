# V13.1 / 0.13.1

Current source version: `0.13.1` (version code `16`).

## Scope

1. Attempt briefing: illustrated Battle, Objective and Opponents tabs. Show the selected map layout, exact objective, records, campaign star targets and actual factions/rulers/styles. Fixed Start Attempt, Back and Field Manual actions preserve existing navigation and replacement confirmation. Non-Daily preview copy explains that neutral armies vary between attempts; Daily uses its frozen date seed.
2. Field Manual: illustrated Combat, Kings and Modes tabs, interactive interception and 25%/50%/100% deployment/count examples, captured-king growth milestones and practice access. Retain capture/reinforcement, 100/125 caps (and Run's king-cap perk), scoring, resignation, missions, Daily, Classic Run and experimental routed-map rules. Lab-origin help opens Modes; its Combat travel copy remains route-specific. Long content scrolls inside the fixed frame of navigation, not beneath its controls.
3. Home: 14 logical units of space between the coverage bar and primary/secondary battle actions, including compact layouts. Adjust fitting/fallback thresholds so campaign progress and bottom More retain their non-scrolling structure.
4. Change the splash branding tagline to Measured Tactics, with corresponding Indonesian and Hindi translations.

The footer/version metadata is 0.13.1. No simulation, AI, progression, save-format, music or unlock-code changes. Exact V7 `12345` remains available. Manual examples mutate only transient presentation state. How to Play opened from the manual returns there on completion/Skip.

## Delivery

The initial delivery was source-only, as requested. Following testing approval, the full portable suite, focused illustrated-screen checks, Python tool tests and six-configuration native Android suite passed on 2026-10-11. A signed local testing APK is available at `build/Frontline-0.13.1.apk`, with actual native captures in `docs/screenshots/v13-1/`. See [verification](verification-v13-1.md) for counts, scope, checksum and remaining physical-device checks.

No game-rule or production-code change was needed during verification. Test fixtures were corrected for the actual Lab Rules entry and compact Home's supported More fallback. The preserved `build/FrontlineV13.apk` and `docs/screenshots/v13/` still belong to 0.13.0. APKs/signing material remain excluded from Git, and no new public GitHub release asset is published.
