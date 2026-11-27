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
		starts.onHit(key, 100, 0);
		starts.onHit(key, 105, 0);
		starts.onHit(key, 140, 0);

		assertEquals(100, starts.take("General Graardor", 141).getFirstHitTick());
	}

	@Test
	public void eachRespawnStartsFromItsOwnFirstHit()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(key, 100, 0);
		assertEquals(100, starts.take("General Graardor", 140).getFirstHitTick());

		starts.onHit(key, 190, 0);
		starts.onHit(key, 220, 0);
		assertEquals(190, starts.take("General Graardor", 221).getFirstHitTick());
		assertNull(starts.take("General Graardor", 222));
	}

	@Test
	public void killCountNamesMatchNpcNamesInAnyCase()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(499, "Thermonuclear smoke devil"), 100, 0);

		assertEquals(100, starts.take("Thermonuclear Smoke Devil", 150).getFirstHitTick());
	}

	@Test
	public void barrowsStartsOnTheFirstBrother()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(NpcID.BARROWS_AHRIM, "Ahrim the Blighted"), 100, 0);
		// Walking and digging to the next brothers
		starts.onHit(FightStarts.key(NpcID.BARROWS_DHAROK, "Dharok the Wretched"), 700, 0);
		starts.onHit(FightStarts.key(NpcID.BARROWS_VERAC, "Verac the Defiled"), 1500, 0);

		// The chest is opened a while after the last brother
		assertEquals(100, starts.take("Barrows chest", 2000).getFirstHitTick());
	}

	@Test
	public void krakenTentaclesCountTowardsTheKraken()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(NpcID.SLAYER_KRAKEN_BOSS_TENTACLE, "Enormous Tentacle"), 100, 0);
		starts.onHit(FightStarts.key(NpcID.SLAYER_KRAKEN_BOSS, "Kraken"), 130, 0);

		assertEquals(100, starts.take("Kraken", 160).getFirstHitTick());
	}

	@Test
	public void bossPartsCountTowardsTheirBoss()
	{
		assertEquals("royal titans", FightStarts.key(NpcID.RT_FIRE_QUEEN, "Branda the Fire Queen"));
		assertEquals("royal titans", FightStarts.key(NpcID.RT_ICE_KING, "Eldric the Ice King"));
		assertEquals("hueycoatl", FightStarts.key(NpcID.HUEY_TAIL, "Hueycoatl tail"));
		assertEquals("hueycoatl", FightStarts.key(NpcID.HUEY_BODY_PART, "Hueycoatl body"));
		assertEquals("grotesque guardians", FightStarts.key(NpcID.GARGBOSS_DUSK_PHASE4, "Dusk"));
		assertEquals("grotesque guardians", FightStarts.key(NpcID.GARGBOSS_DAWN_PHASE1, "Dawn"));
		assertEquals("yama", FightStarts.key(NpcID.YAMA_JUDGE_OF_YAMA, "Judge of Yama"));
		assertEquals("yama", FightStarts.key(NpcID.YAMA, "Yama"));
	}

	@Test
	public void killTakesTheDamageOfItsFight()
	{
		FightStarts starts = new FightStarts();
		String graardor = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(graardor, 100, 32);
		starts.onHit(FightStarts.key(2216, "Sergeant Strongstack"), 102, 15);
		starts.onHit(graardor, 104, 0);
		starts.onHit(graardor, 108, 41);

		assertEquals(new FightStarts.Fight(100, 73, 73), starts.take("General Graardor", 110));
	}

	@Test
	public void othersDamageCountsTowardsTheTotalFromBeforeTheFirstHit()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onOtherDamage(key, 90, 25);
		starts.onHit(key, 100, 30);
		starts.onOtherDamage(key, 102, 6);

		// The kill time still runs from the player's own first hit
		assertEquals(new FightStarts.Fight(100, 30, 61), starts.take("General Graardor", 110));
	}

	@Test
	public void othersDamageAloneIsNoFight()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onOtherDamage(key, 90, 25);

		assertNull(starts.current(100));
		assertNull(starts.take("General Graardor", 100));
	}

	@Test
	public void theNextFightCountsItsOwnDamage()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(key, 100, 50);
		starts.take("General Graardor", 120);
		starts.onHit(key, 150, 20);

		assertEquals(new FightStarts.Fight(150, 20, 20), starts.take("General Graardor", 160));
	}

	@Test
	public void bossPartsAddUpTheirDamage()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(NpcID.GARGBOSS_DUSK_PHASE1_DEFENSIVE, "Dusk"), 100, 10);
		starts.onHit(FightStarts.key(NpcID.GARGBOSS_DAWN_PHASE1, "Dawn"), 110, 25);

		assertEquals(new FightStarts.Fight(100, 35, 35), starts.take("Grotesque Guardians", 200));
	}

	@Test
	public void aHitAfterALongBreakStartsANewFight()
	{
		FightStarts starts = new FightStarts();
		String key = FightStarts.key(GRAARDOR_ID, "General Graardor");
		starts.onHit(key, 100, 0);
		starts.onHit(key, 100 + FightStarts.STALE_TICKS + 1, 0);

		assertEquals(100 + FightStarts.STALE_TICKS + 1, starts.take("General Graardor", 400).getFirstHitTick());
	}

	@Test
	public void aStaleFightIsNotTaken()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(GRAARDOR_ID, "General Graardor"), 100, 0);

		assertNull(starts.take("General Graardor", 100 + FightStarts.STALE_TICKS + 1));
	}

	@Test
	public void pruningDropsFightsThatAreOver()
	{
		FightStarts starts = new FightStarts();
		starts.onHit(FightStarts.key(2, "Goblin"), 100, 0);
		starts.onHit(FightStarts.key(GRAARDOR_ID, "General Graardor"), 350, 0);

		starts.prune(400);

		assertNull(starts.take("Goblin", 301));
		assertEquals(350, starts.take("General Graardor", 400).getFirstHitTick());
	}
}
