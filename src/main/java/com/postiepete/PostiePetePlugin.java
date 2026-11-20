package com.postiepete;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.GameState;
import net.runelite.api.Player;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.Subscribe;
import net.runelite.client.events.ConfigChanged;
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

	// "New item added to your collection log: Abyssal whip" (requires the in-game collection log chat notification)
	private static final Pattern COLLECTION_LOG_PATTERN = Pattern.compile(
		"New item added to your collection log: (?<item>.+)");

	// "Congratulations, you've completed a hard combat task: Task Name (3 points)."
	private static final Pattern COMBAT_ACHIEVEMENT_PATTERN = Pattern.compile(
		"Congratulations, you've completed an? (?<tier>\\w+) combat task: (?<task>.+?)(?: \\(\\d+ points?\\))?\\.?");

	// The duration message arrives on the kill, the game reports the loot around the same time
	private static final int KILL_TIME_MAX_AGE_TICKS = 30;

	// Reward chests are opened some time after the completion message, ~10 minutes
	private static final int CHEST_TIME_MAX_AGE_TICKS = 1000;

	// RuneLite keeps each plugin's on/off state in the "runelite" config group, under the plugin's class name in
	// lower case. No value means the plugin's default, which for the Loot Tracker is on.
	static final String RUNELITE_GROUP = "runelite";
	static final String LOOT_TRACKER_KEY = "loottrackerplugin";

	static final String LOOT_TRACKER_OFF_MESSAGE = "Postie Pete can't send loot while the Loot Tracker plugin is"
		+ " turned off. Turn it on in RuneLite's plugin list.";

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

	@Inject
	private ClientThread clientThread;

	private Gson gson;

	private Double lastKillTime;
	private boolean lastKillTimePersonalBest;
	private int lastKillTimeTick;

	// Whether the Loot Tracker has been checked since logging in, so the warning shows once per login, not per hop
	private boolean lootTrackerChecked;

	@Override
	protected void startUp() throws Exception
	{
		// serializeNulls so that optional fields (e.g. a loot event's duration) are always present, as null
		gson = baseGson.newBuilder().serializeNulls().create();
		// Turned on while already logged in: no login to wait for
		lootTrackerChecked = false;
		clientThread.invokeLater(() ->
		{
			if (client.getGameState() == GameState.LOGGED_IN)
			{
				lootTrackerChecked = true;
				warnIfLootTrackerOff();
			}
		});
		log.debug("Postie Pete started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		lastKillTime = null;
		log.debug("Postie Pete stopped!");
	}

	/**
	 * Fired by the Loot Tracker plugin, for NPC kills and for non-NPC sources (reward chests,
	 * clue caskets etc.). Both are sent as the same loot event with the Loot Tracker name (e.g. "Vorkath",
	 * "Fractured Archive") as the source, so the API can treat them the same way.
	 *
	 * NPC loot is the list the game itself reports for the kill, wherever the items end up. RuneLite's
	 * NpcLootReceived only sees items that land under the dead NPC, so it misses bosses like the Mad Angel.
	 * Pickpocket and player kill loot have their own types and are not sent.
	 */
	@Subscribe
	public void onLootReceived(LootReceived event)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		if (event.getType() == LootRecordType.NPC)
		{
			onNpcLoot(localPlayer, event);
			return;
		}

		if (event.getType() != LootRecordType.EVENT)
		{
			return;
		}

		Double completionTime = consumeKillTime(CHEST_TIME_MAX_AGE_TICKS);
		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), event.getName(), null, completionTime,
			completionTime != null && lastKillTimePersonalBest, toDrops(event.getItems())));
	}

	private void onNpcLoot(Player localPlayer, LootReceived event)
	{
		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		Double killTime = consumeKillTime(KILL_TIME_MAX_AGE_TICKS);
		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), event.getName(), npcId(event.getMetadata()), killTime,
			killTime != null && lastKillTimePersonalBest, toDrops(event.getItems())));
	}

	/**
	 * The Loot Tracker passes the NPC id as the metadata of NPC loot, except for a few NPCs where it
	 * passes a map instead (Cave horror has the id under "id"). Anything else has no id.
	 */
	static Integer npcId(Object metadata)
	{
		if (metadata instanceof Integer)
		{
			return (Integer) metadata;
		}
		if (metadata instanceof Map && ((Map<?, ?>) metadata).get("id") instanceof Integer)
		{
			return (Integer) ((Map<?, ?>) metadata).get("id");
		}
		return null;
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// The Loot Tracker warning shows once per login, so a hop doesn't repeat it
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN)
		{
			lootTrackerChecked = false;
		}
		else if (state == GameState.LOGGED_IN && !lootTrackerChecked)
		{
			lootTrackerChecked = true;
			// Once the login's game state change has been handled, so the chatbox is ready
			clientThread.invokeLater(this::warnIfLootTrackerOff);
		}
	}

	/**
	 * Warns straight away when the Loot Tracker is turned off, or loot sending is turned on while it is off.
	 */
	@Subscribe
	public void onConfigChanged(ConfigChanged event)
	{
		boolean lootTrackerChanged = RUNELITE_GROUP.equals(event.getGroup()) && LOOT_TRACKER_KEY.equals(event.getKey());
		boolean sendLootChanged = PostiePeteConfig.GROUP.equals(event.getGroup())
			&& PostiePeteConfig.SEND_LOOT_KEY.equals(event.getKey());
		if (lootTrackerChanged || sendLootChanged)
		{
			clientThread.invokeLater(this::warnIfLootTrackerOff);
		}
	}

	/**
	 * Loot only reaches this plugin through the Loot Tracker's LootReceived event, so with the Loot Tracker
	 * turned off nothing is sent. Tells the player in chat, if they are logged in and want loot sent.
	 */
	private void warnIfLootTrackerOff()
	{
		if (client.getGameState() != GameState.LOGGED_IN || !config.sendLoot()
			|| isPluginOn(configManager.getConfiguration(RUNELITE_GROUP, LOOT_TRACKER_KEY)))
		{
			return;
		}
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", LOOT_TRACKER_OFF_MESSAGE, null);
	}

	/**
	 * Reads a plugin's on/off config value: on unless it says "false". No value means on, the Loot Tracker's default.
	 */
	static boolean isPluginOn(String value)
	{
		return value == null || Boolean.parseBoolean(value);
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
			accountHash());
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

	private List<LootEvent.Drop> toDrops(Collection<ItemStack> items)
	{
		return items.stream()
			.map(item -> new LootEvent.Drop(
				item.getId(),
				itemManager.getItemComposition(item.getId()).getName(),
				item.getQuantity()))
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
