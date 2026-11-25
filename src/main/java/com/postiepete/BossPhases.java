package com.postiepete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * Follows a boss fight's phases, so they can be sent as splits with its loot. The fight is a fixed list of parts,
 * timed in game ticks, and moved on to the next part by whatever marks the change for the boss: see
 * {@link #phosanisNightmare}.
 */
class BossPhases
{
	/**
	 * Phosani's Nightmare's phases. In each of the first three she has a shield; once it is broken she is weak,
	 * and the four pillars (totems) must be charged, which ends her weak form. Then sleepwalkers walk to her while
	 * she charges her blast, until her next phase. After the third comes the final, desperation phase. See
	 * {@link PhosaniNightmare}.
	 */
	static BossPhases phosanisNightmare()
	{
		return new BossPhases(PHOSANIS_NIGHTMARE,
			"Phosani P1", "Pillar P1", "Sleepwalker P1",
			"Phosani P2", "Pillar P2", "Sleepwalker P2",
			"Phosani P3", "Pillar P3", "Sleepwalker P3",
			"Phosani final phase");
	}

	/**
	 * The bosses' names as their kill count messages and loot name them.
	 */
	static final String PHOSANIS_NIGHTMARE = "Phosani's Nightmare";

	private final String boss;
	private final List<String> parts;

	private final List<LootEvent.Split> splits = new ArrayList<>();

	// The part being timed, an index into parts, and the tick it started. Null when no fight is being followed.
	private Integer part;
	private int partStartTick;

	private boolean finished;

	// The last fight's splits once they were sent with its loot, still shown until the next fight starts
	private List<LootEvent.Split> sentSplits = Collections.emptyList();

	BossPhases(String boss, String... parts)
	{
		this.boss = boss;
		this.parts = Arrays.asList(parts);
	}

	/**
	 * The boss's name, as its kill count message and loot name it.
	 */
	String boss()
	{
		return boss;
	}

	/**
	 * Whether the kill count or loot name is this boss's.
	 */
	boolean isBoss(String name)
	{
		return boss.equals(name);
	}

	/**
	 * Starts a new fight now, e.g. when the boss wakes up. Forgets any fight before it.
	 */
	void start(int tick)
	{
		reset();
		part = 0;
		partStartTick = tick;
	}

	/**
	 * Called on every hitsplat on the boss, anyone's. The first starts the fight, and so does the first after a
	 * finished fight.
	 */
	void onHit(int tick)
	{
		if (!isActive())
		{
			start(tick);
		}
	}

	/**
	 * Whether a fight is being followed.
	 */
	boolean isActive()
	{
		return part != null && !finished;
	}

	/**
	 * The index of the part being timed, or null if there is none.
	 */
	Integer partIndex()
	{
		return isActive() ? part : null;
	}

	/**
	 * Ends the part being timed and starts the next. Ignored in the last part, which only the kill ends.
	 */
	void next(int tick)
	{
		if (!isActive() || part == parts.size() - 1)
		{
			return;
		}
		endPart(tick);
		part++;
		partStartTick = tick;
	}

	/**
	 * Ends the fight, on the boss's kill count message or when the boss starts dying. Its splits are kept for the
	 * loot.
	 */
	void finish(int tick)
	{
		if (!isActive())
		{
			return;
		}
		endPart(tick);
		finished = true;
	}

	/**
	 * The fight's splits, for the boss's loot. A fight whose kill count message wasn't seen ends now. Empty if no
	 * fight was seen. Consumed, so they can't be attached to later loot.
	 */
	List<LootEvent.Split> consumeSplits(int tick)
	{
		finish(tick);
		List<LootEvent.Split> result = new ArrayList<>(splits);
		clearFight();
		sentSplits = result;
		return result;
	}

	/**
	 * The parts done so far, for showing: the fight's, or the last fight's once its loot took them.
	 */
	List<LootEvent.Split> doneSplits()
	{
		return part == null ? sentSplits : splits;
	}

	/**
	 * The name of the part being timed, or null if there is none.
	 */
	String currentPart()
	{
		return isActive() ? parts.get(part) : null;
	}

	/**
	 * The tick the part being timed started. Only meaningful while {@link #currentPart} isn't null.
	 */
	int currentPartStartTick()
	{
		return partStartTick;
	}

	/**
	 * Forgets the fight, e.g. when the player leaves it (dies or teleports out) or logs out.
	 */
	void reset()
	{
		clearFight();
		sentSplits = Collections.emptyList();
	}

	private void clearFight()
	{
		splits.clear();
		part = null;
		finished = false;
	}

	private void endPart(int tick)
	{
		splits.add(new LootEvent.Split(parts.get(part), WaveRun.ticksToSeconds(tick - partStartTick)));
	}
}
