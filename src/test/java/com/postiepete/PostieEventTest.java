package com.postiepete;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
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
		JsonObject json = toJson(new LootEvent(COMMON, "Vorkath", 8061, null, false, null, null,
			Collections.singletonList(new LootEvent.Drop(536, "Dragon bones", 2, 2450))));

		assertEquals(with(COMMON_FIELDS, "source", "npcId", "duration", "personalBest", "stage", "raid", "drops"),
			json.keySet());
		assertEquals("loot", json.get("eventType").getAsString());
		assertEquals("ExamplePlayer", json.get("rsn").getAsString());
		assertEquals(8061, json.get("npcId").getAsInt());
		assertEquals(true, json.get("duration").isJsonNull());
		assertEquals(true, json.get("stage").isJsonNull());
		assertEquals(true, json.get("raid").isJsonNull());
		JsonObject drop = json.getAsJsonArray("drops").get(0).getAsJsonObject();
		assertEquals(set("itemId", "itemName", "quantity", "value"), drop.keySet());
		assertEquals(536, drop.get("itemId").getAsInt());
		assertEquals("Dragon bones", drop.get("itemName").getAsString());
		assertEquals(2450, drop.get("value").getAsLong());
	}

	@Test
	public void raidLootSendsRaidDetails()
	{
		LootEvent.Raid raid = new LootEvent.Raid(2, Arrays.asList("ExamplePlayer", "Teammate"),
			Arrays.asList("Ezekiel", null, "Xarth"),
			Arrays.asList(new LootEvent.Split("Archive Guardian", 123.0), new LootEvent.Split("Ezekiel", 141.6)));
		JsonObject json = toJson(new LootEvent(COMMON, "Fractured Archive", null, 1534.2, false, null, raid,
			Collections.emptyList()));

		JsonObject raidJson = json.getAsJsonObject("raid");
		assertEquals(set("teamSize", "team", "path", "splits"), raidJson.keySet());
		assertEquals(2, raidJson.get("teamSize").getAsInt());
		assertEquals("Teammate", raidJson.getAsJsonArray("team").get(1).getAsString());
		assertEquals(3, raidJson.getAsJsonArray("path").size());
		assertEquals("Ezekiel", raidJson.getAsJsonArray("path").get(0).getAsString());
		assertEquals(true, raidJson.getAsJsonArray("path").get(1).isJsonNull());
		JsonObject split = raidJson.getAsJsonArray("splits").get(1).getAsJsonObject();
		assertEquals(set("boss", "duration"), split.keySet());
		assertEquals("Ezekiel", split.get("boss").getAsString());
		assertEquals(141.6, split.get("duration").getAsDouble(), 0);
	}

	@Test
	public void collectionLogEventHasOnlyCollectionLogFields()
	{
		JsonObject json = toJson(new CollectionLogEvent(COMMON, "Vorkath's head", 21907));

		assertEquals(with(COMMON_FIELDS, "itemName", "itemId"), json.keySet());
		assertEquals("collection_log", json.get("eventType").getAsString());
		assertEquals("Vorkath's head", json.get("itemName").getAsString());
		assertEquals(21907, json.get("itemId").getAsInt());
	}

	@Test
	public void nonNpcLootSendsNullNpcId()
	{
		JsonObject loot = toJson(new LootEvent(COMMON, "Barrows", null, null, false, null, null, Collections.emptyList()));
		assertEquals(true, loot.get("npcId").isJsonNull());
	}

	@Test
	public void collectionLogSnapshotEventHasOnlySnapshotFields()
	{
		JsonObject json = toJson(new CollectionLogSnapshotEvent(COMMON,
			Collections.singletonList(new CollectionLogSnapshotEvent.Item(4151, 3)), 1));

		assertEquals(with(COMMON_FIELDS, "items", "obtainedSlots"), json.keySet());
		assertEquals("collection_log_snapshot", json.get("eventType").getAsString());
		JsonObject item = json.getAsJsonArray("items").get(0).getAsJsonObject();
		assertEquals(set("itemId", "quantity"), item.keySet());
		assertEquals(4151, item.get("itemId").getAsInt());
		assertEquals(3, item.get("quantity").getAsInt());
		assertEquals(1, json.get("obtainedSlots").getAsInt());
	}

	@Test
	public void duplicateCollectionLogItemsCountAsOneSlot()
	{
		Map<Integer, Integer> items = new HashMap<>();
		items.put(4151, 1);
		items.put(12013, 1); // Prospector helmet
		assertEquals(2, CollectionLogReader.countSlots(items));
		items.put(29472, 1); // Prospector helmet, listed again on another page
		assertEquals(2, CollectionLogReader.countSlots(items));
	}

	@Test
	public void combatAchievementEventHasOnlyCombatAchievementFields()
	{
		JsonObject json = toJson(new CombatAchievementEvent(COMMON, "hard", "Noxious Foe"));

		assertEquals(with(COMMON_FIELDS, "tier", "task"), json.keySet());
		assertEquals("combat_achievement", json.get("eventType").getAsString());
		assertEquals("hard", json.get("tier").getAsString());
	}

	@Test
	public void excludedPlayerItemsAreSentAsNull()
	{
		PostieEvent.Common common = new PostieEvent.Common(
			"2026-11-12T20:35:00Z", "ExamplePlayer", "-3412345678901234567", null);
		JsonObject json = toJson(new CombatAchievementEvent(common, "hard", "Noxious Foe"));

		assertEquals(with(COMMON_FIELDS, "tier", "task"), json.keySet());
		assertEquals(true, json.get("playerItems").isJsonNull());
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
