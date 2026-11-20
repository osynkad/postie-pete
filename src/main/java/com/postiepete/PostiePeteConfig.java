package com.postiepete;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;
import net.runelite.client.config.ConfigSection;

@ConfigGroup(PostiePeteConfig.GROUP)
public interface PostiePeteConfig extends Config
{
	String GROUP = "postiepete";

	String SEND_LOOT_KEY = "sendLoot";

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
		keyName = "endpointUrl",
		name = "Endpoint URLs",
		description = "Full URLs that events are POSTed to, separated by commas, e.g. http://localhost:3000/events",
		position = 0
	)
	default String endpointUrl()
	{
		return "";
	}

	@ConfigItem(
		keyName = SEND_LOOT_KEY,
		name = "Send event",
		description = "Send a loot event when you receive loot. Needs RuneLite's Loot Tracker plugin turned on.",
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
