package io.openems.edge.ess.core.power.v2;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.util.List;
import java.util.Random;

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

	private static int reactivePower(PowerDistribution pd, String id) {
		return ((PowerDistribution.Entry.Actual) pd.getEntries().get(id)).reactivePowerSetPoint;
	}

	@Test
	public void testReactiveOnlyDistributionStillUsesOriginalQCorrection() {
		var a = ess("essA", -10000, 10000);
		var c = ess("essC", -10000, 10000);
		var b = ess("essB", -10000, 10000);
		var cluster = new DummyMetaEss("cluster", a, c, b);

		var pd = PowerDistribution.from(List.of(cluster, a, c, b));
		pd.setReactiveLessOrEquals("essA", 5000);
		pd.setReactiveGreaterOrEquals("essC", 0);
		pd.setReactiveGreaterOrEquals("essB", 4000);
		pd.setReactiveEquals("cluster", 4000);
		pd.solve();

		assertEquals(-1290, reactivePower(pd, "essA"));
		assertEquals(1290, reactivePower(pd, "essC"));
		assertEquals(4000, reactivePower(pd, "essB"));
	}


	@Test
	public void testFeasibleTargetWithSimultaneousOpposingBmsBounds() {
		var a = ess("essA", 4000, 10000);
		var b = ess("essB", 4000, 10000);
		var c = ess("essC", -5000, -1000);
		var cluster = new DummyMetaEss("cluster", a, b, c);

		var pd = PowerDistribution.from(List.of(cluster, a, b, c));
		pd.setEquals("cluster", 3000);
		pd.solve();

		// Feasible: +4000 +4000 -5000 = +3000.
		assertEquals(4000, activePower(pd, "essA"));
		assertEquals(4000, activePower(pd, "essB"));
		assertEquals(-5000, activePower(pd, "essC"));
		assertEquals(3000, activePower(pd, "essA") + activePower(pd, "essB") + activePower(pd, "essC"));
	}


	@Test
	public void testFeasibleActiveTargetsAcrossSeededBmsBounds() {
		var random = new Random(20261107L);
		for (int caseIndex = 0; caseIndex < 500; caseIndex++) {
			var a0 = random.nextInt(16001) - 8000;
			var a1 = random.nextInt(16001) - 8000;
			var b0 = random.nextInt(16001) - 8000;
			var b1 = random.nextInt(16001) - 8000;
			var c0 = random.nextInt(16001) - 8000;
			var c1 = random.nextInt(16001) - 8000;
			var amin = Math.min(a0, a1);
			var amax = Math.max(a0, a1);
			var bmin = Math.min(b0, b1);
			var bmax = Math.max(b0, b1);
			var cmin = Math.min(c0, c1);
			var cmax = Math.max(c0, c1);
			var targetMin = amin + bmin + cmin;
			var targetMax = amax + bmax + cmax;
			var target = targetMin + random.nextInt(targetMax - targetMin + 1);
			var a = ess("essA", amin, amax);
			var b = ess("essB", bmin, bmax);
			var c = ess("essC", cmin, cmax);
			var cluster = new DummyMetaEss("cluster", a, b, c);

			var pd = PowerDistribution.from(List.of(cluster, a, b, c));
			pd.setEquals("cluster", target);
			pd.solve();

			var pa = activePower(pd, "essA");
			var pb = activePower(pd, "essB");
			var pc = activePower(pd, "essC");
			assertEquals(target, pa + pb + pc, "Aggregate mismatch at case " + caseIndex);
			assertEquals(true, pa >= amin && pa <= amax, "ESS A out of bounds at case " + caseIndex);
			assertEquals(true, pb >= bmin && pb <= bmax, "ESS B out of bounds at case " + caseIndex);
			assertEquals(true, pc >= cmin && pc <= cmax, "ESS C out of bounds at case " + caseIndex);
		}
	}

}
