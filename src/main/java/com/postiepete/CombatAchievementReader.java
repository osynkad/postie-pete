package com.postiepete;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.GameState;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Reads every completed combat task, so tasks completed before the plugin was installed can be
 * backfilled.
 *
 * <p>Unlike the collection log, all of this is in values the game sends at login, so no interface
 * has to be opened: each task is one bit in the CA_TASK_COMPLETED varps, and the game keeps the
 * points total and each tier's threshold in varbits. The tasks in each tier are listed in one
 * cache enum per tier, whose entries are structs holding the task's ID.
 *
 * <p>Read a few ticks after logging in, and again whenever a combat achievements interface is
 * opened. Sent only if the completed tasks changed since the last read this session.
 *
 * <p>Task bits and tier enums as in the Valiance clan plugin by Sicryption
 * (github.com/Sicryption/ValianceClanPlugin, BSD-2-Clause).
 */
@Slf4j
class CombatAchievementReader
{
	static final String[] TIER_NAMES = {"easy", "medium", "hard", "elite", "master", "grandmaster"};

	/**
	 * Enums listing each tier's tasks, easy first (cs2 proc script4834).
	 */
	private static final int[] TIER_ENUMS = {3981, 3982, 3983, 3984, 3985, 3986};

	private static final int[] TIER_THRESHOLD_VARBITS = {
		VarbitID.CA_THRESHOLD_EASY,
		VarbitID.CA_THRESHOLD_MEDIUM,
		VarbitID.CA_THRESHOLD_HARD,
		VarbitID.CA_THRESHOLD_ELITE,
		VarbitID.CA_THRESHOLD_MASTER,
		VarbitID.CA_THRESHOLD_GRANDMASTER
	};

	/**
	 * Struct param holding a task's ID: the bit to check in TASK_VARPS.
	 */
	private static final int PARAM_TASK_ID = 1306;

	/**
	 * Task ID n is bit (n % 32) of TASK_VARPS[n / 32]. Grows as tasks are added.
	 */
	private static final int[] TASK_VARPS = {
		VarPlayerID.CA_TASK_COMPLETED_0,
		VarPlayerID.CA_TASK_COMPLETED_1,
		VarPlayerID.CA_TASK_COMPLETED_2,
		VarPlayerID.CA_TASK_COMPLETED_3,
		VarPlayerID.CA_TASK_COMPLETED_4,
		VarPlayerID.CA_TASK_COMPLETED_5,
		VarPlayerID.CA_TASK_COMPLETED_6,
		VarPlayerID.CA_TASK_COMPLETED_7,
		VarPlayerID.CA_TASK_COMPLETED_8,
		VarPlayerID.CA_TASK_COMPLETED_9,
		VarPlayerID.CA_TASK_COMPLETED_10,
		VarPlayerID.CA_TASK_COMPLETED_11,
		VarPlayerID.CA_TASK_COMPLETED_12,
		VarPlayerID.CA_TASK_COMPLETED_13,
		VarPlayerID.CA_TASK_COMPLETED_14,
		VarPlayerID.CA_TASK_COMPLETED_15,
		VarPlayerID.CA_TASK_COMPLETED_16,
		VarPlayerID.CA_TASK_COMPLETED_17,
		VarPlayerID.CA_TASK_COMPLETED_18,
		VarPlayerID.CA_TASK_COMPLETED_19,
		VarPlayerID.CA_TASK_COMPLETED_20
	};

	private static final Set<Integer> CA_INTERFACES = Set.of(
		InterfaceID.CA_OVERVIEW,
		InterfaceID.CA_TASKS,
		InterfaceID.CA_BOSSES,
		InterfaceID.CA_BOSS,
		InterfaceID.CA_REWARDS
	);

	/**
	 * Varps arrive during login; wait a few ticks so they are all in before reading.
	 */
	private static final int LOGIN_DELAY_TICKS = 5;

	/**
	 * Called on the client thread with what was read.
	 */
	interface OnRead
	{
		void accept(List<Integer> completedTaskIds, int points, String tier,
			List<CombatAchievementSnapshotEvent.TierProgress> tiers);
	}

	private final Client client;
	private final PostiePeteConfig config;

	private OnRead onRead;
	private int ticksUntilRead = -1;
	private Set<Integer> lastSent;

	@Inject
	CombatAchievementReader(Client client, PostiePeteConfig config)
	{
		this.client = client;
		this.config = config;
	}

	void setOnRead(OnRead onRead)
	{
		this.onRead = onRead;
	}

	void reset()
	{
		ticksUntilRead = -1;
		lastSent = null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		if (event.getGameState() == GameState.LOGGED_IN)
		{
			ticksUntilRead = LOGIN_DELAY_TICKS;
		}
		else if (event.getGameState() == GameState.LOGIN_SCREEN)
		{
			// Possibly a different account next: forget what was sent.
			reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (CA_INTERFACES.contains(event.getGroupId()))
		{
			ticksUntilRead = 0;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (ticksUntilRead < 0 || ticksUntilRead-- > 0)
		{
			return;
		}

		String endpointUrl = config.endpointUrl();
		if (!config.sendCombatAchievementSnapshot() || endpointUrl == null || endpointUrl.trim().isEmpty())
		{
			return;
		}

		int points = client.getVarbitValue(VarbitID.CA_POINTS);
		if (points <= 0)
		{
			// Nothing to backfill, or the values haven't arrived yet. Never send an empty log over a real one.
			return;
		}

		Set<Integer> completed = new TreeSet<>();
		List<CombatAchievementSnapshotEvent.TierProgress> tiers = new ArrayList<>(TIER_ENUMS.length);
		int[] thresholds = new int[TIER_ENUMS.length];
		for (int i = 0; i < TIER_ENUMS.length; i++)
		{
			EnumComposition tasks = client.getEnum(TIER_ENUMS[i]);
			int done = 0;
			for (int structId : tasks.getIntVals())
			{
				int taskId = client.getStructComposition(structId).getIntValue(PARAM_TASK_ID);
				if (isCompleted(taskId))
				{
					completed.add(taskId);
					done++;
				}
			}

			thresholds[i] = client.getVarbitValue(TIER_THRESHOLD_VARBITS[i]);
			tiers.add(new CombatAchievementSnapshotEvent.TierProgress(TIER_NAMES[i], done, tasks.size(),
				thresholds[i] > 0 ? thresholds[i] : null));
		}

		if (completed.equals(lastSent))
		{
			return;
		}
		lastSent = completed;

		if (onRead != null)
		{
			onRead.accept(Collections.unmodifiableList(new ArrayList<>(completed)), points,
				tierFor(points, thresholds), tiers);
		}
	}

	private boolean isCompleted(int taskId)
	{
		int varp = taskId / 32;
		if (taskId < 0 || varp >= TASK_VARPS.length)
		{
			// A new varp the plugin doesn't know about yet.
			log.debug("Combat task {} has no known completion varp", taskId);
			return false;
		}
		return (client.getVarpValue(TASK_VARPS[varp]) & (1 << (taskId % 32))) != 0;
	}

	/**
	 * The highest tier whose threshold has been reached, or null. Thresholds of 0 are unknown and skipped.
	 */
	static String tierFor(int points, int[] thresholds)
	{
		String tier = null;
		for (int i = 0; i < thresholds.length && i < TIER_NAMES.length; i++)
		{
			if (thresholds[i] > 0 && points >= thresholds[i])
			{
				tier = TIER_NAMES[i];
			}
		}
		return tier;
	}
}
