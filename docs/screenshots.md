# Native Screenshots

## V10

These are actual Android 15 Canvas captures from the final V10 native harness, not mockups. Small phone: 480x800; tall phone: 720x1600; tablet: 1200x1920. The fixture disables audio, enables opt-in logs and uses the V7 review code. The result fixture shortens the retain-king timer to 0.2 seconds only for repeatable UI verification; it is not evidence of ordinary mission completion time. Dense-map selection fixtures set 125/100 caps to verify MAX and readable selected counts.

| Continue and Protected Attempts | Mission Briefing |
| --- | --- |
| <img src="screenshots/v10-menu.png" width="240" alt="V10 Continue Battle is primary" /> | <img src="screenshots/v10-briefing.png" width="240" alt="Star targets, personal best and opponent style before an attempt" /> |

| Objective Missions | Offline Daily |
| --- | --- |
| <img src="screenshots/v10-challenges.png" width="240" alt="Separate hold-king, home-guard and budget missions" /> | <img src="screenshots/v10-daily.png" width="240" alt="Dated fixed-Normal daily mission with UTC reset countdown" /> |

| Mastery | Settings and Local Export |
| --- | --- |
| <img src="screenshots/v10-mastery.png" width="240" alt="Measured badges, progress and cosmetic themes" /> | <img src="screenshots/v10-settings.png" width="240" alt="Next-attempt difficulty, V7 code and opt-in CSV logging" /> |

| Dense Fit | Dense Zoom |
| --- | --- |
| <img src="screenshots/v10-dense-fit.png" width="240" alt="Faction symbols, MAX and large selected-count footer" /> | <img src="screenshots/v10-dense-zoom.png" width="240" alt="Zoomed dense battlefield and sent/remaining preview" /> |

| Result Fixture | Tablet Zoom |
| --- | --- |
| <img src="screenshots/v10-result.png" width="240" alt="Actual versus target time, personal best and one factual insight" /> | <img src="screenshots/v10-tablet-zoom.png" width="300" alt="Native tablet dense selection" /> |

Run `scripts/test-android-v10.ps1` after installing the current build on the named test emulator. Its separate instrumentation package is removed afterwards, original preferences and viewport settings are restored, and CSV export is verified through the actual Activity callback and an external content URI. Full captures/CSV fixtures remain under ignored `build/device/v10-native/`. Screenshots cannot establish physical-phone touch accuracy or enjoyment.

## Historical V6

These are actual Android Canvas captures from the Android 15 test emulator, not UI mockups. Most are 720x1280; the small-phone and tablet captures use their labelled viewport sizes. Boost, cap, surrender and campaign-ending captures use deterministic test saves to make the states reproducible.

| Main Menu | Booster Tutorial |
| --- | --- |
| <img src="screenshots/main-menu.png" width="240" alt="Frontline main menu" /> | <img src="screenshots/tutorial-boost.png" width="240" alt="Interactive four-king growth booster tutorial" /> |

| Five Opponents | Persistent Four-King Bonus |
| --- | --- |
| <img src="screenshots/battle-five-opponents.png" width="240" alt="Sector 60 with five rival factions" /> | <img src="screenshots/battle-four-kings.png" width="240" alt="Persistent 3x boost while holding four enemy kings" /> |

| New Story Chapter | Finite Troop Caps |
| --- | --- |
| <img src="screenshots/chapter-6.png" width="240" alt="Iron Frontier campaign chapter" /> | <img src="screenshots/troop-caps.png" width="240" alt="125-troop king and 100-troop ordinary tile" /> |

| Rival Surrender | Campaign Complete |
| --- | --- |
| <img src="screenshots/rival-surrender.png" width="240" alt="Victory after a hopeless rival resigns" /> | <img src="screenshots/campaign-complete.png" width="240" alt="Sector 60 completes the expanded campaign" /> |

| Small Phone (480x800) | Tablet (1200x1920) |
| --- | --- |
| <img src="screenshots/battle-small-phone.png" width="240" alt="Expanded map on a small phone" /> | <img src="screenshots/battle-tablet.png" width="300" alt="Expanded map on a tablet" /> |

Run `scripts/test.ps1` for portable render previews, then `scripts/setup-emulator.ps1`, `scripts/start-test-device.ps1` and `scripts/device-smoke-test.ps1` for native captures under `build/device/`. The smoke test only uses and clears the named `emulator-5580` test device, never a connected physical phone.
