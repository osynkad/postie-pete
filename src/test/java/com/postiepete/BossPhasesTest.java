package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class BossPhasesTest
{
	private static BossPhases boss()
	{
		return new BossPhases("Boss", "Part 1", "Part 2", "Part 3");
	}

	@Test
	public void partsAreOnlyMovedOnDuringAFight()
	{
		BossPhases fight = boss();
		assertFalse(fight.isActive());
		fight.next(900);

		fight.onHit(1000);
		assertTrue(fight.isActive());
		fight.finish(1100);
		assertFalse(fight.isActive());
		fight.next(1105);

		assertEquals(Collections.singletonList(new LootEvent.Split("Part 1", 60.0)), fight.consumeSplits(1110));
	}

	@Test
	public void theLastPartIsOnlyEndedByTheKill()
	{
		BossPhases fight = boss();
		fight.onHit(0);
		fight.next(10);
		fight.next(20);
		fight.next(25);
		fight.finish(50);

		assertEquals(Arrays.asList(
			new LootEvent.Split("Part 1", 6.0),
			new LootEvent.Split("Part 2", 6.0),
			new LootEvent.Split("Part 3", 18.0)), fight.consumeSplits(51));
	}

	@Test
	public void lootWithoutTheKillCountMessageEndsThePartBeingTimed()
	{
		BossPhases fight = boss();
		fight.onHit(1000);
		fight.next(1100);

		assertEquals(Arrays.asList(
			new LootEvent.Split("Part 1", 60.0),
			new LootEvent.Split("Part 2", 12.0)), fight.consumeSplits(1120));
		assertTrue(fight.consumeSplits(1121).isEmpty());
	}

	@Test
	public void aHitAfterAFinishedFightStartsANewOne()
	{
		BossPhases fight = boss();
		fight.onHit(1000);
		fight.next(1100);
		fight.finish(1200);

		fight.onHit(2000);
		fight.finish(2050);

		assertEquals(Collections.singletonList(new LootEvent.Split("Part 1", 30.0)), fight.consumeSplits(2051));
	}

	@Test
	public void showsThePartBeingTimedAndThePartsDone()
	{
		BossPhases fight = boss();
		assertNull(fight.currentPart());
		assertTrue(fight.doneSplits().isEmpty());

		fight.onHit(1000);
		fight.next(1100);

		assertEquals("Part 2", fight.currentPart());
		assertEquals(1100, fight.currentPartStartTick());
		assertEquals(Collections.singletonList(new LootEvent.Split("Part 1", 60.0)), fight.doneSplits());
	}

	@Test
	public void theLastFightIsStillShownAfterItsLootUntilTheNextStarts()
	{
		BossPhases fight = boss();
		fight.onHit(1000);
		fight.finish(1100);
		fight.consumeSplits(1101);

		assertNull(fight.currentPart());
		assertEquals(Collections.singletonList(new LootEvent.Split("Part 1", 60.0)), fight.doneSplits());

		fight.onHit(2000);
		assertEquals("Part 1", fight.currentPart());
		assertTrue(fight.doneSplits().isEmpty());
	}

	@Test
	public void resetForgetsALeftFight()
	{
		BossPhases fight = boss();
		fight.onHit(1000);
		fight.next(1100);
		fight.reset();

		assertFalse(fight.isActive());
		assertTrue(fight.consumeSplits(1200).isEmpty());
	}

	@Test
	public void phosaniIsSplitAtHerFormChanges()
	{
		// A kill's form changes as seen in game: her first three phases came as shield forms 4, 3 and 2, and the
		// game printed a fight duration of 5:25.8
		BossPhases phosani = BossPhases.phosanisNightmare();
		PhosaniNightmare tracker = new PhosaniNightmare(phosani);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_INITIAL, NpcID.NIGHTMARE_CHALLENGE_PHASE_04, 892);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_04, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04, 959);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04, NpcID.NIGHTMARE_CHALLENGE_BLAST, 1046);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_BLAST, NpcID.NIGHTMARE_CHALLENGE_PHASE_03, 1068);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_03, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_03, 1121);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_03, NpcID.NIGHTMARE_CHALLENGE_BLAST, 1216);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_BLAST, NpcID.NIGHTMARE_CHALLENGE_PHASE_02, 1240);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_02, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_02, 1308);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_02, NpcID.NIGHTMARE_CHALLENGE_BLAST, 1399);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_BLAST, NpcID.NIGHTMARE_CHALLENGE_PHASE_05, 1423);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_05, NpcID.NIGHTMARE_CHALLENGE_DYING, 1435);
		// The kill count message comes after she started dying, and doesn't change the fight's end
		phosani.finish(1444);

		List<LootEvent.Split> splits = phosani.consumeSplits(1444);
		assertEquals(Arrays.asList(
			new LootEvent.Split("Phosani P1", 40.2),
			new LootEvent.Split("Pillar P1", 52.2),
			new LootEvent.Split("Sleepwalker P1", 13.2),
			new LootEvent.Split("Phosani P2", 31.8),
			new LootEvent.Split("Pillar P2", 57.0),
			new LootEvent.Split("Sleepwalker P2", 14.4),
			new LootEvent.Split("Phosani P3", 40.8),
			new LootEvent.Split("Pillar P3", 54.6),
			new LootEvent.Split("Sleepwalker P3", 14.4),
			new LootEvent.Split("Phosani final phase", 7.2)), splits);
		assertEquals(325.8, splits.stream().mapToDouble(LootEvent.Split::getDuration).sum(), 0.001);
	}

	@Test
	public void phosaniPillarsChargedStartTheSleepwalkerPart()
	{
		// A kill's first phase as seen in game: the game said "All four totems are fully charged." with the last
		// pillar's change, 6 ticks before she turned into her blast form
		BossPhases phosani = BossPhases.phosanisNightmare();
		PhosaniNightmare tracker = new PhosaniNightmare(phosani);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_INITIAL, NpcID.NIGHTMARE_CHALLENGE_PHASE_01, 93);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_1_DORMANT, NpcID.NIGHTMARE_TOTEM_1_READY, 209);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_2_DORMANT, NpcID.NIGHTMARE_TOTEM_2_READY, 209);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_3_DORMANT, NpcID.NIGHTMARE_TOTEM_3_READY, 209);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_4_DORMANT, NpcID.NIGHTMARE_TOTEM_4_READY, 209);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, 209);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_2_READY, NpcID.NIGHTMARE_TOTEM_2_CHARGED, 239);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_1_READY, NpcID.NIGHTMARE_TOTEM_1_CHARGED, 270);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_3_READY, NpcID.NIGHTMARE_TOTEM_3_CHARGED, 280);
		assertEquals("Pillar P1", phosani.currentPart());
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_4_READY, NpcID.NIGHTMARE_TOTEM_4_CHARGED, 305);
		// The pillars go dormant again, and her blast form changes nothing
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_4_CHARGED, NpcID.NIGHTMARE_TOTEM_4_DORMANT, 308);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_3_CHARGED, NpcID.NIGHTMARE_TOTEM_3_DORMANT, 308);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_1_CHARGED, NpcID.NIGHTMARE_TOTEM_1_DORMANT, 308);
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_2_CHARGED, NpcID.NIGHTMARE_TOTEM_2_DORMANT, 308);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_BLAST, 311);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_BLAST, NpcID.NIGHTMARE_CHALLENGE_PHASE_04, 332);
		// Charging a pillar outside a pillar part changes nothing
		tracker.onPillarChanged(NpcID.NIGHTMARE_TOTEM_1_DORMANT, NpcID.NIGHTMARE_TOTEM_1_CHARGED, 340);

		assertEquals("Phosani P2", phosani.currentPart());
		assertEquals(Arrays.asList(
			new LootEvent.Split("Phosani P1", 69.6),
			new LootEvent.Split("Pillar P1", 57.6),
			new LootEvent.Split("Sleepwalker P1", 16.2)), phosani.doneSplits());
	}

	@Test
	public void phosaniFinalPhaseFormChangesDontEndIt()
	{
		BossPhases phosani = BossPhases.phosanisNightmare();
		PhosaniNightmare tracker = new PhosaniNightmare(phosani);
		phosani.start(0);
		for (int tick = 10; tick <= 90; tick += 10)
		{
			phosani.next(tick);
		}
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_05, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04, 95);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_04, NpcID.NIGHTMARE_CHALLENGE_BLAST, 98);

		assertEquals("Phosani final phase", phosani.currentPart());
	}

	@Test
	public void phosaniWakingUpStartsANewFight()
	{
		BossPhases phosani = BossPhases.phosanisNightmare();
		PhosaniNightmare tracker = new PhosaniNightmare(phosani);
		phosani.onHit(500);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, 600);

		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_INITIAL, NpcID.NIGHTMARE_CHALLENGE_PHASE_01, 1000);
		phosani.onHit(1010);

		assertEquals("Phosani P1", phosani.currentPart());
		assertEquals(1000, phosani.currentPartStartTick());
		assertTrue(phosani.doneSplits().isEmpty());
	}

	@Test
	public void phosaniFormChangesWithoutAFightAreIgnored()
	{
		BossPhases phosani = BossPhases.phosanisNightmare();
		PhosaniNightmare tracker = new PhosaniNightmare(phosani);
		tracker.onFormChanged(NpcID.NIGHTMARE_CHALLENGE_PHASE_01, NpcID.NIGHTMARE_CHALLENGE_WEAK_PHASE_01, 600);

		assertFalse(phosani.isActive());
	}
}
