# Native verification — 2026-11-06

## Final targeted review: nested clusters and integer range — 2026-11-07

- [Run #42263088717](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42263088717): **FAIL (expected diagnostic finding)** — 104 native ESS Core tests, 102 passed, 1 failed, 1 skipped. New nested-cluster tests passed; prior 1,200-case differential test passed.
- Two-level nested clusters covered baseline-correct per-ESS outputs and active aggregate conservation with forced BMS minima across six requested targets.
- **New pre-existing integer-boundary risk:** two ESS with 1,200,000,000 W maximum each, explicit cluster target 2,000,000,000 W. The candidate returned aggregate 0 W rather than 2,000,000,000 W. The pinned upstream `PowerDistribution.Entry.Virtual.ownActiveMax()` uses `mapToInt(...).sum()` and wraps a combined 2,400,000,000 W to a negative `int`; the scoped patch does not modify this implementation. Separate baseline-specific JUnit verification was not run for this case.
- This is an extreme synthetic range, not verified production reachability; **do not conflate it with the original BMS clipping bug**.
- Current selected patch remains [candidate-preserve-baseline-on-correct-output.patch](patches/candidate-preserve-baseline-on-correct-output.patch), unchanged in this round. The new test remains failing intentionally as evidence. No upstream PR or staging merge.
- Recommendation: keep overflow as a separate potential upstream issue, or define a realistic supported aggregate bound before changing wide-range arithmetic. Do not claim full boundary clearance.

---


## Scoped P-only differential validation — 2026-11-07

- **Selected candidate:** [preserve-baseline correction](patches/candidate-preserve-baseline-on-correct-output.patch), changing only the V2 `TreeSolver.java` implementation.
- **PASS:** [Run #42262148114](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42262148114), native full ESS Core JUnit and main/test Checkstyle.
- `ScopedPowerDifferentialTest` compiles the original upstream `TreeSolver` under `BaselineTreeSolver` beside the patched solver. It checks 1,200 seeded three-ESS combinations, requiring identical individual P and Q results when the baseline already meets the achievable aggregate P target; otherwise, the patched result must meet the achievable target and remain within individual P bounds and apparent-power limits.
- Additional deterministic differential cases cover infeasible high/low targets, opposing BMS ranges, and simultaneous P/Q constraints.
- The earlier [Run #42261403697](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42261403697) failed: the first scoped variant changed a baseline-correct per-ESS result. The new candidate avoids that by running the original allocation first, then compensating only mismatched active aggregate power. Two additional failures in that run were tests for the deliberately excluded direction-optimization policy.
- **CI caveat:** [Run #42258728682](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42258728682) had a workflow typo applying the full candidate instead of the P-only patch; do not cite it as evidence for P-only.
- The results are not formal proof, hardware certification, or a demonstrated field incident. Large aggregate values, deeper nested-cluster adversarial cases, and real device dynamics remain unverified. No upstream PR was opened.

---


## Final proportional-rebalance validation — 2026-11-07

- [Run #42240875119](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42240875119): **PASS**, full ESS Core native JUnit and main/test Checkstyle, with **nine** additional solver regressions.
- [Run #42241608639](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42241608639): **PASS**, same full suite and Checkstyle, plus end-to-end `EssPowerImpl` / `ComponentTest` / `applyPower()` callback regression.
- Latest patch changes the residual correction from sequential first-eligible ESS allocation to *proportional allocation by remaining adjustment headroom*, without changing ordinary allocations whose aggregate already matches the target.
- Three-ESS regression: cluster target +7,000 W; ESS A (-5,000..+5,000), C (-10,000..+10,000), ESS B (+4,000..+10,000) → setpoints A=+1,000, C=+2,000, B=+4,000. Reordering A/C leaves their values unchanged.
- Integration regression observes the same results via physical ESS `applyPower()` callbacks.
- Original baseline failure remains [#42235424562](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42235424562), pinned to upstream `9a4b30331ac1f557d752eb8290633042144550a7`.

### Residual policy / tradeoff

Headroom-proportional correction preserves the bounds and aggregate output for the tested feasible cases. A zero-power cluster target combined with a BMS-forced discharge may require another ESS to charge simultaneously. OpenEMS code explicitly supports opposite-direction *forced* ESS operation, but does not state whether intentionally counteracting a forced flow is universally acceptable in physical installations. Keep this as a visible design tradeoff, not an inferred guarantee. No change has been pushed upstream.

---


## Exact upstream source
`OpenEMS/openems@9a4b30331ac1f557d752eb8290633042144550a7` (Java 25, Gradle 9.8.1, `io.openems.edge.ess.core`).

## Baseline — original TreeSolver unchanged
- [Run #42235424562](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42235424562)
- Injected four upstream-native regression tests, left the production implementation unchanged.
- **Expected failure:** 4 tests, **2 failed**, 2 passed.
- Failure example: `testFeasibleClusterTargetWithPositiveMinimumDischarge` expected physical ESS A = 1,000 W; upstream returned 2,500 W. Because ESS B's minimum was 4,000 W, aggregate became 6,500 W rather than feasible 5,000 W.

## Patch — unchanged original proportions plus residual redistribution
- [Current proportional patch](patches/conservation-rebalance.patch)
- Defers physical `applySetpoint` until the candidate per-ESS setpoints are clamped and their aggregate residual has been redistributed to other free ESS within their min/max bounds.
- **No external API or config changes**. V1 unchanged. Same `TreeSolver.java` method only.

## Patched — expanded native suite and style
- [Successful run #42238671829](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42238671829)
- **PASS:** full `./gradlew :io.openems.edge.ess.core:test --no-daemon --stacktrace` including **seven additional regression tests**, and
  `./gradlew :io.openems.edge.ess.core:checkstyleMain :io.openems.edge.ess.core:checkstyleTest --no-daemon`.
- Tests exercise feasible positive target with forced discharge, reversed ESS order, zero cluster target with forced discharge, ordinary proportional distribution, negative target with forced charging, infeasible target respecting device maxima, and no-cluster-constraint preserving forced discharge.
- Earlier passing full ESS Core run with four regression tests: [#42236597555](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42236597555).

## Limitations / remaining review
- This validates the **Java V2 `BALANCE` implementation**, not real inverter delivery or grid behavior.
- The patch is a research candidate; no upstream PR and no staging fork yet.
- Proportional fairness of *individual* ESS during residual correction is now separately covered by order-invariant, three-ESS regression tests for the supported case; no universal proof of order invariance across arbitrary bounds is claimed.
- Opposing power flows used to compensate BMS-required charging or discharging may create additional internal cycling; review this physical-system tradeoff before upstream submission.
- Nested reactive-power recursion and ancestor extrema are separate candidates and intentionally excluded from the patch.
- No runtime/billing assertion beyond these GitHub Actions logs.
