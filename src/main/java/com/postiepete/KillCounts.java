package com.postiepete;

import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.runelite.client.util.Text;

/**
 * Remembers the game's last kill count message, e.g. "Your TzKal-Zuk kill count is: 12." or "Your completed
 * Theatre of Blood: Hard Mode count is: 40.", so the loot that follows can say which kill it came from. The
 * Loot Tracker only knows the loot's own name (the NPC that dropped it, or the chest), which isn't always the
 * boss: raid chests are named without their mode, and some bosses' loot comes from another NPC.
 */
class KillCounts
{
	// RuneLite's chat commands plugin's kill count pattern, on the message with its colour tags. Covers kills
	// ("kill count"), raids ("Your completed ... count"), Wintertodt ("subdued"), the Gauntlet ("completion
	// count") and chests ("Barrows chest count"). The red count is what tells these apart from other "is:" messages.
	static final Pattern KILL_COUNT_PATTERN = Pattern.compile(
		"Your (?<pre>completion count for |subdued |completed )?(?:<col=[0-9a-f]{6}>)?(?<boss>.+?)(?:</col>)? "
			+ "(?<post>(?:(?:kill|harvest|lap|completion|success|Total Ticket) )?(?:count )?)is: ?"
			+ "(?:<col=[0-9a-f]{6}>|@.+?@)(?<kc>[0-9,]+)</col>");

	// Raids say "Your completed <raid> count"; their kill count only goes with the reward chest
	private static final String RAID_PREFIX = "completed ";

	// Kills the game reports no loot for at the kill, so without this they would never be sent: the Fight Caves
	// and the Inferno give their cape and Tokkul as a reward, and Wintertodt and Tempoross rewards are claimed
	// separately (their reward loot isn't one per kill). Only these get a kill without loot: other kill count
	// messages (agility laps, the Sepulchre, activities whose chest is opened later) would be noise or counted twice.
	static final Set<String> KILLS_WITHOUT_LOOT = Set.of("TzKal-Zuk", "TzTok-Jad", "Wintertodt", "Tempoross");

	private final int npcMaxAgeTicks;
	private final int chestMaxAgeTicks;
	private final int withoutLootTicks;

	private LootEvent.Kill last;
	private boolean lastIsRaid;
	private int lastTick;

	/**
	 * @param npcMaxAgeTicks   how long after the message NPC loot can still claim it
	 * @param chestMaxAgeTicks how long after the message a reward chest can still claim it (chests are opened later)
	 * @param withoutLootTicks how long after the message a kill in {@link #KILLS_WITHOUT_LOOT} waits for loot
	 *                         before it counts as a kill without loot; loot comes on the message's own tick
	 */
	KillCounts(int npcMaxAgeTicks, int chestMaxAgeTicks, int withoutLootTicks)
	{
		this.npcMaxAgeTicks = npcMaxAgeTicks;
		this.chestMaxAgeTicks = chestMaxAgeTicks;
		this.withoutLootTicks = withoutLootTicks;
	}

	/**
	 * Remembers the kill count if the message is one, replacing any earlier one. Returns it, or null.
	 *
	 * @param message the chat message as the game sent it, with its colour tags
	 */
	LootEvent.Kill onMessage(String message, int tick)
	{
		Matcher matcher = KILL_COUNT_PATTERN.matcher(message);
		if (!matcher.find())
		{
			return null;
		}

		String name = Text.removeTags(matcher.group("boss")).trim();
		int count;
		try
		{
			count = Integer.parseInt(matcher.group("kc").replace(",", ""));
		}
		catch (NumberFormatException e)
		{
			return null;
		}

		last = new LootEvent.Kill(name, count);
		lastIsRaid = RAID_PREFIX.equals(matcher.group("pre"));
		lastTick = tick;
		return last;
	}

	/**
	 * The kill count for NPC loot: the last message if it is recent and not a raid's, which belongs to the chest.
	 * Consumed, so it can't be attached to a later kill.
	 */
	LootEvent.Kill claimForNpcLoot(int tick)
	{
		if (last == null || lastIsRaid || tick - lastTick > npcMaxAgeTicks)
		{
			return null;
		}
		return consume();
	}

	/**
	 * The kill count for a reward chest: the last message if it is recent enough and names the chest, e.g. the
	 * "Theatre of Blood" chest after "Theatre of Blood: Hard Mode", or the "Barrows" chest after "Barrows chest".
	 * Consumed, so it can't be attached to a later chest.
	 */
	LootEvent.Kill claimForChest(String chestName, int tick)
	{
		if (last == null || chestName == null || tick - lastTick > chestMaxAgeTicks
			|| !last.getName().toLowerCase().startsWith(chestName.toLowerCase()))
		{
			return null;
		}
		return consume();
	}

	/**
	 * A kill in {@link #KILLS_WITHOUT_LOOT} that no loot claimed in time, to be sent as a kill without loot.
	 * Consumed, so it is sent once. Call every tick.
	 */
	LootEvent.Kill takeKillWithoutLoot(int tick)
	{
		if (last == null || lastIsRaid || tick - lastTick <= withoutLootTicks
			|| !KILLS_WITHOUT_LOOT.contains(last.getName()))
		{
			return null;
		}
		return consume();
	}

	void reset()
	{
		last = null;
	}

	private LootEvent.Kill consume()
	{
		LootEvent.Kill kill = last;
		last = null;
		return kill;
	}
}
