package com.postiepete;

/**
 * A completed combat task.
 */
class CombatAchievementEvent extends PostieEvent
{
	/**
	 * easy, medium, hard, elite, master or grandmaster.
	 */
	final String tier;

	/**
	 * Task name, e.g. "Noxious Foe".
	 */
	final String task;

	/**
	 * The game's task ID, e.g. 648. Null if the game's message
	 * didn't include it.
	 */
	final Integer taskId;

	CombatAchievementEvent(Common common, String tier, String task, Integer taskId)
	{
		super(COMBAT_ACHIEVEMENT, common);
		this.tier = tier;
		this.task = task;
		this.taskId = taskId;
	}
}
