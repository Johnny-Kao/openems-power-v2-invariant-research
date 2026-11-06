# Native verification — 2026-11-06

## Exact upstream source
`OpenEMS/openems@9a4b30331ac1f557d752eb8290633042144550a7` (Java 25, Gradle 9.8.1, `io.openems.edge.ess.core`).

## Baseline — original TreeSolver unchanged
- [Run #42235424562](https://github.com/Johnny-Kao/openems-power-v2-invariant-research/actions/runs/42235424562)
- Injected four upstream-native regression tests, left the production implementation unchanged.
- **Expected failure:** 4 tests, **2 failed**, 2 passed.
- Failure example: `testFeasibleClusterTargetWithPositiveMinimumDischarge` expected physical ESS A = 1,000 W; upstream returned 2,500 W. Because ESS B's minimum was 4,000 W, aggregate became 6,500 W rather than feasible 5,000 W.

## Patch — unchanged original proportions plus residual redistribution
- [Patch](patches/conservation-rebalance.patch)
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
- Proportional fairness of *individual* ESS during residual correction remains order-sensitive; only the aggregate conservation invariant is targeted.
- Opposing power flows used to compensate BMS-required charging or discharging may create additional internal cycling; review this physical-system tradeoff before upstream submission.
- Nested reactive-power recursion and ancestor extrema are separate candidates and intentionally excluded from the patch.
- No runtime/billing assertion beyond these GitHub Actions logs.
