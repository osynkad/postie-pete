package com.postiepete;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;
import lombok.Value;
import net.runelite.api.gameval.NpcID;

/**
 * Follows a Fractured Archive run: which boss the team picked at each of the raid's three path
 * choices, each boss's duration, and the team when the raid was completed, so they can be sent
 * with the raid's loot.
 */
class FracturedArchive
{
	/**
	 * The Loot Tracker's name for the raid's reward chest.
	 */
	static final String LOOT_SOURCE = "Fractured Archive";

	/**
	 * Start of the kill count message sent when the raid is completed, before the chest is opened.
	 */
	static final String COMPLETION_MESSAGE = "Your completed Fractured Archive count is";

	/**
	 * Sent when the first boss starts, at the start of every run.
	 */
	static final String RUN_START_MESSAGE = "Challenge started: Archive Guardian.";

	// Boss names as on the OSRS Wiki
	static final String EZEKIEL = "Ezekiel";
	static final String BORK = "Bork and the Smorks";
	static final String MOIA = "Moia";
	static final String DREADNOTH = "Dreadnoth the Jellied";
	static final String DAGONHAI = "The Dagon'hai";
	static final String XARTH = "Xarth";

	// The 2nd, 3rd and 4th boss are each picked from two doors
	private static final int PATH_CHOICES = 3;

	// The path choice (0 = 2nd boss, 1 = 3rd, 2 = 4th) and boss of every NPC in the path encounters,
	// by NPC ID. Includes minions and shielded forms, since the team might only damage some of them.
	private static final Map<Integer, PathBoss> PATH_BOSSES = Map.ofEntries(
		Map.entry(NpcID.TFA_EZEKIEL, new PathBoss(0, EZEKIEL)),
		Map.entry(NpcID.TFA_BORK, new PathBoss(0, BORK)),
		Map.entry(NpcID.TFA_SMORK, new PathBoss(0, BORK)),
		Map.entry(NpcID.TFA_MOIA, new PathBoss(1, MOIA)),
		Map.entry(NpcID.TFA_JELLY_BOSS, new PathBoss(1, DREADNOTH)),
		Map.entry(NpcID.TFA_DAGONHAI_LEADER, new PathBoss(2, DAGONHAI)),
		Map.entry(NpcID.TFA_DAGONHAI_LEADER_UNSHIELDED, new PathBoss(2, DAGONHAI)),
		Map.entry(NpcID.TFA_DAGONHAI_MINION_1, new PathBoss(2, DAGONHAI)),
		Map.entry(NpcID.TFA_DAGONHAI_MINION_1_UNSHIELDED, new PathBoss(2, DAGONHAI)),
		Map.entry(NpcID.TFA_DAGONHAI_MINION_2, new PathBoss(2, DAGONHAI)),
		Map.entry(NpcID.TFA_DAGONHAI_MINION_2_UNSHIELDED, new PathBoss(2, DAGONHAI)),
		Map.entry(NpcID.TFA_XARTH, new PathBoss(2, XARTH)));

	private final String[] path = new String[PATH_CHOICES];
	private final List<LootEvent.Split> splits = new ArrayList<>();
	private LootEvent.Raid completedRaid;

	/**
	 * Called on the first boss's start message. Clears what's left of an earlier, failed run.
	 */
	void onRunStarted()
	{
		clearRun();
	}

	/**
	 * Called for every hitsplat on an NPC. A hitsplat on a path boss means the team picked it.
	 */
	void onNpcHit(int npcId)
	{
		PathBoss boss = PATH_BOSSES.get(npcId);
		if (boss != null)
		{
			path[boss.choice] = boss.name;
		}
	}

	/**
	 * Called on a boss's completion message, with the boss name as the game writes it and its duration in seconds.
	 */
	void onBossCompleted(String boss, double duration)
	{
		splits.add(new LootEvent.Split(boss, duration));
	}

	/**
	 * Keeps the run's details for the reward chest, which is opened some time later.
	 */
	void onRaidCompleted(Integer teamSize, List<String> team)
	{
		completedRaid = currentRaid(teamSize, team);
		clearRun();
	}

	/**
	 * The completed run's details, for the reward chest's loot. Uses the given team and the run so
	 * far if the completion message was missed. Consumed, so they can't be attached to later loot.
	 */
	LootEvent.Raid consumeRaid(Integer currentTeamSize, List<String> currentTeam)
	{
		LootEvent.Raid raid = completedRaid != null ? completedRaid : currentRaid(currentTeamSize, currentTeam);
		reset();
		return raid;
	}

	void reset()
	{
		completedRaid = null;
		clearRun();
	}

	private void clearRun()
	{
		Arrays.fill(path, null);
		splits.clear();
	}

	private LootEvent.Raid currentRaid(Integer teamSize, List<String> team)
	{
		return new LootEvent.Raid(teamSize, team, Arrays.asList(path.clone()), new ArrayList<>(splits));
	}

	@Value
	private static class PathBoss
	{
		int choice;
		String name;
	}
}
