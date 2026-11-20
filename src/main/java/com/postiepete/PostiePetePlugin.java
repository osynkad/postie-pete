package com.postiepete;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
import net.runelite.api.events.GameTick;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.VarPlayerID;
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
	// The personal best time can be coloured too.
	static final Pattern KILL_DURATION_PATTERN = Pattern.compile(
		"(?i)(?:(?:Fight |Lap |Challenge |Corrupted challenge )?duration:|Subdued in|(?<!total )completion time:"
			+ "|The Fractured Archive: Total completion time:) "
			+ "(?:<col=[0-9a-f]{6}>|@.+?@)(?<time>[0-9:]+(?:\\.[0-9]+)?)</col>(?:(?<newpb> \\(new personal best\\))"
			+ "|\\.? ?Personal best: (?:</col>)?(?:<col=[0-9a-f]{6}>|@.+?@)?(?<pb>[0-9:]+(?:\\.[0-9]+)?))?");

	// "CA_ID:648|Congratulations, you've completed a hard combat task: @ach_comp@Task Name (3 points).", tags removed.
	// The game puts the task id in front of the message and colours the task name with an @...@ code, which
	// Text.removeTags leaves in. Older messages came without either.
	static final Pattern COMBAT_ACHIEVEMENT_PATTERN = Pattern.compile(
		"(?:CA_ID:(?<id>\\d+)\\|)?Congratulations, you've completed an? (?<tier>\\w+) combat task: (?:@\\w+@)?(?<task>.+?)"
			+ "(?: \\(\\d+ points?\\))?\\.?");

	// "Wave 2 completed! Wave duration: @mes_hl_red@1:09.60</col>"
	private static final Pattern COLOSSEUM_WAVE_PATTERN = Pattern.compile("Wave (?<wave>\\d+) completed!");

	// "Delve level: 2 duration: @mes_hl_red@1:19.80</col>. Personal best: ..." (Doom of Mokhaiotl)
	private static final Pattern DOOM_LEVEL_PATTERN = Pattern.compile("Delve level: (?<level>\\d+)\\+? duration:");

	// "Total duration: @mes_hl_red@2:03.00</col>", sent right after the delve level duration
	private static final Pattern DOOM_TOTAL_DURATION_PATTERN = Pattern.compile(
		"Total duration: (?:<col=[0-9a-f]{6}>|@.+?@)(?<time>[0-9:]+(?:\\.[0-9]+)?)");

	// "Challenge complete: Bork. Duration: <col=ef1020>2:21.00</col>. Total: ...", tags removed (Fractured Archive)
	private static final Pattern TFA_BOSS_COMPLETE_PATTERN = Pattern.compile(
		"Challenge complete: (?<boss>.+?)\\. Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	private static final String COLOSSEUM = "Fortis Colosseum";
	private static final String DOOM_OF_MOKHAIOTL = "Doom of Mokhaiotl";

	// A full run, the only kind that gets a duration. Doom can continue past level 8, but only levels 1-8 are timed.
	private static final int COLOSSEUM_FULL_RUN_WAVES = 12;
	private static final int DOOM_FULL_RUN_LEVELS = 8;

	// The duration message arrives on the kill, the game reports the loot around the same time
	private static final int KILL_TIME_MAX_AGE_TICKS = 30;

	// Reward chests are opened some time after the completion message, ~10 minutes
	private static final int CHEST_TIME_MAX_AGE_TICKS = 1000;

	// NPC loot comes on the kill count message's own tick (seen in game), so a kill still unclaimed after this
	// had no loot. Well within KILL_TIME_MAX_AGE_TICKS, so the kill keeps its duration.
	private static final int KILL_WITHOUT_LOOT_TICKS = 10;

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

	private LootEvent.KillTime lastKillTime;
	private int lastKillTimeTick;

	private Integer lastColosseumWave;
	private Integer lastDoomLevel;
	private Double doomFullRunDuration;

	private final FracturedArchive fracturedArchive = new FracturedArchive();

	private final KillCounts killCounts = new KillCounts(KILL_TIME_MAX_AGE_TICKS, CHEST_TIME_MAX_AGE_TICKS,
		KILL_WITHOUT_LOOT_TICKS);

	// The collection log overview's "most recent item" varp is set to the item ID of each new
	// collection log item. On login the game sends the existing value too, so changes are only
	// treated as new items once the player is fully logged in and a baseline has been taken.
	private boolean collectionLogReady;
	private int lastCollectionLogItemId;

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
		lastColosseumWave = null;
		lastDoomLevel = null;
		doomFullRunDuration = null;
		fracturedArchive.reset();
		killCounts.reset();
		collectionLogReady = false;
		lastCollectionLogItemId = 0;
		log.debug("Postie Pete stopped!");
	}

	/**
	 * Fired by the Loot Tracker plugin, for NPC kills and for non-NPC sources (reward chests,
	 * clue caskets etc.). Both are sent as the same loot event, so the API can treat them the same way. The
	 * source is named by the kill count message that came with the loot, or else by the Loot Tracker.
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

		LootEvent.KillTime completionTime = consumeKillTime(CHEST_TIME_MAX_AGE_TICKS);
		LootEvent.Kill kill = killCounts.claimForChest(event.getName(), client.getTickCount());
		Integer wave = null;
		LootEvent.Raid raid = null;

		// Loot can be claimed after any wave / delve level. Send how far the run got, and a duration
		// only for a full run. The game only reports per-wave / per-level personal bests, so no personal
		// best is sent; the API derives run personal bests from the durations.
		if (COLOSSEUM.equals(event.getName()))
		{
			wave = lastColosseumWave;
			completionTime = wave != null && wave >= COLOSSEUM_FULL_RUN_WAVES
				? withoutPersonalBest(ticksToSeconds(client.getVarpValue(VarPlayerID.COLOSSEUM_TOTAL_DURATION)))
				: null;
		}
		else if (DOOM_OF_MOKHAIOTL.equals(event.getName()))
		{
			// The Loot Tracker passes the delve level as metadata
			wave = event.getMetadata() instanceof Integer ? (Integer) event.getMetadata() : null;
			completionTime = wave != null && wave >= DOOM_FULL_RUN_LEVELS ? withoutPersonalBest(doomFullRunDuration) : null;
		}
		else if (FracturedArchive.LOOT_SOURCE.equals(event.getName()))
		{
			raid = fracturedArchive.consumeRaid();
		}
		lastColosseumWave = null;
		lastDoomLevel = null;
		doomFullRunDuration = null;

		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), LootEvent.Source.of(event.getName(), kill, null, completionTime, wave),
			raid, toDrops(event.getItems())));
	}

	private void onNpcLoot(Player localPlayer, LootReceived event)
	{
		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		LootEvent.KillTime killTime = consumeKillTime(KILL_TIME_MAX_AGE_TICKS);
		LootEvent.Kill kill = killCounts.claimForNpcLoot(client.getTickCount());
		log.debug("NPC loot: {} (tick {}), kill count {}", event.getName(), client.getTickCount(), kill);
		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer),
			LootEvent.Source.of(event.getName(), kill, npcId(event.getMetadata()), killTime, null),
			null, toDrops(event.getItems())));
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
		// LOGGED_IN also follows every loading screen, so only a real logout or hop resets this
		GameState state = event.getGameState();
		if (state == GameState.LOGIN_SCREEN || state == GameState.HOPPING || state == GameState.CONNECTION_LOST)
		{
			collectionLogReady = false;
		}

		// The Loot Tracker warning shows once per login, so a hop doesn't repeat it
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

		LootEvent.Kill killWithoutLoot = killCounts.takeKillWithoutLoot(client.getTickCount());
		if (killWithoutLoot != null)
		{
			onKillWithoutLoot(killWithoutLoot);
		}
	}

	/**
	 * A kill the game reported no loot for (e.g. the Inferno), sent as loot with no drops so it still counts as a
	 * kill with its duration. The source is the kill count message's name, with no Loot Tracker name.
	 */
	private void onKillWithoutLoot(LootEvent.Kill kill)
	{
		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		LootEvent.KillTime killTime = consumeKillTime(KILL_TIME_MAX_AGE_TICKS);
		Player localPlayer = client.getLocalPlayer();
		log.debug("Kill without loot: {} {}, duration {}", kill.getName(), kill.getCount(), killTime);
		if (localPlayer == null || !config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), LootEvent.Source.of(null, kill, null, killTime, null),
			null, Collections.emptyList()));
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

		String message = Text.removeTags(event.getMessage());

		if (message.equals(FracturedArchive.RUN_START_MESSAGE))
		{
			fracturedArchive.onRunStarted();
			return;
		}

		// Checked before KILL_DURATION_PATTERN, which also matches these. A boss's duration
		// isn't a kill time: the raid's loot gets the total completion time.
		matcher = TFA_BOSS_COMPLETE_PATTERN.matcher(message);
		if (matcher.lookingAt())
		{
			fracturedArchive.onBossCompleted(matcher.group("boss"), parseDuration(matcher.group("time")));
			return;
		}

		int tick = client.getTickCount();
		matcher = KILL_DURATION_PATTERN.matcher(event.getMessage());
		if (matcher.find())
		{
			lastKillTime = parseKillTime(matcher);
			lastKillTimeTick = tick;
			return;
		}

		// Not returned: the Fractured Archive's completion message below is also a kill count message
		LootEvent.Kill killCount = killCounts.onMessage(event.getMessage(), tick);
		if (killCount != null)
		{
			log.debug("Kill count: {} {} (tick {})", killCount.getName(), killCount.getCount(), tick);
		}

		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return;
		}

		// Keep the run's splits for the chest, which is opened later
		if (message.startsWith(FracturedArchive.COMPLETION_MESSAGE))
		{
			fracturedArchive.onRaidCompleted();
			return;
		}

		matcher = COMBAT_ACHIEVEMENT_PATTERN.matcher(message);
		if (matcher.matches() && config.sendCombatAchievement())
		{
			String id = matcher.group("id");
			postEvent(new CombatAchievementEvent(common(localPlayer),
				matcher.group("tier").toLowerCase(), matcher.group("task"), id == null ? null : Integer.valueOf(id)));
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
	private LootEvent.KillTime consumeKillTime(int maxAgeTicks)
	{
		LootEvent.KillTime killTime = null;
		if (lastKillTime != null && client.getTickCount() - lastKillTimeTick <= maxAgeTicks)
		{
			killTime = lastKillTime;
		}
		lastKillTime = null;
		return killTime;
	}

	/**
	 * The duration and personal best of a {@link #KILL_DURATION_PATTERN} match: the duration itself for a new
	 * personal best, the "Personal best:" time if the message shows one, otherwise none. newPersonalBest is set
	 * only for "(new personal best)", so a time that ties the personal best is not a new one.
	 */
	static LootEvent.KillTime parseKillTime(Matcher matcher)
	{
		double duration = parseDuration(matcher.group("time"));
		boolean newPersonalBest = matcher.group("newpb") != null;
		Double personalBest = null;
		if (newPersonalBest)
		{
			personalBest = duration;
		}
		else if (matcher.group("pb") != null)
		{
			personalBest = parseDuration(matcher.group("pb"));
		}
		return new LootEvent.KillTime(duration, personalBest, newPersonalBest);
	}

	/**
	 * A duration the game reports no personal best with, or null if there is none.
	 */
	private static LootEvent.KillTime withoutPersonalBest(Double duration)
	{
		return duration != null ? new LootEvent.KillTime(duration, null, false) : null;
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

	/**
	 * The endpoint URLs from the comma-separated setting, in order and without duplicates. Blank entries are
	 * skipped, and invalid ones are logged and skipped.
	 */
	static List<HttpUrl> endpointUrls(String setting)
	{
		if (setting == null)
		{
			return Collections.emptyList();
		}

		Set<HttpUrl> urls = new LinkedHashSet<>();
		for (String part : setting.split(","))
		{
			String trimmed = part.trim();
			if (trimmed.isEmpty())
			{
				continue;
			}

			HttpUrl url = HttpUrl.parse(trimmed);
			if (url == null)
			{
				log.debug("Invalid endpoint URL: {}", trimmed);
				continue;
			}
			urls.add(url);
		}
		return new ArrayList<>(urls);
	}

	private void postEvent(PostieEvent event)
	{
		List<HttpUrl> urls = endpointUrls(config.endpointUrl());
		if (urls.isEmpty())
		{
			return;
		}

		String json = gson.toJson(event);
		for (HttpUrl url : urls)
		{
			post(url, json);
		}
	}

	private void post(HttpUrl url, String json)
	{
		Request request = new Request.Builder()
			.url(url)
			.post(RequestBody.create(JSON, json))
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
