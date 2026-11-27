package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;
import org.junit.Test;

public class DamageLogTest
{
	@Test
	public void addsUpTheDamageSinceTheStart()
	{
		DamageLog log = new DamageLog();
		log.onDamage(90, 40, true);
		log.onDamage(100, 12, true);
		log.onDamage(100, 8, true);
		log.onDamage(100, 20, false);
		log.onDamage(105, 0, true);
		log.onDamage(130, 30, true);

		assertEquals(new DamageLog.Damage(50, 70), log.since(100, 140));
		assertEquals(new DamageLog.Damage(20, 40), log.since(100, 120));
	}

	@Test
	public void noHitsSinceTheStartIsNoDamage()
	{
		DamageLog log = new DamageLog();
		log.onDamage(90, 40, true);

		assertEquals(new DamageLog.Damage(0, 0), log.since(100, 140));
	}

	@Test
	public void unknownStartHasNoDamage()
	{
		DamageLog log = new DamageLog();
		log.onDamage(100, 40, true);

		assertNull(log.since(null, 140));
		// From before logging out, when the game's tick count was higher
		assertNull(log.since(5000, 140));
		assertNull(log.since(100, 100 + DamageLog.MAX_AGE_TICKS + 1));
	}

	@Test
	public void pruningDropsOldHits()
	{
		DamageLog log = new DamageLog();
		log.onDamage(100, 40, true);
		log.onDamage(200, 10, true);

		log.prune(150 + DamageLog.MAX_AGE_TICKS);

		assertEquals(new DamageLog.Damage(10, 10), log.since(150, 150 + DamageLog.MAX_AGE_TICKS));
	}

	@Test
	public void damageIsAnyonesHitOrPoisonButNotAHeal()
	{
		assertTrue(DamageLog.isDamage(hitsplat(HitsplatID.DAMAGE_ME)));
		assertTrue(DamageLog.isDamage(hitsplat(HitsplatID.DAMAGE_MAX_ME_CYAN)));
		assertTrue(DamageLog.isDamage(hitsplat(HitsplatID.DAMAGE_OTHER)));
		assertTrue(DamageLog.isDamage(hitsplat(HitsplatID.BLOCK_ME)));
		assertTrue(DamageLog.isDamage(hitsplat(HitsplatID.VENOM)));
		assertTrue(DamageLog.isDamage(hitsplat(HitsplatID.BURN)));
		assertFalse(DamageLog.isDamage(hitsplat(HitsplatID.HEAL)));
		assertFalse(DamageLog.isDamage(hitsplat(HitsplatID.PRAYER_DRAIN)));
	}

	private static Hitsplat hitsplat(int type)
	{
		return new Hitsplat()
		{
			@Override
			public int getHitsplatType()
			{
				return type;
			}

			@Override
			public int getAmount()
			{
				return 10;
			}

			@Override
			public int getDisappearsOnGameCycle()
			{
				return 0;
			}
		};
	}
}
