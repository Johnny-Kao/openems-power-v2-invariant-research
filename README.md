# OpenEMS ESS Power V2 — distribution invariant research

**Status:** research only; no upstream patch or production-impact claim.

Upstream: https://github.com/OpenEMS/openems

Relevant code:
- `io.openems.edge.ess.core/src/io/openems/edge/ess/core/power/v2/TreeSolver.java`
- `io.openems.edge.ess.core/src/io/openems/edge/ess/core/power/v2/PowerDistribution.java`
- `io.openems.edge.ess.core/test/io/openems/edge/ess/core/power/v2/TreeSolverTest.java`
- `io.openems.edge.ess.core/test/io/openems/edge/ess/core/power/v2/PowerDistributionHandlerV2Test.java`

## Candidate

The V2 `BALANCE` strategy's `TreeSolver.distributeProportional()` appears to lose aggregate conservation after individual ESS power bounds are applied. The routine clamps each ESS setpoint but never redistributes the resulting residual to other ESS units with headroom.

Feasible example (watts):

| ESS | Minimum | Maximum | Possible setpoint |
|---|---:|---:|---:|
| essA | -10,000 | 10,000 | 1,000 |
| essB | 4,000 | 10,000 | 4,000 |
| **Cluster** | | **Requested 5,000** | **5,000 feasible** |

For ordering A then B, code inspection predicts A=2,500 and B=4,000: **total 6,500**, not 5,000. Reversing children predicts total 5,000.

The positive minimum is explicitly covered by upstream's `testEssClusterForceDischarge()` as an allowed BMS-induced state. **No physical hardware behavior has been demonstrated.**

## Contract checklist — protect these before proposing a fix

1. Physical ESS setpoints must satisfy their allowed charge/discharge bounds and apparent power constraints.
2. Aggregate active power must equal a feasible explicit cluster target. For infeasible requests, preserve the upstream best-effort/clamping semantics.
3. Existing proportional allocation must remain unchanged where already valid.
4. `BALANCE` should not produce a different **aggregate** output merely because child order changes.
5. Preserve BMS-induced forced charging and discharging, including sign conventions.
6. Preserve standalone ESS, one-member cluster, no-cluster-constraint, and nested-cluster behavior.
7. Preserve the independent reactive-power pass and the apparent-power circle.
8. Do not change V1, external APIs, configurations, `PowerDistributionHandler` interfaces, or `EssCluster` binding.
9. Do not assert that `NotSolved` detects all aggregate mismatches without verified evidence.
10. Regression must fail on a pinned original upstream commit, pass after the minimal change, and keep existing upstream tests passing.

## Tests

`tests/TreeSolverConservationRegressionTest.java` is intended to be copied into the matching upstream Java test package; it uses upstream test APIs, not a reimplemented solver. Initial assertions are **expected to fail on unmodified upstream**.

No GitHub Actions workflow is automatically triggered from this repository. Do not run billable CI without checking runner/billing eligibility. Keep original-fail and patched-pass evidence separately.

## Current evidence level

- Upstream source and existing regression tests inspected.
- Independent isolated Java model reported the conservation failure; this is **not** OpenEMS JUnit verification.
- A native JUnit regression test is prepared for integration; execution and results remain pending.
