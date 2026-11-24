package com.postiepete;

import java.util.List;
import lombok.Value;

/**
 * Every completed combat task, with the player's points and tier, read from the game after login.
 */
class CombatAchievementSnapshotEvent extends PostieEvent
{
	/**
	 * IDs of every completed task, ascending. The same IDs the game uses internally.
	 */
	final List<Integer> completedTaskIds;

	/**
	 * Number of completed tasks (the length of completedTaskIds).
	 */
	final int completedTasks;

	/**
	 * Combat achievement points, from the game's own counter.
	 */
	final int points;

	/**
	 * Highest tier whose points threshold has been reached ("easy" ... "grandmaster"), or null if none.
	 */
	final String tier;

	/**
	 * Progress in each tier, easy first.
	 */
	final List<TierProgress> tiers;

	CombatAchievementSnapshotEvent(Common common, List<Integer> completedTaskIds, int points, String tier,
		List<TierProgress> tiers)
	{
		super(COMBAT_ACHIEVEMENT_SNAPSHOT, common);
		this.completedTaskIds = completedTaskIds;
		this.completedTasks = completedTaskIds.size();
		this.points = points;
		this.tier = tier;
		this.tiers = tiers;
	}

	@Value
	static class TierProgress
	{
		/**
		 * "easy", "medium", "hard", "elite", "master" or "grandmaster".
		 */
		String tier;

		/**
		 * Completed tasks in this tier.
		 */
		int completed;

		/**
		 * All tasks in this tier.
		 */
		int total;

		/**
		 * Points needed to reach this tier, or null if the game didn't report it.
		 */
		Integer pointsThreshold;

		/**
		 * Points all tasks in this tier are worth together.
		 */
		int maxPoints;
	}
}
