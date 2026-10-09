# Frontline V11 Testing Build

Offline Android prototype, version 0.11.0. Android 7.0 or newer. Install over the previous Frontline prototype to preserve local data; do not uninstall first.

- Equal starting armies for all factions in new battles; existing saves are preserved.
- Clear objective-specific mission results, explicit defeat reasons and deployment-budget warnings.
- Settings gear and Daily Mission in the top-right main-menu area.
- Offline English, Bahasa Indonesia and Hindi, with immediate saved language selection.
- Five-battle Classic Run Mode, perk councils, one same-seed retry and resumable state.
- Three separate Experimental Logistics maps with connected friendly routes, path/ETA previews and transit combat.
- V7's `12345` sector unlock code retained; unlocking does not mark sectors completed.
- Balance Lab reports, updated architecture/flow diagrams, game rules and native screenshots included in the repository.

Portable tests and Android emulator checks passed on small-phone, tall-phone and tablet layouts in all three languages. Staged UI outcomes are not human gameplay. Routing has unresolved longer-match/timeout concerns and remains Experimental. Human balance, fluent-speaker review, Run duration and physical-phone/audio/performance checks are pending.

No login, backend, network permission, advertisements or remote telemetry. Debug-signed testing build, not a Play Store release.

APK SHA-256: `63bb5e6193a8ae84077c8bb5eb6f71bacb5ada044f93cf10fb28cc0aa18f8632`.

Full evidence: [V11 delivery report](https://github.com/rohitashmathur/Frontline/blob/main/docs/verification-v11.md).
