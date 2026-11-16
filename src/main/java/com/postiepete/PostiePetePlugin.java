package com.postiepete;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
import net.runelite.client.game.ItemManager;
import net.runelite.client.game.ItemStack;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;
import net.runelite.client.plugins.loottracker.LootReceived;
import net.runelite.client.util.Text;
import net.runelite.http.api.loottracker.LootRecordType;
import okhttp3.Call;
import okhttp3.Callback;
import okhttp3.HttpUrl;
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;

@Slf4j
@PluginDescriptor(
	name = "Postie Pete",
	description = "Collects in-game events like drops and kills for tracking over time."
)
public class PostiePetePlugin extends Plugin
{
	private static final MediaType JSON = MediaType.parse("application/json; charset=utf-8");

	// Matches boss kill duration messages, e.g. "Fight duration: <col=ff0000>1:23.40</col>. Personal best: 1:20.00"
	// or "Fight duration: <col=ff0000>1:19.80</col> (new personal best)". Same formats as RuneLite's chat commands plugin.
	private static final Pattern KILL_DURATION_PATTERN = Pattern.compile(
		"(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:"
			+ "|The Fractured Archive: Total completion time:) "
			+ "(?:<col=[0-9a-f]{6}>|@.+?@)(?<time>[0-9:]+(?:\\.[0-9]+)?)</col>(?<newpb> \\(new personal best\\))?");

	// "Congratulations, you've completed a hard combat task: Task Name (3 points)."
	private static final Pattern COMBAT_ACHIEVEMENT_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+?)(?: \\(\\d+ points?\\))?\\.?");

	// "Wave 2 completed! Wave duration: @mes_hl_red@1:09.60</col>"
	private static final Pattern COLOSSEUM_WAVE_PATTERN = Pattern.compile("Wave (?<wave>\\d+) completed!");

	// "Delve level: 2 duration: @mes_hl_red@1:19.80</col>. Personal best: ..." (Doom of Mokhaiotl)
	private static final Pattern DOOM_LEVEL_PATTERN = Pattern.compile("Delve level: (?<level>\\d+)\\+? duration:");

	// "Total duration: @mes_hl_red@2:03.00</col>", sent right after the delve level duration
	private static final Pattern DOOM_TOTAL_DURATION_PATTERN = Pattern.compile(
		"Total duration: (?:<col=[0-9a-f]{6}>|@.+?@)(?<time>[0-9:]+(?:\\.[0-9]+)?)");

	private static final String COLOSSEUM = "Fortis Colosseum";
	private static final String DOOM_OF_MOKHAIOTL = "Doom of Mokhaiotl";

	// A full run, the only kind that gets a duration. Doom can continue past level 8, but only levels 1-8 are timed.
	private static final int COLOSSEUM_FULL_RUN_WAVES = 12;
	private static final int DOOM_FULL_RUN_LEVELS = 8;

	// The duration message arrives on the kill, the loot spawns after the death animation
	private static final int KILL_TIME_MAX_AGE_TICKS = 30;

	// Reward chests are opened some time after the completion message, ~10 minutes
	private static final int CHEST_TIME_MAX_AGE_TICKS = 1000;

	// Regular rune pouch uses the first 3 slots, divine rune pouch uses all 4
	private static final int[] RUNE_POUCH_TYPE_VARBITS = {
		VarbitID.RUNE_POUCH_TYPE_1,
		VarbitID.RUNE_POUCH_TYPE_2,
		VarbitID.RUNE_POUCH_TYPE_3,
		VarbitID.RUNE_POUCH_TYPE_4
	};

	private static final int[] RUNE_POUCH_QUANTITY_VARBITS = {
		VarbitID.RUNE_POUCH_QUANTITY_1,
		VarbitID.RUNE_POUCH_QUANTITY_2,
		VarbitID.RUNE_POUCH_QUANTITY_3,
		VarbitID.RUNE_POUCH_QUANTITY_4
	};

	@Inject
	private Client client;

	@Inject
	private PostiePeteConfig config;

	@Inject
	private OkHttpClient okHttpClient;

	@Inject
	private Gson baseGson;

	@Inject
	private ItemManager itemManager;

	@Inject
	private ConfigManager configManager;

	private Gson gson;

	private Double lastKillTime;
	private boolean lastKillTimePersonalBest;
	private int lastKillTimeTick;

	private Integer lastColosseumWave;
	private Integer lastDoomLevel;
	private Double doomFullRunDuration;

	// The collection log overview's "most recent item" varp is set to the item ID of each new
	// collection log item. On login the game sends the existing value too, so changes are only
	// treated as new items once the player is fully logged in and a baseline has been taken.
	private boolean collectionLogReady;
	private int lastCollectionLogItemId;

	@Override
	protected void startUp() throws Exception
	{
		migrateApiUrl();

		// serializeNulls so that optional fields (e.g. a loot event's duration) are always present, as null
		gson = baseGson.newBuilder().serializeNulls().create();
		log.debug("Postie Pete started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		lastKillTime = null;
		lastColosseumWave = null;
		lastDoomLevel = null;
		doomFullRunDuration = null;
		collectionLogReady = false;
		lastCollectionLogItemId = 0;
		log.debug("Postie Pete stopped!");
	}

	/**
	 * Older versions had a base "API URL" setting and always posted to {base}/events. Converts
	 * that to the full endpoint URL setting, once, so existing users keep posting to the same URL.
	 */
	private void migrateApiUrl()
	{
		String legacyUrl = configManager.getConfiguration(PostiePeteConfig.GROUP, PostiePeteConfig.LEGACY_API_URL_KEY);
		if (legacyUrl == null)
		{
			return;
		}

		if (!legacyUrl.trim().isEmpty()
			&& configManager.getConfiguration(PostiePeteConfig.GROUP, PostiePeteConfig.ENDPOINT_URL_KEY) == null)
		{
			String endpointUrl = toEndpointUrl(legacyUrl);
			configManager.setConfiguration(PostiePeteConfig.GROUP, PostiePeteConfig.ENDPOINT_URL_KEY, endpointUrl);
			log.debug("Migrated API URL {} to endpoint URL {}", legacyUrl, endpointUrl);
		}
		configManager.unsetConfiguration(PostiePeteConfig.GROUP, PostiePeteConfig.LEGACY_API_URL_KEY);
	}

	/**
	 * The endpoint URL older versions posted to for a given base URL: the base with an "events" path
	 * segment appended. A value that isn't a valid URL is returned trimmed but otherwise unchanged.
	 */
	static String toEndpointUrl(String baseUrl)
	{
		String trimmed = baseUrl.trim();
		HttpUrl parsed = HttpUrl.parse(trimmed);
		if (parsed == null)
		{
			return trimmed;
		}
		return parsed.newBuilder().addPathSegment("events").build().toString();
	}

	@Subscribe
	public void onNpcLootReceived(NpcLootReceived event)
	{
		Player localPlayer = client.getLocalPlayer();
		NPC npc = event.getNpc();
		if (localPlayer == null || npc == null)
		{
			return;
		}

		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		Double killTime = consumeKillTime(KILL_TIME_MAX_AGE_TICKS);
		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), npc.getName(), npc.getId(), killTime,
			killTime != null && lastKillTimePersonalBest, null, toDrops(event.getItems())));
	}

	/**
	 * Fired by the Loot Tracker plugin. NPC loot is already handled by onNpcLootReceived,
	 * so only non-NPC sources (reward chests, clue caskets etc.) are handled here. These are
	 * sent as the same loot event with the Loot Tracker name (e.g. "Fractured Archive") as the
	 * source, so the API can treat them the same way.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || event.getType() != LootRecordType.EVENT)
		{
			return;
		}

		Double completionTime = consumeKillTime(CHEST_TIME_MAX_AGE_TICKS);
		boolean personalBest = completionTime != null && lastKillTimePersonalBest;
		Integer stage = null;

		// Loot can be claimed after any wave / delve level. Send how far the run got, and a duration
		// only for a full run. The game only reports per-wave / per-level personal bests, so the API
		// derives run personal bests from the durations.
		if (COLOSSEUM.equals(event.getName()))
		{
			stage = lastColosseumWave;
			completionTime = stage != null && stage >= COLOSSEUM_FULL_RUN_WAVES
				? ticksToSeconds(client.getVarpValue(VarPlayerID.COLOSSEUM_TOTAL_DURATION))
				: null;
			personalBest = false;
		}
		else if (DOOM_OF_MOKHAIOTL.equals(event.getName()))
		{
			// The Loot Tracker passes the delve level as metadata
			stage = event.getMetadata() instanceof Integer ? (Integer) event.getMetadata() : null;
			completionTime = stage != null && stage >= DOOM_FULL_RUN_LEVELS ? doomFullRunDuration : null;
			personalBest = false;
		}
		lastColosseumWave = null;
		lastDoomLevel = null;
		doomFullRunDuration = null;

		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), event.getName(), null, completionTime, personalBest, stage,
			toDrops(event.getItems())));
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// LOGGED_IN also follows every loading screen, so only a real logout or hop resets this
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			collectionLogReady = false;
		}
	}

	/**
	 * Takes the collection log baseline on the first tick after logging in (or after the plugin is
	 * enabled mid-session), by which point the game has sent the existing value.
	 */
	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!collectionLogReady && client.getGameState() == GameState.LOGGED_IN)
		{
			lastCollectionLogItemId = client.getVarpValue(VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM0);
			collectionLogReady = true;
			log.debug("Collection log baseline: {}", lastCollectionLogItemId);
		}
	}

	@Subscribe
	public void onVarbitChanged(VarbitChanged event)
	{
		if (event.getVarpId() != VarPlayerID.COLLECTION_OVERVIEW_LAST_ITEM0 || event.getVarbitId() != -1)
		{
			return;
		}

		int itemId = event.getValue();
		if (!collectionLogReady)
		{
			log.debug("Ignoring collection log varp change to {} during login", itemId);
			return;
		}
		if (itemId <= 0 || itemId == lastCollectionLogItemId)
		{
			return;
		}
		lastCollectionLogItemId = itemId;

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || !config.sendCollectionLog())
		{
			return;
		}

		String itemName = itemManager.getItemComposition(itemId).getName();
		postEvent(new CollectionLogEvent(common(localPlayer), itemName, itemId));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Matcher matcher = COLOSSEUM_WAVE_PATTERN.matcher(event.getMessage());
		if (matcher.lookingAt())
		{
			lastColosseumWave = Integer.parseInt(matcher.group("wave"));
			return;
		}

		matcher = DOOM_LEVEL_PATTERN.matcher(event.getMessage());
		if (matcher.lookingAt())
		{
			lastDoomLevel = Integer.parseInt(matcher.group("level"));
			if (lastDoomLevel == 1)
			{
				doomFullRunDuration = null;
			}
			return;
		}

		// Keep the run time at the end of level 8; time spent on deeper levels doesn't count
		matcher = DOOM_TOTAL_DURATION_PATTERN.matcher(event.getMessage());
		if (matcher.lookingAt())
		{
			if (lastDoomLevel != null && lastDoomLevel >= DOOM_FULL_RUN_LEVELS && doomFullRunDuration == null)
			{
				doomFullRunDuration = parseDuration(matcher.group("time"));
			}
			return;
		}

		matcher = KILL_DURATION_PATTERN.matcher(event.getMessage());
		if (matcher.find())
		{
			lastKillTime = parseDuration(matcher.group("time"));
			lastKillTimePersonalBest = matcher.group("newpb") != null;
			lastKillTimeTick = client.getTickCount();
			return;
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		String message = Text.removeTags(event.getMessage());

		matcher = COMBAT_ACHIEVEMENT_PATTERN.matcher(message);
		if (matcher.matches() && config.sendCombatAchievement())
		{
			postEvent(new CombatAchievementEvent(common(localPlayer),
				matcher.group("tier").toLowerCase(), matcher.group("task")));
		}
	}

	/**
	 * Captures the fields every event type shares, at the moment the event happens.
	 */
	private PostieEvent.Common common(Player localPlayer)
	{
		return new PostieEvent.Common(
			Instant.now().truncatedTo(ChronoUnit.SECONDS).toString(),
			localPlayer.getName(),
			accountHash(),
			config.includePlayerItems() ? getPlayerItems() : null);
	}

	/**
	 * Returns the last reported duration if it is recent enough, and consumes it so it
	 * can't be attached to a later, unrelated event.
	 */
	private Double consumeKillTime(int maxAgeTicks)
	{
		Double killTime = null;
		if (lastKillTime != null && client.getTickCount() - lastKillTimeTick <= maxAgeTicks)
		{
			killTime = lastKillTime;
		}
		lastKillTime = null;
		return killTime;
	}

	/**
	 * Parses "1:23", "1:23.40" or "1:02:03.40" into seconds.
	 */
	private static double parseDuration(String duration)
	{
		String[] parts = duration.split(":");
		double seconds = 0;
		for (int i = 0; i < parts.length - 1; i++)
		{
			seconds = (seconds + Integer.parseInt(parts[i])) * 60;
		}
		return seconds + Double.parseDouble(parts[parts.length - 1]);
	}

	/**
	 * Converts a game tick count (0.6s each) to seconds, or null if no ticks were counted.
	 */
	private static Double ticksToSeconds(int ticks)
	{
		return ticks > 0 ? ticks * 6 / 10.0 : null;
	}

	/**
	 * Always uses the wiki real-time price, regardless of the player's "Use wiki item prices"
	 * setting, so values are comparable between players.
	 */
	private List<LootEvent.Drop> toDrops(Collection<ItemStack> items)
	{
		return items.stream()
			.map(item -> new LootEvent.Drop(
				item.getId(),
				itemManager.getItemComposition(item.getId()).getName(),
				item.getQuantity(),
				itemManager.getItemPriceWithSource(item.getId(), true)))
			.collect(Collectors.toList());
	}

	/**
	 * The logged-in account's hash as a string, or null if not logged in.
	 */
	private String accountHash()
	{
		long hash = client.getAccountHash();
		return hash == -1 ? null : Long.toString(hash);
	}

	private PlayerItems getPlayerItems()
	{
		Map<String, PlayerItems.SlotItem> equipment = new LinkedHashMap<>();
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
			{
				Item item = worn.getItem(slot.getSlotIdx());
				if (item != null)
				{
					equipment.put(slot.name(), new PlayerItems.SlotItem(item.getId(), item.getQuantity()));
				}
			}
		}

		Map<String, PlayerItems.SlotItem> inventory = new LinkedHashMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			for (int slot = 0; slot < inv.size(); slot++)
			{
				Item item = inv.getItem(slot);
				if (item != null)
				{
					inventory.put(String.valueOf(slot), new PlayerItems.SlotItem(item.getId(), item.getQuantity()));
				}
			}
		}

		return new PlayerItems(equipment, inventory, getRunePouch(), getQuiver());
	}

	/**
	 * Raw contents of the extra ammo slot (Dizana's quiver / bolt pouch). The game retains this
	 * even when no quiver is worn, so the API decides whether it applies based on the equipment.
	 */
	private PlayerItems.SlotItem getQuiver()
	{
		int ammoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		int ammoQuantity = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		if (ammoId == -1 || ammoQuantity <= 0)
		{
			return null;
		}
		return new PlayerItems.SlotItem(ammoId, ammoQuantity);
	}

	/**
	 * Raw contents of the rune pouch. The game retains this even when no pouch is carried,
	 * so the API decides whether it applies based on the inventory. Null if the pouch is empty.
	 */
	private List<PlayerItems.SlotItem> getRunePouch()
	{
		EnumComposition runeEnum = client.getEnum(EnumID.RUNEPOUCH_RUNE);
		List<PlayerItems.SlotItem> runes = new ArrayList<>();
		for (int i = 0; i < RUNE_POUCH_TYPE_VARBITS.length; i++)
		{
			int type = client.getVarbitValue(RUNE_POUCH_TYPE_VARBITS[i]);
			int quantity = client.getVarbitValue(RUNE_POUCH_QUANTITY_VARBITS[i]);
			if (type == 0 || quantity <= 0)
			{
				continue;
			}

			int runeId = runeEnum.getIntValue(type);
			if (runeId > 0)
			{
				runes.add(new PlayerItems.SlotItem(runeId, quantity));
			}
		}
		return runes.isEmpty() ? null : runes;
	}

	private void postEvent(PostieEvent event)
	{
		String endpointUrl = config.endpointUrl();
		if (endpointUrl == null || endpointUrl.trim().isEmpty())
		{
			return;
		}

		HttpUrl url = HttpUrl.parse(endpointUrl.trim());
		if (url == null)
		{
			log.debug("Invalid endpoint URL: {}", endpointUrl);
			return;
		}

		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, gson.toJson(event)))
			.build();

		okHttpClient.newCall(request).enqueue(new Callback()
		{
			@Override
			public void onFailure(Call call, IOException e)
			{
				log.debug("Failed to send event to {}", url, e);
			}

			@Override
			public void onResponse(Call call, Response response)
			{
				try (response)
				{
					if (!response.isSuccessful())
					{
						log.debug("Event POST to {} returned HTTP {}", url, response.code());
					}
				}
			}
		});
	}

	@Provides
	PostiePeteConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PostiePeteConfig.class);
	}
}
