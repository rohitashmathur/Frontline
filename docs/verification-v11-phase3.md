# V11 Phase 3: Classic Run MVP

Exactly five frozen Classic battles, four councils, five non-stacking perks and one same-seed retry are implemented. Perks and results remain isolated from campaign, missions, Daily and mastery. Missing battle associations never reconstruct an already-started battle. Protected switching includes READY, BATTLE, COUNCIL and RETRY_AVAILABLE, not just a live simulation.

Portable verification: `RunStateTest` passed 1,828 checks, covering all 54 council-choice paths, retry at each node, serialization, corruption and frozen maps. `V11RunSceneTest` passed 2,224 scene checks, including staged complete five-battle runs, process-style save/restores, council/retry guards, language-only changes and confirmed replacement. `V11LogTest` passed 653 checks. Staged victories are acceptance fixtures, not human gameplay or evidence of natural run length.

Verification used the phase-3 Git index exported into `build/phase3-source`, without the later routing implementation. Its complete portable suite and render previews passed. Native Android verification used `scripts/test-android-v11-run.ps1` on emulator-5580: small (480x800, 240 dpi), tall (720x1600, 320 dpi), and tablet (1200x1920, 320 dpi). Each size passed 4,418 suite checks plus 457 separate-process recovery checks: 14,625 total. English, Indonesian and Hindi READY, COUNCIL, RETRY and SUMMARY screens, accessibility labels, protected replacement and restored council selection were checked. The harness produced 81 PNGs and 81 layout reports; preferences and display configuration were restored after testing.

Native-rendered acceptance fixtures, with staged outcomes: [council](screenshots/v11/run-council.png), [summary](screenshots/v11/run-summary.png).

The renderer checks compact and tall Run Home, every node battlefield, four councils and summary in English, Indonesian and Hindi. Android preferences save run state, previous summary, battle association and progress together in one synchronous transaction. Restarting a live Run battle counts as defeat; the run retry remains the only replay token.

Human testing of the 10-15 minute duration hypothesis, perk comprehension and replay interest remains pending. No campaign rewards or aggregate farmable run score are introduced.
