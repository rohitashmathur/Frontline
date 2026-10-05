# V6 Screenshots

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
