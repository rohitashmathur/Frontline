# V13 / 0.13.0

Current source version: `0.13.0` (version code `15`).

Scope: localized rendering performance; non-scrolling Home with campaign progress above bottom More; portrait/landscape tablet support; first-mode skippable tutorial; correct five-battle/four-perk Run copy; repository About update; current screenshots; anonymous playtest kit and CSV summary tool. No global AI/balance, Lab experiment, public-release signing, login, network or monetization work is included.

Home uses full mode cards where they fit, compact three-column modes when needed, and a bottom More entry for secondary actions in short/enlarged-display windows. Campaign progress and best total remain on initial Home. Other long selection/settings pages can still scroll; this is not a promise to fit sixty sectors on one screen.

Tablets with smallest width at least 600dp support sensor rotation. Controls have a maximum scale; maps consume extra width. Wide battles use left-hand controls and the full-height right-hand map. Touch, camera and accessibility coordinates share the same transform.

The first user mode action opens How to Play before battle creation. Skip/Complete returns to that action; Back cancels it without marking seen. Seen state remains the existing local preference, so upgrades keep established behavior. Tutorial examples do not alter actual armies or records.

V7 unlock code, local progress/saves, game rules and record policies are retained. Local debug signing is retained for prototype upgrades. No GitHub APK asset or public-store release is implied.

See [verification](verification-v13.md), [screenshots](screenshots.md), [playtest kit](playtest-kit-v13.md) and [summary tool](playtest-summary-v13.md).
