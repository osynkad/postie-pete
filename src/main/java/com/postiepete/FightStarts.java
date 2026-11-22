package com.postiepete;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.util.Text;

/**
 * The tick of the player's first landed hit on each boss, for bosses the game prints no kill time for (e.g. the
 * God Wars Dungeon generals, the Dagannoth Kings, Barrows). Keyed by the name the kill count message uses, so a
 * kill can look up when its fight started. Each kill takes its start, so a respawned boss starts again from the
 * next first hit.
 */
class FightStarts
{
	/**
	 * A fight with no hit for this long is over, e.g. the player left, so the next hit starts a new one.
	 */
	static final int STALE_TICKS = 200;

	/**
	 * Barrows brothers are fought one at a time, with walking and digging in between, and the chest is
	 * opened after the last one, so a run's start is kept for longer.
	 */
	static final int BARROWS_STALE_TICKS = 1500;

	static final String BARROWS = "barrows chest";
	static final String KRAKEN = "kraken";
	static final String ROYAL_TITANS = "royal titans";
	static final String HUEYCOATL = "hueycoatl";

	private static final Set<Integer> BARROWS_BROTHERS = Set.of(
		NpcID.BARROWS_AHRIM, NpcID.BARROWS_DHAROK, NpcID.BARROWS_GUTHAN,
		NpcID.BARROWS_KARIL, NpcID.BARROWS_TORAG, NpcID.BARROWS_VERAC);

	// The Kraken is disturbed and fought along with its tentacles, which have other names
	private static final Set<Integer> KRAKEN_PARTS = Set.of(
		NpcID.SLAYER_KRAKEN_BOSS, NpcID.SLAYER_KRAKEN_BOSS_WHIRLPOOL,
		NpcID.SLAYER_KRAKEN_BOSS_TENTACLE, NpcID.SLAYER_KRAKEN_BOSS_TENTACLE_WHIRLPOOL);

	// The two Royal Titans are named after themselves, not the kill count message
	private static final Set<Integer> ROYAL_TITANS_PARTS = Set.of(
		NpcID.RT_FIRE_QUEEN, NpcID.RT_FIRE_QUEEN_INACTIVE, NpcID.RT_ICE_KING, NpcID.RT_ICE_KING_INACTIVE);

	// The Hueycoatl is fought at its head, tail and body parts
	private static final Set<Integer> HUEYCOATL_PARTS = Set.of(
		NpcID.HUEY_HEAD, NpcID.HUEY_HEAD_INVULNERABLE, NpcID.HUEY_HEAD_ENRAGED,
		NpcID.HUEY_TAIL, NpcID.HUEY_TAIL_BROKEN, NpcID.HUEY_BODY_PART, NpcID.HUEY_BODY_PART_BROKEN);

	private final Map<String, Fight> fights = new HashMap<>();

	/**
	 * The kill count message name, in lower case, that hits on the NPC count towards. Every Barrows brother
	 * counts towards the "Barrows chest", the Kraken's tentacles towards the Kraken, and the parts of the Royal
	 * Titans and the Hueycoatl towards them. Other NPCs use their own name, which is what their kill count
	 * message calls them (bosses with several forms keep one name).
	 */
	static String key(int npcId, String npcName)
	{
		if (BARROWS_BROTHERS.contains(npcId))
		{
			return BARROWS;
		}
		if (KRAKEN_PARTS.contains(npcId))
		{
			return KRAKEN;
		}
		if (ROYAL_TITANS_PARTS.contains(npcId))
		{
			return ROYAL_TITANS;
		}
		if (HUEYCOATL_PARTS.contains(npcId))
		{
			return HUEYCOATL;
		}
		return npcName != null ? Text.removeTags(npcName).toLowerCase() : null;
	}

	/**
	 * Called on each of the player's own hitsplats on an NPC, with the NPC's {@link #key}.
	 */
	void onHit(String key, int tick)
	{
		if (key == null)
		{
			return;
		}
		Fight fight = fights.get(key);
		if (fight == null || isStale(key, fight, tick))
		{
			fight = new Fight(tick);
			fights.put(key, fight);
		}
		fight.lastHitTick = tick;
	}

	/**
	 * The tick of the first hit of the fight for the kill count message's name, or null if there is no recent
	 * one. Consumed, so the next kill of the same boss starts from its own first hit.
	 */
	Integer take(String killName, int tick)
	{
		if (killName == null)
		{
			return null;
		}
		String key = killName.toLowerCase();
		Fight fight = fights.remove(key);
		return fight != null && !isStale(key, fight, tick) ? fight.firstHitTick : null;
	}

	/**
	 * The fight with the most recent hit that isn't over, or null if there is none.
	 */
	Current current(int tick)
	{
		Map.Entry<String, Fight> latest = null;
		for (Map.Entry<String, Fight> entry : fights.entrySet())
		{
			if (!isStale(entry.getKey(), entry.getValue(), tick)
				&& (latest == null || entry.getValue().lastHitTick > latest.getValue().lastHitTick))
			{
				latest = entry;
			}
		}
		return latest != null ? new Current(latest.getKey(), latest.getValue().firstHitTick) : null;
	}

	/**
	 * Drops fights that are over, e.g. ones on NPCs that aren't bosses.
	 */
	void prune(int tick)
	{
		fights.entrySet().removeIf(entry -> isStale(entry.getKey(), entry.getValue(), tick));
	}

	void reset()
	{
		fights.clear();
	}

	private static boolean isStale(String key, Fight fight, int tick)
	{
		int staleTicks = BARROWS.equals(key) ? BARROWS_STALE_TICKS : STALE_TICKS;
		return tick - fight.lastHitTick > staleTicks;
	}

	@Value
	static class Current
	{
		/**
		 * The fight's {@link #key}.
		 */
		String key;
		int firstHitTick;
	}

	private static class Fight
	{
		final int firstHitTick;
		int lastHitTick;

		Fight(int firstHitTick)
		{
			this.firstHitTick = firstHitTick;
			this.lastHitTick = firstHitTick;
		}
	}
}
