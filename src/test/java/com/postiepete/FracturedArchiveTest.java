package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class FracturedArchiveTest
{
	private static final List<String> TEAM = Arrays.asList("ExamplePlayer", "Teammate", "Other");

	@Test
	public void recordsTheBossPickedAtEachChoiceAndTheSplits()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onRunStarted();
		archive.onNpcHit(NpcID.TFA_GUARDIAN);
		archive.onBossCompleted("Archive Guardian", 123.0);
		archive.onNpcHit(NpcID.TFA_SMORK);
		archive.onBossCompleted("Bork", 141.0);
		archive.onNpcHit(NpcID.TFA_JELLY_BOSS);
		archive.onNpcHit(NpcID.TFA_DAGONHAI_MINION_2_UNSHIELDED);
		archive.onNpcHit(NpcID.TFA_OCELLUS_P1);
		archive.onBossCompleted("Ocellus", 499.8);
		archive.onRaidCompleted(3, TEAM);
		assertTrue(archive.onMessage("Teammate found something special: Obligator (uncharged)", "ExamplePlayer"));

		LootEvent.Raid raid = archive.consumeRaid(null, null);
		assertEquals(Integer.valueOf(3), raid.getTeamSize());
		assertEquals(TEAM, raid.getTeam());
		assertEquals(Arrays.asList(FracturedArchive.BORK, FracturedArchive.DREADNOTH, FracturedArchive.DAGONHAI),
			raid.getPath());
		assertEquals(Arrays.asList(
			new LootEvent.Split("Archive Guardian", 123.0),
			new LootEvent.Split("Bork", 141.0),
			new LootEvent.Split("Ocellus", 499.8)), raid.getSplits());
		// Announced after the completion message, still part of the raid
		assertEquals(Arrays.asList(new LootEvent.Purple("Teammate", "Obligator (uncharged)")), raid.getPurples());
	}

	@Test
	public void recordsEveryPurpleInTheRunAndNoneOutsideIt()
	{
		FracturedArchive archive = new FracturedArchive();
		// Not in a run: another raid's purple isn't picked up
		assertFalse(archive.onMessage("Other found something special: Twisted bow", "ExamplePlayer"));

		archive.onRunStarted();
		assertTrue(archive.onMessage("You found something special: Obligator (uncharged).", "ExamplePlayer"));
		assertTrue(archive.onMessage("Teammate found something special: Horn of Bran", "ExamplePlayer"));
		assertFalse(archive.onMessage("Challenge complete: Bork. Duration: 2:21.00", "ExamplePlayer"));
		archive.onRaidCompleted(2, TEAM.subList(0, 2));

		LootEvent.Raid raid = archive.consumeRaid(null, null);
		assertEquals(Arrays.asList(
			new LootEvent.Purple("ExamplePlayer", "Obligator (uncharged)"),
			new LootEvent.Purple("Teammate", "Horn of Bran")), raid.getPurples());
		// Consumed with the loot
		assertTrue(archive.consumeRaid(null, null).getPurples().isEmpty());
	}

	@Test
	public void runStartClearsAFailedRunsPurples()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onRunStarted();
		archive.onMessage("Teammate found something special: Horn of Bran", "ExamplePlayer");
		archive.onRunStarted();
		archive.onRaidCompleted(1, TEAM.subList(0, 1));
		assertTrue(archive.consumeRaid(null, null).getPurples().isEmpty());
	}

	@Test
	public void runStartClearsAFailedRun()
	{
		FracturedArchive archive = new FracturedArchive();
		// A wiped run
		archive.onNpcHit(NpcID.TFA_EZEKIEL);
		archive.onBossCompleted("Ezekiel", 100.0);
		archive.onNpcHit(NpcID.TFA_MOIA);

		archive.onRunStarted();
		archive.onBossCompleted("Archive Guardian", 110.0);
		archive.onNpcHit(NpcID.TFA_BORK);
		archive.onRaidCompleted(1, TEAM.subList(0, 1));

		LootEvent.Raid raid = archive.consumeRaid(null, null);
		assertEquals(Arrays.asList(FracturedArchive.BORK, null, null), raid.getPath());
		assertEquals(Arrays.asList(new LootEvent.Split("Archive Guardian", 110.0)), raid.getSplits());
	}

	@Test
	public void raidIsConsumedByTheFirstLoot()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onNpcHit(NpcID.TFA_XARTH);
		archive.onBossCompleted("Xarth", 148.2);
		archive.onRaidCompleted(2, TEAM.subList(0, 2));
		archive.consumeRaid(null, null);

		LootEvent.Raid next = archive.consumeRaid(null, null);
		assertNull(next.getTeamSize());
		assertEquals(Arrays.asList(null, null, null), next.getPath());
		assertTrue(next.getSplits().isEmpty());
	}

	@Test
	public void usesTheCurrentRunIfCompletionWasMissed()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onNpcHit(NpcID.TFA_EZEKIEL);
		archive.onNpcHit(NpcID.TFA_MOIA);
		archive.onNpcHit(NpcID.TFA_XARTH);
		archive.onBossCompleted("Xarth", 148.2);

		LootEvent.Raid raid = archive.consumeRaid(3, TEAM);
		assertEquals(Integer.valueOf(3), raid.getTeamSize());
		assertEquals(Arrays.asList(FracturedArchive.EZEKIEL, FracturedArchive.MOIA, FracturedArchive.XARTH),
			raid.getPath());
		assertEquals(Arrays.asList(new LootEvent.Split("Xarth", 148.2)), raid.getSplits());
	}
}
