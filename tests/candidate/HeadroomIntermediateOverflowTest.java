package io.openems.edge.ess.core.power.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyMetaEss;

/** Diagnostic: intermediate parent-headroom arithmetic at a sub-int cluster target. */
public class HeadroomIntermediateOverflowTest {

    @Test
    public void testParentHeadroomWithTargetBelowIntMax() {
        // Both the root's achievable maximum and requested target are only 1 GW.
        // The sibling can charge up to 1.3 GW, but is not forced to charge.
        // Parent target - sibling minimum = 2.3 GW, which does not fit into int.
        var a = new DummyManagedSymmetricEss("a")
                .withSoc(50)
                .withAllowedChargePower(0)
                .withAllowedDischargePower(1_000_000_000)
                .withMaxApparentPower(1_000_000_000);
        var b = new DummyManagedSymmetricEss("b")
                .withSoc(50)
                .withAllowedChargePower(-1_300_000_000)
                .withAllowedDischargePower(0)
                .withMaxApparentPower(1_300_000_000);
        var root = new DummyMetaEss("root", a, b);
        var pd = PowerDistribution.from(List.of(root, a, b));
        pd.setEquals("root", 1_000_000_000);
        var entry = pd.getEntries().get("a");

        assertEquals(1_000_000_000, entry.ownActiveMax(), "ESS A hardware bound");
        assertEquals(1_000_000_000, pd.getEntries().get("root").ownActiveMax(),
                "cluster aggregate max is below int limit");
        assertEquals(1_000_000_000, entry.getActiveMax(),
                "the cluster target can be reached with ESS A alone");
    }
}
