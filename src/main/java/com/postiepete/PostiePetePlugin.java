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
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.NpcLootReceived;
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

	// "New item added to your collection log: Abyssal whip" (requires the in-game collection log chat notification)
	private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)");

	// "Congratulations, you've completed a hard combat task: Task Name (3 points)."
	private static final Pattern COMBAT_ACHIEVEMENT_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+?)(?: \\(\\d+ points?\\))?\\.?");

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

	private Gson gson;

	private Double lastKillTime;
	private boolean lastKillTimePersonalBest;
	private int lastKillTimeTick;

	@Override
	protected void startUp() throws Exception
	{
		// serializeNulls so that optional fields (e.g. a loot event's duration) are always present, as null
		gson = baseGson.newBuilder().serializeNulls().create();
		log.debug("Postie Pete started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		lastKillTime = null;
		log.debug("Postie Pete stopped!");
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

		postEvent(new LootEvent(common(localPlayer), npc.getName(), killTime,
			killTime != null && lastKillTimePersonalBest, toDrops(event.getItems())));
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
		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), event.getName(), completionTime,
			completionTime != null && lastKillTimePersonalBest, toDrops(event.getItems())));
	}

	@Subscribe
	public void onChatMessage(ChatMessage event)
	{
		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Matcher matcher = KILL_DURATION_PATTERN.matcher(event.getMessage());
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

		matcher = COLLECTION_LOG_PATTERN.matcher(message);
		if (matcher.matches())
		{
			if (config.sendCollectionLog())
			{
				postEvent(new CollectionLogEvent(common(localPlayer), matcher.group("item")));
			}
			return;
		}

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
			getPlayerItems());
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

	private static List<LootEvent.Drop> toDrops(Collection<ItemStack> items)
	{
		return items.stream()
			.map(item -> new LootEvent.Drop(item.getId(), item.getQuantity()))
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
		String apiUrl = config.apiUrl();
		if (apiUrl == null || apiUrl.trim().isEmpty())
		{
			return;
		}

		HttpUrl baseUrl = HttpUrl.parse(apiUrl.trim());
		if (baseUrl == null)
		{
			log.debug("Invalid API URL: {}", apiUrl);
			return;
		}

		HttpUrl url = baseUrl.newBuilder()
			.addPathSegment("events")
			.build();

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
