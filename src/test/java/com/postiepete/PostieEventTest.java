package com.postiepete;

import static org.junit.Assert.assertEquals;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonObject;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.junit.Test;

public class PostieEventTest
{
	private static final Gson GSON = new GsonBuilder().serializeNulls().create();

	private static final PostieEvent.Common COMMON = new PostieEvent.Common(
		"2026-11-12T20:35:00Z", "ExamplePlayer", "-3412345678901234567");

	private static final Set<String> COMMON_FIELDS = set("eventType", "timestamp", "rsn", "accountHash");

	private static final List<String> SOURCE_FIELDS = Arrays.asList(
		"name", "lootName", "npcId", "killCount", "duration", "personalBest", "newPersonalBest", "wave");

	@Test
	public void lootEventHasOnlyLootFields()
	{
		JsonObject json = toJson(new LootEvent(COMMON,
			LootEvent.Source.of("Mad Angel", new LootEvent.Kill("Mad Angel", 212), 16305,
				new LootEvent.KillTime(48.6, 41.2, false), null),
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
		assertEquals(false, source.get("newPersonalBest").getAsBoolean());
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
		LootEvent.Raid raid = new LootEvent.Raid(
			Arrays.asList(new LootEvent.Split("Archive Guardian", 123.0), new LootEvent.Split("Ezekiel", 141.6)));
		JsonObject json = toJson(new LootEvent(COMMON,
			LootEvent.Source.of("Fractured Archive", new LootEvent.Kill("Fractured Archive", 75), null,
				new LootEvent.KillTime(1534.2, 1534.2, true), null),
			raid, Collections.emptyList()));

		JsonObject raidJson = json.getAsJsonObject("raid");
		assertEquals(set("splits"), raidJson.keySet());
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
	public void lootWithoutKillCountIsNamedByTheLootTracker()
	{
		LootEvent.Source source = LootEvent.Source.of("Reward cart (Wintertodt)", null, null, null, null);
		assertEquals("Reward cart (Wintertodt)", source.getName());
		assertEquals(null, source.getLootName());
		assertEquals(null, source.getNpcId());
		assertEquals(null, source.getKillCount());
		assertEquals(null, source.getDuration());
		assertEquals(null, source.getPersonalBest());
		assertEquals(false, source.isNewPersonalBest());
	}

	@Test
	public void lootIsNamedByItsKillCountMessageAndKeepsADifferentLootTrackerName()
	{
		LootEvent.Source source = LootEvent.Source.of("Theatre of Blood", new LootEvent.Kill("Theatre of Blood: Hard Mode", 40),
			null, new LootEvent.KillTime(1234.5, null, false), null);
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
			new LootEvent.KillTime(3720.0, 3720.0, true), null);
		assertEquals("TzKal-Zuk", source.getName());
		assertEquals(true, source.isNewPersonalBest());
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
	public void combatAchievementEventHasOnlyCombatAchievementFields()
	{
		JsonObject json = toJson(new CombatAchievementEvent(COMMON, "hard", "Noxious Foe", 402));

		assertEquals(with(COMMON_FIELDS, "tier", "task", "taskId"), json.keySet());
		assertEquals("combat_achievement", json.get("eventType").getAsString());
		assertEquals("hard", json.get("tier").getAsString());
		assertEquals(402, json.get("taskId").getAsInt());
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
