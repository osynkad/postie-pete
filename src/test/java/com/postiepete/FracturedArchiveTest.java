package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import java.util.Arrays;
import java.util.List;
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
		List<LootEvent.Split> splits = archive.consumeSplits();
		assertNull(archive.startTick());
		assertEquals(Arrays.asList(
			new LootEvent.Split("Archive Guardian", 123.0),
			new LootEvent.Split("Bork", 141.0),
			new LootEvent.Split("Ocellus", 499.8)), splits);
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
		List<LootEvent.Split> splits = archive.consumeSplits();
		assertEquals(Arrays.asList(new LootEvent.Split("Archive Guardian", 110.0)), splits);
	}

	@Test
	public void raidIsConsumedByTheFirstLoot()
	{
		FracturedArchive archive = new FracturedArchive();
		archive.onBossCompleted("Xarth", 148.2);
		archive.onRaidCompleted();
		archive.consumeSplits();

		assertTrue(archive.consumeSplits().isEmpty());
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

		List<LootEvent.Split> splits = archive.consumeSplits();
		assertEquals(Arrays.asList(new LootEvent.Split("Xarth", 148.2)), splits);
	}
}
