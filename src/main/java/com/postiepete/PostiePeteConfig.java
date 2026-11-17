package com.postiepete;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(PostiePeteConfig.GROUP)
public interface PostiePeteConfig extends Config
{
	String GROUP = "postiepete";

	/**
	 * Legacy key: the base URL that "/events" was appended to. Migrated to {@link #endpointUrl()} on startup.
	 */
	String LEGACY_API_URL_KEY = "apiUrl";

	String ENDPOINT_URL_KEY = "endpointUrl";

	@ConfigSection(
		name = "Loot",
		description = "NPC drops, raid and reward chests, clue caskets etc.",
		position = 2,
		closedByDefault = true
	)
	String lootSection = "loot";

	@ConfigSection(
		name = "Collection log",
		description = "New items added to your collection log",
		position = 3,
		closedByDefault = true
	)
	String collectionLogSection = "collectionLog";

	@ConfigSection(
		name = "Combat achievements",
		description = "Completed combat tasks, points and tier",
		position = 4,
		closedByDefault = true
	)
	String combatAchievementSection = "combatAchievement";

	@ConfigItem(
		keyName = ENDPOINT_URL_KEY,
		name = "Endpoint URL",
		description = "Full URL that events are POSTed to, e.g. http://localhost:3000/events",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default String endpointUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = "includePlayerItems",
		name = "Include player items",
		description = "Send equipment, inventory, rune pouch and quiver contents with loot, collection log and combat"
			+ " achievement events. Snapshots never include them.",
		position = 1
	)
	default boolean includePlayerItems()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendLoot",
		name = "Send event",
		description = "Send a loot event when you receive loot",
		section = lootSection,
		position = 0
	)
	default boolean sendLoot()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendCollectionLog",
		name = "Send event",
		description = "Send a collection log event when a new item is added to your collection log",
		section = collectionLogSection,
		position = 0
	)
	default boolean sendCollectionLog()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendCollectionLogSnapshot",
		name = "Send full log when opened",
		description = "Send every item in your collection log each time you open it in game, so slots you got before"
			+ " installing the plugin are included. Sent at most once per opening, and only if something changed.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = collectionLogSection,
		position = 1
	)
	default boolean sendCollectionLogSnapshot()
	{
		return false;
	}

	@ConfigItem(
		keyName = "sendCombatAchievement",
		name = "Send event",
		description = "Send a combat achievement event when you complete a combat task",
		section = combatAchievementSection,
		position = 0
	)
	default boolean sendCombatAchievement()
	{
		return true;
	}

	@ConfigItem(
		keyName = "sendCombatAchievementSnapshot",
		name = "Send all completed tasks",
		description = "After you log in, send every combat task you have completed with your points and tier, so tasks"
			+ " completed before installing the plugin are included. Sent again when you open the combat achievements"
			+ " interface, and only if something changed.",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		section = combatAchievementSection,
		position = 1
	)
	default boolean sendCombatAchievementSnapshot()
	{
		return false;
	}
}
