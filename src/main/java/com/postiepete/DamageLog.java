package com.postiepete;

import java.util.ArrayDeque;
import java.util.Deque;
import java.util.Iterator;
import lombok.Value;
import net.runelite.api.Hitsplat;
import net.runelite.api.HitsplatID;

/**
 * The damage on NPCs by game tick, the player's own and everyone's, so a raid or wave activity can add up what was
 * dealt since its start. The start is often only known later, e.g. from the first room's time, so every hit is
 * kept for a while.
 */
class DamageLog
{
	/**
	 * How long hits are kept: 4 hours, longer than any raid or wave activity run.
	 */
	static final int MAX_AGE_TICKS = 24000;

	private final Deque<Hit> hits = new ArrayDeque<>();

	/**
	 * Whether the hitsplat is damage that takes the NPC's hitpoints: anyone's hit, or poison, venom or burn. Not
	 * heals or other effects.
	 */
	static boolean isDamage(Hitsplat hitsplat)
	{
		switch (hitsplat.getHitsplatType())
		{
			case HitsplatID.POISON:
			case HitsplatID.VENOM:
			case HitsplatID.BURN:
				return true;
			default:
				return hitsplat.isMine() || hitsplat.isOthers();
		}
	}

	/**
	 * Called on each damage hitsplat on an NPC (see {@link #isDamage}), with the damage it shows and whether it is
	 * the player's own hit. Hits on the same tick are kept as one.
	 */
	void onDamage(int tick, int damage, boolean mine)
	{
		if (damage <= 0)
		{
			return;
		}
		Hit last = hits.peekLast();
		if (last == null || last.tick != tick)
		{
			last = new Hit(tick);
			hits.addLast(last);
		}
		last.total += damage;
		if (mine)
		{
			last.dealt += damage;
		}
	}

	/**
	 * The damage dealt from the start tick until the tick, or null if it isn't known: there is no start, or it is
	 * older than the hits kept or after the tick (e.g. from before logging out).
	 */
	Damage since(Integer startTick, int tick)
	{
		if (startTick == null || startTick > tick || tick - startTick > MAX_AGE_TICKS)
		{
			return null;
		}
		int dealt = 0;
		int total = 0;
		Iterator<Hit> newestFirst = hits.descendingIterator();
		while (newestFirst.hasNext())
		{
			Hit hit = newestFirst.next();
			if (hit.tick < startTick)
			{
				break;
			}
			if (hit.tick <= tick)
			{
				dealt += hit.dealt;
				total += hit.total;
			}
		}
		return new Damage(dealt, total);
	}

	/**
	 * Drops hits older than {@link #MAX_AGE_TICKS}.
	 */
	void prune(int tick)
	{
		while (!hits.isEmpty() && tick - hits.peekFirst().tick > MAX_AGE_TICKS)
		{
			hits.removeFirst();
		}
	}

	void reset()
	{
		hits.clear();
	}

	@Value
	static class Damage
	{
		/**
		 * The player's own damage.
		 */
		int dealt;

		/**
		 * Everyone's damage, the player's included, and poison, venom and burn.
		 */
		int total;
	}

	private static class Hit
	{
		final int tick;
		int dealt;
		int total;

		Hit(int tick)
		{
			this.tick = tick;
		}
	}
}
