package com.postiepete;

import lombok.Value;

/**
 * Fields shared by every event sent to the API. Each event type adds its own fields in a
 * subclass, so a payload only contains the fields that apply to its type.
 */
abstract class PostieEvent
{
	static final String LOOT = "loot";
	static final String COLLECTION_LOG = "collection_log";
	static final String COMBAT_ACHIEVEMENT = "combat_achievement";
	static final String COLLECTION_LOG_SNAPSHOT = "collection_log_snapshot";
	static final String COMBAT_ACHIEVEMENT_SNAPSHOT = "combat_achievement_snapshot";

	/**
	 * "loot", "collection_log", "collection_log_snapshot", "combat_achievement" or
	 * "combat_achievement_snapshot".
	 */
	final String eventType;
	final String timestamp;
	final String rsn;

	/**
	 * RuneLite account hash: stable per account, unaffected by name changes. Sent as a
	 * string because it is a 64-bit value that JavaScript numbers can't represent exactly.
	 */
	final String accountHash;

	PostieEvent(String eventType, Common common)
	{
		this.eventType = eventType;
		this.timestamp = common.timestamp;
		this.rsn = common.rsn;
		this.accountHash = common.accountHash;
	}

	/**
	 * The shared fields, captured once when an event happens and passed to the event's constructor.
	 */
	@Value
	static class Common
	{
		String timestamp;
		String rsn;
		String accountHash;
	}
}
