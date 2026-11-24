/*
 * Parts of this file are based on the Valiance clan plugin
 * (https://github.com/Sicryption/ValianceClanPlugin), distributed under the
 * following license:
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, Encryptiron
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
import net.runelite.api.WidgetNode;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.eventbus.Subscribe;

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

	/**
	 * A combat achievements interface is on screen. Switching between its tabs loads another of them, which isn't a
	 * new opening, so it doesn't send again.
	 */
	private boolean visible;

	/**
	 * Read on the next tick: the interfaces were just opened.
	 */
	private boolean pendingRead;

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
		visible = false;
		pendingRead = false;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Interfaces are gone after logging out or hopping
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (CA_INTERFACES.contains(event.getGroupId()) && !visible)
		{
			visible = true;
			pendingRead = true;
		}
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (pendingRead)
		{
			pendingRead = false;
			read();
		}

		// Only looked up while one is open, so the next opening can be told apart from a tab switch
		if (visible && !anyInterfaceOpen())
		{
			visible = false;
		}
	}

	private boolean anyInterfaceOpen()
	{
		for (WidgetNode node : client.getComponentTable())
		{
			if (CA_INTERFACES.contains(node.getId()))
			{
				return true;
			}
		}
		return false;
	}

	/**
	 * Sent on every opening, changed or not, so a request that failed is simply retried by opening the interface
	 * again. Read while the interface is open, the values the game shows in it are all in.
	 */
	private void read()
	{
		if (PostiePetePlugin.endpointUrls(config.endpointUrl()).isEmpty())
		{
			return;
		}

		int points = client.getVarbitValue(VarbitID.CA_POINTS);
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
