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

	CombatAchievementEvent(Common common, String tier, String task)
	{
		super(COMBAT_ACHIEVEMENT, common);
		this.tier = tier;
		this.task = task;
	}
}
