package com.postiepete;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
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
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarClientID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
import net.runelite.client.callback.ClientThread;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.eventbus.EventBus;
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

	// "CA_ID:648|Congratulations, you've completed a hard combat task: @ach_comp@Task Name (3 points).", tags removed.
	// The game puts the task id in front of the message and colours the task name with an @...@ code, which
	// Text.removeTags leaves in. Older messages came without either. The id is the one snapshots send.
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

	// Fractured Archive party slots: a non-zero status means the slot is taken, and the name is the player in it
	private static final int[] TFA_PARTY_SLOT_VARBITS = {
		VarbitID.TFA_CLIENT_P0,
		VarbitID.TFA_CLIENT_P1,
		VarbitID.TFA_CLIENT_P2,
		VarbitID.TFA_CLIENT_P3,
		VarbitID.TFA_CLIENT_P4,
		VarbitID.TFA_CLIENT_P5,
		VarbitID.TFA_CLIENT_P6,
		VarbitID.TFA_CLIENT_P7
	};

	private static final int[] TFA_PARTY_NAME_VARCS = {
		VarClientID.TFA_CLIENT_NAME0,
		VarClientID.TFA_CLIENT_NAME1,
		VarClientID.TFA_CLIENT_NAME2,
		VarClientID.TFA_CLIENT_NAME3,
		VarClientID.TFA_CLIENT_NAME4,
		VarClientID.TFA_CLIENT_NAME5,
		VarClientID.TFA_CLIENT_NAME6,
		VarClientID.TFA_CLIENT_NAME7
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

	@Inject
	private ClientThread clientThread;

	@Inject
	private EventBus eventBus;

	@Inject
	private CollectionLogReader collectionLogReader;

	@Inject
	private CombatAchievementReader combatAchievementReader;

	private Gson gson;

	private Double lastKillTime;
	private boolean lastKillTimePersonalBest;
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
		migrateApiUrl();

		// serializeNulls so that optional fields (e.g. a loot event's duration) are always present, as null
		gson = baseGson.newBuilder().serializeNulls().create();
		collectionLogReader.setOnRead(this::onCollectionLogRead);
		eventBus.register(collectionLogReader);
		combatAchievementReader.setOnRead(this::onCombatAchievementsRead);
		eventBus.register(combatAchievementReader);
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
		eventBus.unregister(collectionLogReader);
		collectionLogReader.reset();
		eventBus.unregister(combatAchievementReader);
		combatAchievementReader.reset();
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

	/**
	 * Fired by the Loot Tracker plugin, for NPC kills and for non-NPC sources (reward chests,
	 * clue caskets etc.). Both are sent as the same loot event, chests with the Loot Tracker name
	 * (e.g. "Fractured Archive") as the source, so the API can treat them the same way.
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
		LootEvent.Kill kill = killCounts.claimForChest(event.getName(), client.getTickCount());
		boolean personalBest = completionTime != null && lastKillTimePersonalBest;
		Integer stage = null;
		LootEvent.Raid raid = null;

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
		else if (FracturedArchive.LOOT_SOURCE.equals(event.getName()))
		{
			raid = fracturedArchive.consumeRaid(fracturedArchiveTeamSize(), fracturedArchiveTeam());
		}
		lastColosseumWave = null;
		lastDoomLevel = null;
		doomFullRunDuration = null;

		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), event.getName(), null, completionTime, personalBest, stage,
			raid, kill, toDrops(event.getItems())));
	}

	private void onNpcLoot(Player localPlayer, LootReceived event)
	{
		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		Double killTime = consumeKillTime(KILL_TIME_MAX_AGE_TICKS);
		LootEvent.Kill kill = killCounts.claimForNpcLoot(client.getTickCount());
		log.debug("NPC loot: {} (tick {}), kill count {}", event.getName(), client.getTickCount(), kill);
		if (!config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), event.getName(), npcId(event.getMetadata()), killTime,
			killTime != null && lastKillTimePersonalBest, null, null, kill, toDrops(event.getItems())));
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

	/**
	 * Tracks which Fractured Archive path bosses the team fights. Only hitsplats the player can see
	 * are reported, so this only picks up bosses in the player's own raid.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (event.getActor() instanceof NPC)
		{
			fracturedArchive.onNpcHit(((NPC) event.getActor()).getId());
		}
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
	 * kill with its duration. The source is the kill count message's name.
	 */
	private void onKillWithoutLoot(LootEvent.Kill kill)
	{
		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		Double killTime = consumeKillTime(KILL_TIME_MAX_AGE_TICKS);
		Player localPlayer = client.getLocalPlayer();
		log.debug("Kill without loot: {} {}, duration {}", kill.getName(), kill.getCount(), killTime);
		if (localPlayer == null || !config.sendLoot())
		{
			return;
		}

		postEvent(new LootEvent(common(localPlayer), kill.getName(), null, killTime,
			killTime != null && lastKillTimePersonalBest, null, null, kill, Collections.emptyList()));
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
		// Purples might be announced as a notification rather than a game message; both come from the game
		if (event.getType() == ChatMessageType.GAMEMESSAGE || event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			Player player = client.getLocalPlayer();
			String localName = player == null || player.getName() == null ? null : Text.sanitize(player.getName());
			if (fracturedArchive.onMessage(Text.removeTags(event.getMessage()), localName))
			{
				return;
			}
		}

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
			lastKillTime = parseDuration(matcher.group("time"));
			lastKillTimePersonalBest = matcher.group("newpb") != null;
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

		// Read the team now, while the raid's party is certain to be loaded; the chest is opened later
		if (message.startsWith(FracturedArchive.COMPLETION_MESSAGE))
		{
			fracturedArchive.onRaidCompleted(fracturedArchiveTeamSize(), fracturedArchiveTeam());
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
	 * The whole collection log was read after the player opened it.
	 */
	private void onCollectionLogRead(List<CollectionLogSnapshotEvent.Item> items, int obtainedSlots)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || !config.sendCollectionLogSnapshot())
		{
			return;
		}

		postEvent(new CollectionLogSnapshotEvent(common(localPlayer), items, obtainedSlots));
	}

	/**
	 * Every completed combat task was read after login or opening the combat achievements interface.
	 */
	private void onCombatAchievementsRead(List<Integer> completedTaskIds, int points, String tier,
		List<CombatAchievementSnapshotEvent.TierProgress> tiers)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || !config.sendCombatAchievementSnapshot())
		{
			return;
		}

		postEvent(new CombatAchievementSnapshotEvent(common(localPlayer), completedTaskIds, points, tier, tiers));
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
	 * Number of occupied Fractured Archive party slots, or null if not in a party.
	 */
	private Integer fracturedArchiveTeamSize()
	{
		int size = 0;
		for (int varbit : TFA_PARTY_SLOT_VARBITS)
		{
			if (client.getVarbitValue(varbit) != 0)
			{
				size++;
			}
		}
		return size > 0 ? size : null;
	}

	/**
	 * Names in the occupied Fractured Archive party slots, in slot order, or null if not in a party.
	 */
	private List<String> fracturedArchiveTeam()
	{
		List<String> team = new ArrayList<>();
		for (int i = 0; i < TFA_PARTY_SLOT_VARBITS.length; i++)
		{
			if (client.getVarbitValue(TFA_PARTY_SLOT_VARBITS[i]) == 0)
			{
				continue;
			}

			String name = client.getVarcStrValue(TFA_PARTY_NAME_VARCS[i]);
			if (name != null && !name.isEmpty())
			{
				team.add(Text.sanitize(Text.removeTags(name)));
			}
		}
		log.debug("Fractured Archive team: {}", team);
		return team.isEmpty() ? null : team;
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
