package com.postiepete;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup("postiepete")
public interface PostiePeteConfig extends Config
{
	@ConfigSection(
		name = "Loot",
		description = "NPC drops, raid and reward chests, clue caskets etc.",
		position = 1,
		closedByDefault = true
	)
	String lootSection = "loot";

	@ConfigSection(
		name = "Collection log",
		description = "New items added to your collection log",
		position = 2,
		closedByDefault = true
	)
	String collectionLogSection = "collectionLog";

	@ConfigSection(
		name = "Combat achievements",
		description = "Completed combat tasks",
		position = 3,
		closedByDefault = true
	)
	String combatAchievementSection = "combatAchievement";

	@ConfigItem(
		keyName = "apiUrl",
		name = "API URL",
		description = "URL to the API that should receive the events",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers",
		position = 0
	)
	default String apiUrl()
	{
		return "";
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
