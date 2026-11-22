package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import net.runelite.api.gameval.NpcID;
import org.junit.Test;

public class FightStartsTest
{
	private static final int GRAARDOR_ID = 2215;

	@Test
	public void killTakesTheFirstHitOfItsFight()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(key, 100);
		starts.onHit(key, 105);
		starts.onHit(key, 140);

		assertEquals(Integer.valueOf(100), starts.take("General Graardor", 141));
	}

	@Test
	public void eachRespawnStartsFromItsOwnFirstHit()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(key, 100);
		assertEquals(Integer.valueOf(100), starts.take("General Graardor", 140));

		starts.onHit(key, 190);
		starts.onHit(key, 220);
		assertEquals(Integer.valueOf(190), starts.take("General Graardor", 221));
		assertNull(starts.take("General Graardor", 222));
	}

	@Test
	public void killCountNamesMatchNpcNamesInAnyCase()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(499, "Thermonuclear smoke devil"), 100);

		assertEquals(Integer.valueOf(100), starts.take("Thermonuclear Smoke Devil", 150));
	}

	@Test
	public void barrowsStartsOnTheFirstBrother()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(NpcID.BARROWS_AHRIM, "Ahrim the Blighted"), 100);
		// Walking and digging to the next brothers
		starts.onHit(FightStarts.key(NpcID.BARROWS_DHAROK, "Dharok the Wretched"), 700);
		starts.onHit(FightStarts.key(NpcID.BARROWS_VERAC, "Verac the Defiled"), 1500);

		// The chest is opened a while after the last brother
		assertEquals(Integer.valueOf(100), starts.take("Barrows chest", 2000));
	}

	@Test
	public void krakenTentaclesCountTowardsTheKraken()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(NpcID.SLAYER_KRAKEN_BOSS_TENTACLE, "Enormous Tentacle"), 100);
		starts.onHit(FightStarts.key(NpcID.SLAYER_KRAKEN_BOSS, "Kraken"), 130);

		assertEquals(Integer.valueOf(100), starts.take("Kraken", 160));
	}

	@Test
	public void bossPartsCountTowardsTheirBoss()
	{
		assertEquals("royal titans", FightStarts.key(NpcID.RT_FIRE_QUEEN, "Branda the Fire Queen"));
		assertEquals("royal titans", FightStarts.key(NpcID.RT_ICE_KING, "Eldric the Ice King"));
		assertEquals("hueycoatl", FightStarts.key(NpcID.HUEY_TAIL, "Hueycoatl tail"));
		assertEquals("hueycoatl", FightStarts.key(NpcID.HUEY_BODY_PART, "Hueycoatl body"));
	}

	@Test
	public void aHitAfterALongBreakStartsANewFight()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(key, 100);
		starts.onHit(key, 100 + FightStarts.STALE_TICKS + 1);

		assertEquals(Integer.valueOf(100 + FightStarts.STALE_TICKS + 1), starts.take("General Graardor", 400));
	}

	@Test
	public void aStaleFightIsNotTaken()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(GRAARDOR_ID, "General Graardor"), 100);

		assertNull(starts.take("General Graardor", 100 + FightStarts.STALE_TICKS + 1));
	}

	@Test
	public void pruningDropsFightsThatAreOver()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(2, "Goblin"), 100);
		starts.onHit(FightStarts.key(GRAARDOR_ID, "General Graardor"), 350);

		starts.prune(400);

		assertNull(starts.take("Goblin", 301));
		assertEquals(Integer.valueOf(350), starts.take("General Graardor", 400));
	}
}
