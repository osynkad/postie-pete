package com.postiepete;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.Test;

public class PostieEventTest
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private static final PostieEvent.Common COMMON = new PostieEvent.Common(
		"2026-11-12T20:35:00Z", "ExamplePlayer", "-3412345678901234567",
		new PlayerItems(Collections.emptyMap(), Collections.emptyMap(), null, null));

	private static final Set<String> IDENTITY_FIELDS = set("eventType", "timestamp", "rsn", "accountHash");

	// Events that happen at a moment (loot, a new slot, a task) also carry the player's gear; snapshots don't
	private static final Set<String> COMMON_FIELDS = with(IDENTITY_FIELDS, "playerItems");

	private static final List<String> SOURCE_FIELDS = Arrays.asList(
		"name", "lootName", "npcId", "killCount", "duration", "personalBest", "wave");

	@Test
	public void lootEventHasOnlyLootFields()
	{
		JsonObject json = toJson(new LootEvent(COMMON,
			LootEvent.Source.of("Mad Angel", new LootEvent.Kill("Mad Angel", 212), 16305,
				new LootEvent.KillTime(48.6, 41.2), null),
			null, Collections.singletonList(new LootEvent.Drop(536, "Dragon bones", 2, 2450))));

		assertEquals(with(COMMON_FIELDS, "source", "raid", "drops"), json.keySet());
		assertEquals("loot", json.get("eventType").getAsString());
		assertEquals("ExamplePlayer", json.get("rsn").getAsString());
		JsonObject source = json.getAsJsonObject("source");
		assertEquals(new HashSet<>(SOURCE_FIELDS), source.keySet());
		assertEquals("Mad Angel", source.get("name").getAsString());
		// Same name in the kill count message and the Loot Tracker
		assertEquals(true, source.get("lootName").isJsonNull());
		assertEquals(16305, source.get("npcId").getAsInt());
		assertEquals(212, source.get("killCount").getAsInt());
		assertEquals(48.6, source.get("duration").getAsDouble(), 0);
		assertEquals(41.2, source.get("personalBest").getAsDouble(), 0);
		assertEquals(true, source.get("wave").isJsonNull());
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
			Arrays.asList(new LootEvent.Split("Archive Guardian", 123.0), new LootEvent.Split("Ezekiel", 141.6)),
			Collections.singletonList(new LootEvent.Purple("Teammate", "Obligator (uncharged)")));
		JsonObject json = toJson(new LootEvent(COMMON,
			LootEvent.Source.of("Fractured Archive", new LootEvent.Kill("Fractured Archive", 75), null,
				new LootEvent.KillTime(1534.2, 1534.2), null),
			raid, Collections.emptyList()));

		JsonObject raidJson = json.getAsJsonObject("raid");
		assertEquals(set("teamSize", "team", "splits", "purples"), raidJson.keySet());
		assertEquals(2, raidJson.get("teamSize").getAsInt());
		assertEquals("Teammate", raidJson.getAsJsonArray("team").get(1).getAsString());
		JsonObject split = raidJson.getAsJsonArray("splits").get(1).getAsJsonObject();
		assertEquals(set("boss", "duration"), split.keySet());
		assertEquals("Ezekiel", split.get("boss").getAsString());
		assertEquals(141.6, split.get("duration").getAsDouble(), 0);
		JsonObject purple = raidJson.getAsJsonArray("purples").get(0).getAsJsonObject();
		assertEquals(set("rsn", "item"), purple.keySet());
		assertEquals("Obligator (uncharged)", purple.get("item").getAsString());
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
	public void lootWithoutKillCountIsNamedByTheLootTracker()
	{
		LootEvent.Source source = LootEvent.Source.of("Reward cart (Wintertodt)", null, null, null, null);
		assertEquals("Reward cart (Wintertodt)", source.getName());
		assertEquals(null, source.getLootName());
		assertEquals(null, source.getNpcId());
		assertEquals(null, source.getKillCount());
		assertEquals(null, source.getDuration());
		assertEquals(null, source.getPersonalBest());
	}

	@Test
	public void lootIsNamedByItsKillCountMessageAndKeepsADifferentLootTrackerName()
	{
		LootEvent.Source source = LootEvent.Source.of("Theatre of Blood", new LootEvent.Kill("Theatre of Blood: Hard Mode", 40),
			null, new LootEvent.KillTime(1234.5, null), null);
		assertEquals("Theatre of Blood: Hard Mode", source.getName());
		assertEquals("Theatre of Blood", source.getLootName());
		assertEquals(Integer.valueOf(40), source.getKillCount());
		assertEquals(Double.valueOf(1234.5), source.getDuration());
		assertEquals(null, source.getPersonalBest());

		JsonObject json = GSON.toJsonTree(source).getAsJsonObject();
		assertEquals("The Leviathan", LootEvent.Source.of("The Leviathan", new LootEvent.Kill("Leviathan", 120),
			12214, null, null).getLootName());
		assertEquals(new HashSet<>(SOURCE_FIELDS), json.keySet());
	}

	@Test
	public void killWithoutLootHasNoLootTrackerName()
	{
		LootEvent.Source source = LootEvent.Source.of(null, new LootEvent.Kill("TzKal-Zuk", 3), null,
			new LootEvent.KillTime(3720.0, 3720.0), null);
		assertEquals("TzKal-Zuk", source.getName());
		assertEquals(null, source.getLootName());
		assertEquals(Integer.valueOf(3), source.getKillCount());
	}

	@Test
	public void colosseumLootSendsItsWave()
	{
		LootEvent.Source source = LootEvent.Source.of("Fortis Colosseum", null, null, null, 7);
		assertEquals(Integer.valueOf(7), source.getWave());
	}

	@Test
	public void collectionLogSnapshotEventHasOnlySnapshotFields()
	{
		JsonObject json = toJson(new CollectionLogSnapshotEvent(COMMON,
			Collections.singletonList(new CollectionLogSnapshotEvent.Item(4151, 3)), 1));

		assertEquals(with(IDENTITY_FIELDS, "items", "obtainedSlots"), json.keySet());
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
		JsonObject json = toJson(new CombatAchievementEvent(COMMON, "hard", "Noxious Foe", 402));

		assertEquals(with(COMMON_FIELDS, "tier", "task", "taskId"), json.keySet());
		assertEquals("combat_achievement", json.get("eventType").getAsString());
		assertEquals("hard", json.get("tier").getAsString());
		assertEquals(402, json.get("taskId").getAsInt());
	}

	@Test
	public void excludedPlayerItemsAreSentAsNull()
	{
		PostieEvent.Common common = new PostieEvent.Common(
			"2026-11-12T20:35:00Z", "ExamplePlayer", "-3412345678901234567", null);
		JsonObject json = toJson(new CombatAchievementEvent(common, "hard", "Noxious Foe", null));

		assertEquals(with(COMMON_FIELDS, "tier", "task", "taskId"), json.keySet());
		assertEquals(true, json.get("playerItems").isJsonNull());
	}

	@Test
	public void combatAchievementSnapshotEventHasOnlySnapshotFields()
	{
		JsonObject json = toJson(new CombatAchievementSnapshotEvent(COMMON, Arrays.asList(3, 17, 402), 9, "easy",
			Arrays.asList(
				new CombatAchievementSnapshotEvent.TierProgress("easy", 2, 33, 33),
				new CombatAchievementSnapshotEvent.TierProgress("medium", 1, 41, null))));

		assertEquals(with(IDENTITY_FIELDS, "completedTaskIds", "completedTasks", "points", "tier", "tiers"), json.keySet());
		assertEquals("combat_achievement_snapshot", json.get("eventType").getAsString());
		assertEquals(3, json.get("completedTasks").getAsInt());
		assertEquals(402, json.getAsJsonArray("completedTaskIds").get(2).getAsInt());
		assertEquals(9, json.get("points").getAsInt());
		assertEquals("easy", json.get("tier").getAsString());
		JsonObject easy = json.getAsJsonArray("tiers").get(0).getAsJsonObject();
		assertEquals(set("tier", "completed", "total", "pointsThreshold"), easy.keySet());
		assertEquals(33, easy.get("pointsThreshold").getAsInt());
		assertEquals(true, json.getAsJsonArray("tiers").get(1).getAsJsonObject().get("pointsThreshold").isJsonNull());
	}

	@Test
	public void combatTierIsTheHighestThresholdReached()
	{
		int[] thresholds = {33, 115, 304, 820, 1465, 2005};
		assertEquals(null, CombatAchievementReader.tierFor(32, thresholds));
		assertEquals("easy", CombatAchievementReader.tierFor(33, thresholds));
		assertEquals("elite", CombatAchievementReader.tierFor(1000, thresholds));
		assertEquals("grandmaster", CombatAchievementReader.tierFor(2630, thresholds));
		// Unknown (0) thresholds are skipped rather than counted as reached.
		assertEquals("medium", CombatAchievementReader.tierFor(1000, new int[]{33, 115, 0, 0, 0, 0}));
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
