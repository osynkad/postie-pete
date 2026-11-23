package com.postiepete;

/**
 * The Fractured Archive's messages. Its run is followed with {@link RaidSplits}: each boss's duration, where the
 * path (the boss picked at each of the three choices) is the 2nd to 4th boss.
 */
final class FracturedArchive
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

	private FracturedArchive()
	{
	}
}
