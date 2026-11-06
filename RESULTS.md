# Native verification — 2026-11-06

## Fork staging and upstream Draft PR — 2026-11-07

- Fork created: [Johnny-Kao/openems](https://github.com/Johnny-Kao/openems); staging branch: `staging/ess-v2-cluster-power-conservation`, based on upstream `develop@1e1d35fb9b1cdadb39b83f152827faaf4acc1c0a`.
- Staged three files only: active residual correction in `TreeSolver.java` (+45 lines), solver regression tests, and `applyPower()` integration regression.
- Staging validation [run #42268281216](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42268281216): **PASS**, full ESS Core JUnit, main/test Checkstyle, 1,200 baseline differential cases, and two nested-cluster diagnostics. First run [#42267619592](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42267619592) passed JUnit but failed Checkstyle in a staging-only nested diagnostic, corrected and rerun.
- Upstream [Draft PR #4117](https://github.com/OpenEMS/openems/pull/4117) opened against `develop`; asks for early review and separately asks about documented ESS/cluster power capacity limits and 32-bit intermediate overflows. No changes to the overflow research or upstream overflow implementation.
- At creation, GitHub reported no upstream PR checks yet; staging PASS is confirmed, not an upstream CI result.

---


## Integer range contract and sub-2.147-GW target diagnostics — 2026-11-07

- P setpoints, active constraints, and ESS apparent-power capacity in the inspected V2 entry model are Java `int` values in **W**; Q constraints are `int` in **VAr**. No independent cap below the `int` type range was found in the inspected `PowerDistribution` and `TreeSolver` code paths; this does **not** rule out external component/configuration limits.
- **Unmodified upstream confirmed** [run #42264482731](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42264482731): P target **+1.0 GW**, ESS A discharge max +1.0 GW, ESS B charge min −1.3 GW / discharge max 0. `getActiveMax()` on A returned **0 W**, expected 1.0 GW, because `parentConstraint - siblingMin = 2.3 GW` overflowed Java `int`.
- **Unmodified upstream confirmed** [run #42265668583](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42265668583): 21 ESS, each ≤105 MW apparent power; 20 charge-only ESS with min −105 MW each, one discharge ESS with max +100 MW, root target **+100 MW**; `getActiveMax()` on discharge ESS returned **0 W**, expected 100 MW. Sibling sum −2.1 GW itself fits in `int`; intermediate subtraction of 100 MW − (−2.1 GW) = **2.2 GW** overflows. Both diagnostic tests produced expected assertion failures (2 failed/2 total).
- The test source is [HeadroomIntermediateOverflowTest.java](tests/candidate/HeadroomIntermediateOverflowTest.java), executed against pinned baseline `9a4b30331ac1f557d752eb8290633042144550a7` without any implementation patch.
- **Scope:** these are confirmed erroneous headroom getter values, **not yet proven to cause physical incorrect ESS commands**; practical deployment reachability and any out-of-model capacity restrictions remain unverified.
- Keep distinct from the P conservation patch and ask upstream maintainers separately about supported aggregate bounds, whether `int` arithmetic is an intended limitation, and preferred checked/saturating/long intermediate calculation approach. No upstream PR opened.

---


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
