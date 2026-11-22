package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import org.junit.Test;

public class FracturedArchiveTest
{
	@Test
	public void recordsTheSplits()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onRunStarted(100);
		archive.onBossCompleted("Archive Guardian", 123.0);
		archive.onBossCompleted("Bork", 141.0);
		archive.onBossCompleted("Ocellus", 499.8);
		archive.onRaidCompleted();

		assertEquals(Integer.valueOf(100), archive.startTick());
		LootEvent.Raid raid = archive.consumeRaid();
		assertNull(archive.startTick());
		assertEquals(Arrays.asList(
			new LootEvent.Split("Archive Guardian", 123.0),
			new LootEvent.Split("Bork", 141.0),
			new LootEvent.Split("Ocellus", 499.8)), raid.getSplits());
	}

	@Test
	public void runStartClearsAFailedRun()
	{
		FracturedArchive archive = new FracturedArchive();
		// A wiped run
		archive.onBossCompleted("Ezekiel", 100.0);

		archive.onRunStarted(500);
		archive.onBossCompleted("Archive Guardian", 110.0);
		archive.onRaidCompleted();

		assertEquals(Integer.valueOf(500), archive.startTick());
		LootEvent.Raid raid = archive.consumeRaid();
		assertEquals(Arrays.asList(new LootEvent.Split("Archive Guardian", 110.0)), raid.getSplits());
	}

	@Test
	public void raidIsConsumedByTheFirstLoot()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onBossCompleted("Xarth", 148.2);
		archive.onRaidCompleted();
		archive.consumeRaid();

		assertTrue(archive.consumeRaid().getSplits().isEmpty());
	}

	@Test
	public void logoutForgetsTheStartTick()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onRunStarted(100);
		archive.onLogout();
		archive.onBossCompleted("Archive Guardian", 123.0);
		archive.onRaidCompleted();

		assertNull(archive.startTick());
	}

	@Test
	public void usesTheCurrentRunIfCompletionWasMissed()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onBossCompleted("Xarth", 148.2);

		LootEvent.Raid raid = archive.consumeRaid();
		assertEquals(Arrays.asList(new LootEvent.Split("Xarth", 148.2)), raid.getSplits());
	}
}
