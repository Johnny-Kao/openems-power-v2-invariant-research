package io.openems.edge.ess.core.power.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.util.List;
import java.util.Random;

import org.junit.jupiter.api.Test;

import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyMetaEss;

/**
 * Research-only differential contract checks against a renamed, unmodified
 * copy of the pinned upstream TreeSolver (BaselineTreeSolver).
 */
public class ScopedPowerDifferentialTest {

    private static DummyManagedSymmetricEss ess(String id, int min, int max) {
        return new DummyManagedSymmetricEss(id) //
                .withSoc(50) //
                .withAllowedChargePower(min) //
                .withAllowedDischargePower(max) //
                .withMaxApparentPower(10000);
    }

    private static int power(PowerDistribution pd, String id, boolean active) {
        var entry = (PowerDistribution.Entry.Actual) pd.getEntries().get(id);
        return active ? entry.activePowerSetPoint : entry.reactivePowerSetPoint;
    }

    private static PowerDistribution solve(int[] min, int[] max, int target, Integer reactiveTarget,
            boolean baseline) {
        var a = ess("essA", min[0], max[0]);
        var b = ess("essB", min[1], max[1]);
        var c = ess("essC", min[2], max[2]);
        var cluster = new DummyMetaEss("cluster", a, b, c);
        var pd = PowerDistribution.from(List.of(cluster, a, b, c));
        pd.setEquals("cluster", target);
        if (reactiveTarget != null) {
            pd.setReactiveEquals("cluster", reactiveTarget);
        }
        if (baseline) {
            BaselineTreeSolver.solve(pd.getEntries());
        } else {
            TreeSolver.solve(pd.getEntries());
        }
        return pd;
    }

    private static void compare(int[] min, int[] max, int target, Integer reactiveTarget, String label) {
        var oldPd = solve(min, max, target, reactiveTarget, true);
        var newPd = solve(min, max, target, reactiveTarget, false);
        var ids = new String[] { "essA", "essB", "essC" };
        var minTotal = min[0] + min[1] + min[2];
        var maxTotal = max[0] + max[1] + max[2];
        var achievable = Math.max(minTotal, Math.min(maxTotal, target));
        var oldTotal = 0;
        var newTotal = 0;

        for (int i = 0; i < ids.length; i++) {
            oldTotal += power(oldPd, ids[i], true);
            var p = power(newPd, ids[i], true);
            var q = power(newPd, ids[i], false);
            newTotal += p;
            assertTrue(p >= min[i] && p <= max[i], label + " " + ids[i] + " P bounds: " + p);
            assertTrue((long) p * p + (long) q * q <= 10000L * 10000L,
                    label + " " + ids[i] + " apparent power: P=" + p + " Q=" + q);
        }

        assertEquals(achievable, newTotal, label + " candidate total");
        if (oldTotal == achievable) {
            for (var id : ids) {
                assertEquals(power(oldPd, id, true), power(newPd, id, true),
                        label + " already-correct P changed for " + id);
                assertEquals(power(oldPd, id, false), power(newPd, id, false),
                        label + " already-correct Q changed for " + id);
            }
        }
    }

    @Test
    public void testBaselineCorrectSetpointsRemainIdenticalAndFeasibleTargetsConservePower() {
        var random = new Random(20261107L);
        for (int i = 0; i < 1200; i++) {
            var min = new int[3];
            var max = new int[3];
            for (int j = 0; j < 3; j++) {
                var x = random.nextInt(16001) - 8000;
                var y = random.nextInt(16001) - 8000;
                min[j] = Math.min(x, y);
                max[j] = Math.max(x, y);
            }
            var totalMin = min[0] + min[1] + min[2];
            var totalMax = max[0] + max[1] + max[2];
            var target = totalMin + random.nextInt(totalMax - totalMin + 1);
            compare(min, max, target, null, "random-" + i);
        }
    }

    @Test
    public void testInfeasibleTargetsAndReactiveCoupling() {
        var min = new int[] { -8000, 4000, -5000 };
        var max = new int[] { 8000, 9000, -1000 };
        compare(min, max, 30000, 2000, "infeasible-high");
        compare(min, max, -30000, -2000, "infeasible-low");
        compare(new int[] { -9000, -9000, -9000 },
                new int[] { 9000, 9000, 9000 }, 21000, 21000, "P-Q-boundary");
        compare(new int[] { 4000, 4000, -5000 },
                new int[] { 10000, 10000, -1000 }, 3000, 5000, "opposing-BMS");
    }
}
