package com.postiepete;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import lombok.Value;
import net.runelite.api.gameval.NpcID;
import net.runelite.client.util.Text;

/**
 * The player's fight with each boss: the tick of their first landed hit, for bosses the game prints no kill time
 * for (e.g. the God Wars Dungeon generals, the Dagannoth Kings, Barrows), the damage they dealt, and everyone's
 * damage on the boss. Keyed by the name the kill count message uses, so a kill can look up its fight. Each kill
 * takes its fight, so a respawned boss starts again from the next damage.
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
	static final String GROTESQUE_GUARDIANS = "grotesque guardians";
	static final String YAMA = "yama";

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

	// The Grotesque Guardians are Dusk and Dawn, fought together and named after themselves
	private static final Set<Integer> GROTESQUE_GUARDIANS_PARTS = Set.of(
		NpcID.GARGBOSS_DUSK_SPAWN, NpcID.GARGBOSS_DUSK_PHASE1_DEFENSIVE, NpcID.GARGBOSS_DUSK_PHASE1_TRANSITION,
		NpcID.GARGBOSS_DUSK_PHASE1_FLYTRANSITION, NpcID.GARGBOSS_DUSK_PHASE2_ATTACKING,
		NpcID.GARGBOSS_DUSK_PHASE3_DEFENSIVE, NpcID.GARGBOSS_DUSK_PHASE3_TRANSITION,
		NpcID.GARGBOSS_DUSK_PHASE4_SPAWN, NpcID.GARGBOSS_DUSK_PHASE4, NpcID.GARGBOSS_DUSK_DEATH,
		NpcID.GARGBOSS_DAWN_SPAWN, NpcID.GARGBOSS_DAWN_PHASE1, NpcID.GARGBOSS_DAWN_PHASE1_TRANSITION,
		NpcID.GARGBOSS_DAWN_PHASE3, NpcID.GARGBOSS_DAWN_DEATH);

	private final Map<String, Ongoing> fights = new HashMap<>();

	/**
	 * The kill count message name, in lower case, that hits on the NPC count towards. Every Barrows brother
	 * counts towards the "Barrows chest", the Kraken's tentacles towards the Kraken, the parts of the Royal
	 * Titans and the Hueycoatl towards them, Dusk and Dawn towards the Grotesque Guardians, and the Judge of Yama
	 * towards Yama, whose fight it is part of. Other NPCs use their own name, which is what their kill count
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
		if (GROTESQUE_GUARDIANS_PARTS.contains(npcId))
		{
			return GROTESQUE_GUARDIANS;
		}
		if (npcId == NpcID.YAMA_JUDGE_OF_YAMA)
		{
			return YAMA;
		}
		return npcName != null ? Text.removeTags(npcName).toLowerCase() : null;
	}

	/**
	 * Called on each of the player's own hitsplats on an NPC, with the NPC's {@link #key} and the damage the
	 * hitsplat shows.
	 */
	void onHit(String key, int tick, int damage)
	{
		Ongoing fight = fight(key, tick);
		if (fight == null)
		{
			return;
		}
		if (fight.firstHitTick == null)
		{
			fight.firstHitTick = tick;
		}
		fight.lastHitTick = tick;
		fight.damage += damage;
		fight.totalDamage += damage;
	}

	/**
	 * Called on each damage hitsplat on an NPC that isn't the player's own hit: another player's, or poison, venom
	 * or burn. Counts towards the fight's total damage, even before the player's first hit.
	 */
	void onOtherDamage(String key, int tick, int damage)
	{
		Ongoing fight = fight(key, tick);
		if (fight != null)
		{
			fight.totalDamage += damage;
		}
	}

	/**
	 * The fight for the key, a new one if there is none or it is over. Null for no key.
	 */
	private Ongoing fight(String key, int tick)
	{
		if (key == null)
		{
			return null;
		}
		Ongoing fight = fights.get(key);
		if (fight == null || isStale(key, fight, tick))
		{
			fight = new Ongoing();
			fights.put(key, fight);
		}
		fight.lastDamageTick = tick;
		return fight;
	}

	/**
	 * The fight for the name, or null if there is no recent one the player landed a hit in. The name is the kill
	 * count message's, or the NPC's for a boss its kill count message names differently (e.g. "The Leviathan").
	 * Consumed, so the next kill of the same boss starts from its own first hit.
	 */
	Fight take(String name, int tick)
	{
		if (name == null)
		{
			return null;
		}
		String key = name.toLowerCase();
		Ongoing fight = fights.remove(key);
		return fight != null && fight.firstHitTick != null && !isStale(key, fight, tick)
			? new Fight(fight.firstHitTick, fight.damage, fight.totalDamage)
			: null;
	}

	/**
	 * The fight with the player's most recent hit, or null if they landed none in the last {@link #STALE_TICKS}
	 * (or longer, for Barrows).
	 */
	Current current(int tick)
	{
		Map.Entry<String, Ongoing> latest = null;
		for (Map.Entry<String, Ongoing> entry : fights.entrySet())
		{
			Ongoing fight = entry.getValue();
			if (fight.firstHitTick != null && tick - fight.lastHitTick <= staleTicks(entry.getKey())
				&& (latest == null || fight.lastHitTick > latest.getValue().lastHitTick))
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

	/**
	 * Whether the fight is over: no damage from anyone for a while.
	 */
	private static boolean isStale(String key, Ongoing fight, int tick)
	{
		return tick - fight.lastDamageTick > staleTicks(key);
	}

	private static int staleTicks(String key)
	{
		return BARROWS.equals(key) ? BARROWS_STALE_TICKS : STALE_TICKS;
	}

	/**
	 * A fight taken by its kill.
	 */
	@Value
	static class Fight
	{
		/**
		 * The tick of the player's first hit.
		 */
		int firstHitTick;

		/**
		 * The damage the player's own hitsplats showed on the boss, until the kill.
		 */
		int damage;

		/**
		 * Everyone's damage on the boss in the fight, the player's included, and poison, venom and burn.
		 */
		int totalDamage;
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

	private static class Ongoing
	{
		// The player's first and last hit. Null until they land one: others can start the fight.
		Integer firstHitTick;
		int lastHitTick;

		// Anyone's last damage, which keeps the fight going
		int lastDamageTick;

		int damage;
		int totalDamage;
	}
}
