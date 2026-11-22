package com.postiepete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * When other players were near the player, by game tick, so a kill can count how many were in the fight with
 * them. Each player's nearby time is kept as tick ranges. Names are only kept here, to count each player once;
 * they are never sent.
 */
class NearbyPlayers
{
	/**
	 * How close another player must be, in tiles, to count as near.
	 */
	static final int RADIUS_TILES = 15;

	/**
	 * How long ranges are kept after they end: longer than any fight that is counted.
	 */
	static final int MAX_AGE_TICKS = 3000;

	private final Map<String, List<Range>> seen = new HashMap<>();

	/**
	 * Called each tick another player is near. A tick right after their last one extends that range.
	 */
	void onNear(String player, int tick)
	{
		if (player == null)
		{
			return;
		}
		List<Range> ranges = seen.computeIfAbsent(player, name -> new ArrayList<>());
		Range last = ranges.isEmpty() ? null : ranges.get(ranges.size() - 1);
		if (last != null && tick - last.toTick <= 1)
		{
			last.toTick = Math.max(last.toTick, tick);
		}
		else
		{
			ranges.add(new Range(tick));
		}
	}

	/**
	 * How many other players were near at some point between the two ticks.
	 */
	int count(int fromTick, int toTick)
	{
		int count = 0;
		for (List<Range> ranges : seen.values())
		{
			for (Range range : ranges)
			{
				if (range.fromTick <= toTick && range.toTick >= fromTick)
				{
					count++;
					break;
				}
			}
		}
		return count;
	}

	/**
	 * Drops ranges that ended more than {@link #MAX_AGE_TICKS} ago.
	 */
	void prune(int tick)
	{
		seen.values().forEach(ranges -> ranges.removeIf(range -> tick - range.toTick > MAX_AGE_TICKS));
		seen.values().removeIf(List::isEmpty);
	}

	void reset()
	{
		seen.clear();
	}

	private static class Range
	{
		final int fromTick;
		int toTick;

		Range(int tick)
		{
			this.fromTick = tick;
			this.toTick = tick;
		}
	}
}
