package io.openems.edge.ess.core.power.v2;

import static org.junit.Assert.assertEquals;

import java.util.List;

import org.junit.Test;

import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyMetaEss;

/**
 * Research-only regression tests for a feasible cluster setpoint with a
 * BMS-enforced positive minimum active power on one physical ESS.
 *
 * Copy into the upstream TreeSolver test package before running:
 * io.openems.edge.ess.core/test/io/openems/edge/ess/core/power/v2/
 */
public class TreeSolverConservationRegressionTest {

    private static DummyManagedSymmetricEss ess(String id, int chargeMin, int dischargeMax) {
        return new DummyManagedSymmetricEss(id)
                .withSoc(50)
                .withAllowedChargePower(chargeMin)
                .withAllowedDischargePower(dischargeMax)
                .withMaxApparentPower(10000);
    }

    private static int activePower(PowerDistribution pd, String essId) {
        return ((PowerDistribution.Entry.Actual) pd.getEntries().get(essId)).activePowerSetPoint;
    }

    @Test
    public void testFeasibleClusterTargetWithPositiveMinimumDischarge() {
        var a = ess("essA", -10000, 10000);
        var b = ess("essB", 4000, 10000);
        var cluster = new DummyMetaEss("cluster", a, b);

        var pd = PowerDistribution.from(List.of(cluster, a, b));
        pd.setEquals("cluster", 5000);
        pd.solve();

        assertEquals("ESS A should compensate the forced minimum on B", 1000, activePower(pd, "essA"));
        assertEquals("BMS-enforced minimum", 4000, activePower(pd, "essB"));
        assertEquals("Feasible cluster power target must be conserved",
                5000, activePower(pd, "essA") + activePower(pd, "essB"));
    }

    @Test
    public void testSameTargetWithReverseMemberOrder() {
        var a = ess("essA", -10000, 10000);
        var b = ess("essB", 4000, 10000);
        var cluster = new DummyMetaEss("cluster", b, a);

        var pd = PowerDistribution.from(List.of(cluster, a, b));
        pd.setEquals("cluster", 5000);
        pd.solve();

        assertEquals("Aggregate output cannot depend on child order",
                5000, activePower(pd, "essA") + activePower(pd, "essB"));
    }

    @Test
    public void testZeroClusterTargetWithForcedDischarge() {
        var a = ess("essA", -10000, 10000);
        var b = ess("essB", 4000, 10000);
        var cluster = new DummyMetaEss("cluster", a, b);

        var pd = PowerDistribution.from(List.of(cluster, a, b));
        pd.setEquals("cluster", 0);
        pd.solve();

        assertEquals("ESS A must absorb the forced discharge", -4000, activePower(pd, "essA"));
        assertEquals("ESS B retains its hardware minimum", 4000, activePower(pd, "essB"));
        assertEquals("Feasible zero cluster target must be conserved",
                0, activePower(pd, "essA") + activePower(pd, "essB"));
    }

    @Test
    public void testOrdinaryProportionalDistributionUnchanged() {
        var a = ess("essA", -10000, 10000);
        var b = ess("essB", -10000, 10000);
        var cluster = new DummyMetaEss("cluster", a, b);

        var pd = PowerDistribution.from(List.of(cluster, a, b));
        pd.setEquals("cluster", 5000);
        pd.solve();

        assertEquals(2500, activePower(pd, "essA"));
        assertEquals(2500, activePower(pd, "essB"));
    }
}
