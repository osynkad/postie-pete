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
		description = "Completed combat tasks",
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
		description = "Send equipment, inventory, rune pouch and quiver contents with every event",
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
}
