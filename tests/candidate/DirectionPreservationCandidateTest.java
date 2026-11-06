package io.openems.edge.ess.core.power.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;

import org.junit.jupiter.api.Test;

import io.openems.edge.ess.test.DummyManagedSymmetricEss;
import io.openems.edge.ess.test.DummyMetaEss;

public class DirectionPreservationCandidateTest {

	private static DummyManagedSymmetricEss ess(String id, int min, int max) {
		return new DummyManagedSymmetricEss(id) //
				.withSoc(50) //
				.withAllowedChargePower(min) //
				.withAllowedDischargePower(max) //
				.withMaxApparentPower(10000);
	}

	private static int activePower(PowerDistribution pd, String id) {
		return ((PowerDistribution.Entry.Actual) pd.getEntries().get(id)).activePowerSetPoint;
	}

	@Test
	public void testAvoidUnnecessaryOpposingFlowsForFeasibleDischargeTarget() {
		var a = ess("essA", -10000, 5000);
		var c = ess("essC", 0, 10000);
		var b = ess("essB", 4000, 10000);
		var cluster = new DummyMetaEss("cluster", a, c, b);

		var pd = PowerDistribution.from(List.of(cluster, a, c, b));
		pd.setEquals("cluster", 4000);
		pd.solve();

		// Candidate policy, not an existing declared V2 requirement:
		// BMS forces B to >=4000 W; no opposing flows are required.
		assertEquals(0, activePower(pd, "essA"));
		assertEquals(0, activePower(pd, "essC"));
		assertEquals(4000, activePower(pd, "essB"));
	}
}
