package com.postiepete;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import org.junit.Test;

public class PostieEventTest
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private static final PostieEvent.Common COMMON = new PostieEvent.Common(
		"2026-11-12T20:35:00Z", "ExamplePlayer", "-3412345678901234567",
		new PlayerItems(Collections.emptyMap(), Collections.emptyMap(), null, null));

	private static final Set<String> COMMON_FIELDS = set("eventType", "timestamp", "rsn", "accountHash", "playerItems");

	@Test
	public void lootEventHasOnlyLootFields()
	{
		JsonObject json = toJson(new LootEvent(COMMON, "Vorkath", null, false,
			Collections.singletonList(new LootEvent.Drop(536, 2))));

		assertEquals(with(COMMON_FIELDS, "source", "duration", "personalBest", "drops"), json.keySet());
		assertEquals("loot", json.get("eventType").getAsString());
		assertEquals("ExamplePlayer", json.get("rsn").getAsString());
		assertEquals(true, json.get("duration").isJsonNull());
		assertEquals(536, json.getAsJsonArray("drops").get(0).getAsJsonObject().get("itemId").getAsInt());
	}

	@Test
	public void collectionLogEventHasOnlyCollectionLogFields()
	{
		JsonObject json = toJson(new CollectionLogEvent(COMMON, "Vorkath's head"));

		assertEquals(with(COMMON_FIELDS, "itemName"), json.keySet());
		assertEquals("collection_log", json.get("eventType").getAsString());
		assertEquals("Vorkath's head", json.get("itemName").getAsString());
	}

	@Test
	public void combatAchievementEventHasOnlyCombatAchievementFields()
	{
		JsonObject json = toJson(new CombatAchievementEvent(COMMON, "hard", "Noxious Foe"));

		assertEquals(with(COMMON_FIELDS, "tier", "task"), json.keySet());
		assertEquals("combat_achievement", json.get("eventType").getAsString());
		assertEquals("hard", json.get("tier").getAsString());
	}

	private static JsonObject toJson(PostieEvent event)
	{
		return GSON.toJsonTree(event).getAsJsonObject();
	}

	private static Set<String> with(Set<String> base, String... more)
	{
		Set<String> result = new HashSet<>(base);
		result.addAll(Arrays.asList(more));
		return result;
	}

	private static Set<String> set(String... values)
	{
		return new HashSet<>(Arrays.asList(values));
	}
}
