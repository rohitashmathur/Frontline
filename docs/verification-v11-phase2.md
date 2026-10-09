# V11 Phase 2: Balance Lab

Model baseline: Phase 1 commit `6fc1b66`. The committed raw matrix records the V11 working model, identical to this model baseline. The measurement harness and human checklist are separate from gameplay changes.

`V11BalanceTest` passed 5,574 checks. The 720-match Classic matrix used maps 0/12/36, Normal difficulty, matched rules 10/11, seeds 0/7/1000, 0.05-second steps and a 120-second cap. Starting seats and controller styles rotate independently, with resignation both enabled and disabled. Two runs produced byte-identical CSV and summaries.

Results: 120 focus wins, 136 focus losses, 464 unresolved timeouts, no draws. All 256 completed matches ended naturally; none completed through resignation. Denominators are all attempted matches, not only completions. A timeout is never an inferred victory.

See [CLI and reproducibility instructions](../tools/BALANCE_LAB.md), [raw matrix and summary](../tools/balance-reports/v11-lab/summary.md), and [human checklist](playtest-v11.md). Human sessions, comprehension, enjoyment and device performance remain pending. The large timeout share and Province counterattack success rate are explicit remaining balance questions, not validated shipping targets.
