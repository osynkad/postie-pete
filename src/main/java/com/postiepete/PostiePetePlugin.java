package com.postiepete;

import com.google.gson.Gson;
import com.google.inject.Provides;
import java.io.IOException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.LinkedHashMap;
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
import net.runelite.api.EnumComposition;
import net.runelite.api.EnumID;
import net.runelite.api.EquipmentInventorySlot;
import net.runelite.api.GameState;
import net.runelite.api.Item;
import net.runelite.api.ItemContainer;
import net.runelite.api.NPC;
import net.runelite.api.Player;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.events.ChatMessage;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.HitsplatApplied;
import net.runelite.api.events.ItemContainerChanged;
import net.runelite.api.events.PlayerDespawned;
import net.runelite.api.events.PlayerSpawned;
import net.runelite.api.events.VarbitChanged;
import net.runelite.api.gameval.InventoryID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.api.gameval.VarbitID;
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

	// "Wave 2 completed! Wave duration: @mes_hl_red@1:09.60</col>". The time is optional, so the wave is still
	// followed if its format changes.
	static final Pattern COLOSSEUM_WAVE_PATTERN = Pattern.compile(
		"Wave (?<wave>\\d+) completed!(?: Wave duration: (?:<col=[0-9a-f]{6}>|@.+?@)?(?<time>[0-9:]+(?:\\.[0-9]+)?))?");

	// "Delve level: 2 duration: @mes_hl_red@1:19.80</col>. Personal best: ..." (Doom of Mokhaiotl). Levels past 8
	// show the real level in brackets: "Delve level: 8+ (22) duration: 1:08.4. Personal best: 0:54.0" (seen in
	// game). The time is optional, like the Colosseum's.
	static final Pattern DOOM_LEVEL_PATTERN = Pattern.compile(
		"Delve level: (?<level>\\d+)\\+?(?: \\((?<deep>\\d+)\\))? duration:"
			+ "(?: (?:<col=[0-9a-f]{6}>|@.+?@)?(?<time>[0-9:]+(?:\\.[0-9]+)?))?");

	// "<col=ef1020>Wave: 12</col>", tags removed: the start of a Fight Caves or Inferno wave
	static final Pattern TZHAAR_WAVE_PATTERN = Pattern.compile("Wave: (?<wave>\\d+)");

	// The map regions of the Fight Caves and the Inferno, where "Wave: N" messages are theirs
	private static final int FIGHT_CAVES_REGION = 9551;
	private static final int INFERNO_REGION = 9043;

	// The kill count message names that end a Fight Caves and an Inferno run
	private static final String FIGHT_CAVES_KILL = "TzTok-Jad";
	private static final String INFERNO_KILL = "TzKal-Zuk";

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

	// Each raid's party slots, by chest name: non-zero while a player is in the slot. Read when the raid is
	// completed. RuneLite's chat commands plugin counts ToB and ToA team sizes the same way.
	private static final Map<String, int[]> RAID_PARTY_SLOTS = Map.of(
		RaidTeams.THEATRE_OF_BLOOD, new int[]{
			VarbitID.TOB_CLIENT_P0, VarbitID.TOB_CLIENT_P1, VarbitID.TOB_CLIENT_P2, VarbitID.TOB_CLIENT_P3,
			VarbitID.TOB_CLIENT_P4},
		RaidTeams.TOMBS_OF_AMASCUT, new int[]{
			VarbitID.TOA_CLIENT_P0, VarbitID.TOA_CLIENT_P1, VarbitID.TOA_CLIENT_P2, VarbitID.TOA_CLIENT_P3,
			VarbitID.TOA_CLIENT_P4, VarbitID.TOA_CLIENT_P5, VarbitID.TOA_CLIENT_P6, VarbitID.TOA_CLIENT_P7},
		FracturedArchive.LOOT_SOURCE, new int[]{
			VarbitID.TFA_CLIENT_P0, VarbitID.TFA_CLIENT_P1, VarbitID.TFA_CLIENT_P2, VarbitID.TFA_CLIENT_P3,
			VarbitID.TFA_CLIENT_P4, VarbitID.TFA_CLIENT_P5, VarbitID.TFA_CLIENT_P6, VarbitID.TFA_CLIENT_P7});

	// How often stale fight starts and nearby players are dropped
	private static final int PRUNE_INTERVAL_TICKS = 100;

	// Skilling bosses: the players around are a crowd, not a team, so no team size is sent
	private static final Set<String> NO_TEAM = Set.of("Wintertodt", "Tempoross", "Zalcano");

	// "Total duration: @mes_hl_red@2:03.00</col>", sent right after the delve level duration
	private static final Pattern DOOM_TOTAL_DURATION_PATTERN = Pattern.compile(
		"Total duration: (?:<col=[0-9a-f]{6}>|@.+?@)(?<time>[0-9:]+(?:\\.[0-9]+)?)");

	// "Challenge complete: Bork. Duration: <col=ef1020>2:21.00</col>. Total: ...", tags removed (Fractured Archive)
	private static final Pattern TFA_BOSS_COMPLETE_PATTERN = Pattern.compile(
		"Challenge complete: (?<boss>.+?)\\. Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	// "Wave 'The Maiden of Sugadinti' (Normal Mode) complete!Duration: 2:05.40Total: 2:05.40", tags removed: a
	// Theatre of Blood room's completion. Verzik's room is "The Final Challenge".
	static final Pattern TOB_ROOM_COMPLETE_PATTERN = Pattern.compile(
		"Wave '(?<room>.+?)' \\([^)]*\\) complete!Duration: (?<time>[0-9:]+(?:\\.[0-9]+)?)");

	// The first Theatre of Blood room. The raid has no start message, so its completion starts the run.
	static final String TOB_FIRST_ROOM = "The Maiden of Sugadinti";

	// Where the raid's completion time starts in Verzik's room message, in lower case
	private static final String TOB_COMPLETION_TIME = "completion time:";

	private static final String COLOSSEUM = "Fortis Colosseum";
	private static final String SOL_HEREDIT = "Sol Heredit";
	private static final String DOOM_OF_MOKHAIOTL = "Doom of Mokhaiotl";

	// A full run, the only kind that gets a duration. Doom can continue past level 8, but only levels 1-8 are timed.
	private static final int COLOSSEUM_FULL_RUN_WAVES = 12;
	private static final int DOOM_FULL_RUN_LEVELS = 8;

	// The duration message arrives on the kill, the game reports the loot around the same time
	private static final int KILL_TIME_MAX_AGE_TICKS = 30;

	// Reward chests are opened some time after the completion message, ~10 minutes
	private static final int CHEST_TIME_MAX_AGE_TICKS = 1000;

	// NPC loot comes on the kill count message's own tick (seen in game), so it only takes a kill count from
	// then, with a tick to spare. Any older one (e.g. an agility lap count) belongs to something else.
	private static final int NPC_KILL_COUNT_MAX_AGE_TICKS = 2;

	// A kill still unclaimed after this had no loot. Above NPC_KILL_COUNT_MAX_AGE_TICKS, and well within
	// KILL_TIME_MAX_AGE_TICKS, so the kill keeps its duration.
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
	// The raid chest lastKillTime is kept for when it is a raid's completion time, otherwise null
	private String lastKillTimeRaid;

	private Integer lastDoomLevel;
	private Double doomFullRunDuration;

	private final WaveRun colosseum = new WaveRun("Wave");
	private final WaveRun doom = new WaveRun("Level");
	private final WaveRun fightCaves = new WaveRun("Wave");
	private final WaveRun inferno = new WaveRun("Wave");

	private final RaidSplits fracturedArchive = new RaidSplits();
	private final RaidSplits theatreOfBlood = new RaidSplits();
	private final ChambersOfXeric chambersOfXeric = new ChambersOfXeric();

	private final TripGear tripGear = new TripGear();
	private final FightStarts fightStarts = new FightStarts();
	private final NearbyPlayers nearbyPlayers = new NearbyPlayers();

	// The other players in the scene, from their spawn and despawn events
	private final Set<Player> scenePlayers = new HashSet<>();
	private final RaidTeams raidTeams = new RaidTeams();

	private final KillCounts killCounts = new KillCounts(NPC_KILL_COUNT_MAX_AGE_TICKS, CHEST_TIME_MAX_AGE_TICKS,
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
				// The players already in view, which won't get spawn events
				for (Player player : client.getTopLevelWorldView().players())
				{
					if (player != client.getLocalPlayer())
					{
						scenePlayers.add(player);
					}
				}
			}
		});
		log.debug("Postie Pete started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		lastKillTime = null;
		lastKillTimeRaid = null;
		lastDoomLevel = null;
		doomFullRunDuration = null;
		colosseum.reset();
		doom.reset();
		fightCaves.reset();
		inferno.reset();
		fracturedArchive.reset();
		theatreOfBlood.reset();
		chambersOfXeric.reset();
		killCounts.reset();
		tripGear.end();
		fightStarts.reset();
		nearbyPlayers.reset();
		scenePlayers.clear();
		raidTeams.reset();
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

		int tick = client.getTickCount();
		LootEvent.KillTime completionTime = consumeKillTime(event.getName(), chestKillTimeMaxAge(event.getName()));
		Integer fightTimeStart = fightStartTick(completionTime);
		LootEvent.Kill kill = killCounts.claimForChest(event.getName(), tick);
		Integer firstHit = kill != null ? fightStarts.take(kill.getName(), tick) : null;
		Integer wave = null;
		List<LootEvent.Split> splits = null;
		LootEvent.Points points = null;
		WaveRun.Run run = null;
		// Chests with a kill count message are boss kills, and so are these activities' chests without one
		boolean bossKill = kill != null;

		// Loot can be claimed after any wave / delve level. Send how far the run got, and a duration
		// only for a full run. A full Colosseum run's time and personal best come from the game's "Colosseum
		// duration:" message (seen in game), or else the run time the game keeps, without a personal best.
		// Doom only reports per-level personal bests, so its run time has none.
		if (COLOSSEUM.equals(event.getName()))
		{
			wave = colosseum.lastWave();
			run = colosseum.consume();
			// A full run's kill count message is Sol Heredit's. The loot keeps the Colosseum's name, so full and
			// partial runs stay one source.
			LootEvent.Kill solHeredit = killCounts.claimForChest(SOL_HEREDIT, tick);
			if (solHeredit != null)
			{
				kill = new LootEvent.Kill(COLOSSEUM, solHeredit.getCount());
			}
			if (wave == null || wave < COLOSSEUM_FULL_RUN_WAVES)
			{
				completionTime = null;
			}
			else if (completionTime == null)
			{
				completionTime = withoutPersonalBest(ticksToSeconds(client.getVarpValue(VarPlayerID.COLOSSEUM_TOTAL_DURATION)));
			}
			bossKill = true;
		}
		else if (DOOM_OF_MOKHAIOTL.equals(event.getName()))
		{
			// The Loot Tracker passes the delve level as metadata
			wave = event.getMetadata() instanceof Integer ? (Integer) event.getMetadata() : null;
			run = doom.consume();
			completionTime = wave != null && wave >= DOOM_FULL_RUN_LEVELS ? withoutPersonalBest(doomFullRunDuration) : null;
			bossKill = true;
		}
		else if (FracturedArchive.LOOT_SOURCE.equals(event.getName()))
		{
			splits = fracturedArchive.consumeSplits();
			bossKill = true;
		}
		else if (RaidTeams.THEATRE_OF_BLOOD.equals(event.getName()))
		{
			// The run so far: the chest is in Verzik's room, so no new run can start before it's opened
			splits = theatreOfBlood.consumeSplits();
		}
		else if (RaidTeams.CHAMBERS_OF_XERIC.equals(event.getName()))
		{
			splits = chambersOfXeric.consumeSplits();
			points = chambersOfXeric.consumePoints();
		}
		Integer runStart = run != null ? run.getStartTick() : null;
		if (run != null)
		{
			splits = run.getSplits();
		}
		lastDoomLevel = null;
		doomFullRunDuration = null;

		// Raids have the game's party size, read when the raid was completed (CoX's comes from its completion
		// message)
		boolean raidChest = event.getName() != null && event.getName().equals(RaidTeams.chestName(event.getName()));
		Integer raidSize = raidChest ? raidTeams.takeSize(event.getName()) : null;
		bossKill |= raidChest;

		// A chest with no time from the game (e.g. Barrows) is timed from the first hit. Runs and raids keep
		// their own rules above.
		if (completionTime == null && run == null && !raidChest)
		{
			completionTime = timeFromFirstHit(firstHit, tick);
		}

		if (!config.sendLoot())
		{
			return;
		}

		PlayerItems gear = bossKill ? tripGear() : null;
		LootEvent.Team team;
		if (raidChest)
		{
			team = raidSize != null ? new LootEvent.Team(raidSize) : null;
		}
		else
		{
			team = bossKill ? teamFor(kill != null ? kill.getName() : null, firstKnown(runStart, fightTimeStart, firstHit), tick) : null;
		}
		postEvent(new LootEvent(common(localPlayer),
			LootEvent.Source.of(event.getName(), kill, null, completionTime, wave, splits).withPoints(points),
			toDrops(event.getItems()), gear, team));
	}

	private void onNpcLoot(Player localPlayer, LootReceived event)
	{
		// Consumed even when loot isn't sent, so it can't be attached to a later kill
		int tick = client.getTickCount();
		LootEvent.KillTime killTime = consumeKillTime(null, KILL_TIME_MAX_AGE_TICKS);
		Integer fightTimeStart = fightStartTick(killTime);
		LootEvent.Kill kill = killCounts.claimForNpcLoot(tick);
		Integer firstHit = kill != null ? fightStarts.take(kill.getName(), tick) : null;
		// Bosses the game prints no time for (e.g. Corp) are timed from the first hit
		if (killTime == null)
		{
			killTime = timeFromFirstHit(firstHit, tick);
		}
		log.debug("NPC loot: {} (tick {}), kill count {}", event.getName(), tick, kill);
		if (!config.sendLoot())
		{
			return;
		}

		// Only kills with a kill count message are boss kills
		PlayerItems gear = kill != null ? tripGear() : null;
		LootEvent.Team team = kill != null ? teamFor(kill.getName(), firstKnown(fightTimeStart, firstHit), tick) : null;
		postEvent(new LootEvent(common(localPlayer),
			LootEvent.Source.of(event.getName(), kill, npcId(event.getMetadata()), killTime, null, null),
			toDrops(event.getItems()), gear, team));
	}

	/**
	 * How old a kill time can be and still belong to this event loot. Raid chests and the Colosseum and Doom
	 * chests are opened long after their completion time is printed. Other loot only takes a time printed with it,
	 * so a leftover time (e.g. an agility lap's) can't end up on a clue casket or a Barrows chest.
	 */
	static int chestKillTimeMaxAge(String lootName)
	{
		boolean raidChest = lootName != null && lootName.equals(RaidTeams.chestName(lootName));
		return raidChest || COLOSSEUM.equals(lootName) || DOOM_OF_MOKHAIOTL.equals(lootName)
			? CHEST_TIME_MAX_AGE_TICKS
			: KILL_TIME_MAX_AGE_TICKS;
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
			// Game ticks before and after aren't comparable, and the account can change
			tripGear.end();
			fightStarts.reset();
			nearbyPlayers.reset();
			scenePlayers.clear();
			colosseum.onLogout();
			doom.onLogout();
			fightCaves.onLogout();
			inferno.onLogout();
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

		int tick = client.getTickCount();
		recordNearbyPlayers(tick);
		if (tick % PRUNE_INTERVAL_TICKS == 0)
		{
			fightStarts.prune(tick);
			nearbyPlayers.prune(tick);
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
		// Consumed even when loot isn't sent, so they can't be attached to a later kill
		LootEvent.KillTime killTime = consumeKillTime(null, KILL_TIME_MAX_AGE_TICKS);
		Integer fightTimeStart = fightStartTick(killTime);
		Integer firstHit = fightStarts.take(kill.getName(), client.getTickCount());
		WaveRun.Run run = null;
		Integer wave = null;
		WaveRun waveRun = FIGHT_CAVES_KILL.equals(kill.getName()) ? fightCaves
			: INFERNO_KILL.equals(kill.getName()) ? inferno
			: null;
		if (waveRun != null)
		{
			// How far the run got, like the Colosseum's and Doom's: the last wave, 63 / 69 for a full run
			wave = waveRun.lastWave();
			run = waveRun.consume();
		}
		if (killTime == null && run == null)
		{
			killTime = timeFromFirstHit(firstHit, client.getTickCount());
		}

		Player localPlayer = client.getLocalPlayer();
		log.debug("Kill without loot: {} {}, duration {}", kill.getName(), kill.getCount(), killTime);
		if (localPlayer == null || !config.sendLoot())
		{
			return;
		}

		Integer runStart = run != null ? run.getStartTick() : null;
		PlayerItems gear = tripGear();
		LootEvent.Team team = teamFor(kill.getName(), firstKnown(runStart, fightTimeStart, firstHit), client.getTickCount());
		postEvent(new LootEvent(common(localPlayer),
			LootEvent.Source.of(null, kill, null, killTime, wave, run != null ? run.getSplits() : null),
			Collections.emptyList(), gear, team));
	}

	/**
	 * Whether the kill count name is a skilling boss's, whose crowd isn't a team.
	 */
	private static boolean isSkillingBoss(String killName)
	{
		return NO_TEAM.stream().anyMatch(name -> name.equalsIgnoreCase(killName));
	}

	/**
	 * The team for a boss kill other than a raid: the player plus every other player who was near them at some
	 * point between the fight's start and the kill, or in the last {@link FightStarts#STALE_TICKS} if the start
	 * isn't known. Null for skilling bosses, whose crowd isn't a team.
	 */
	private LootEvent.Team teamFor(String killName, Integer fightStartTick, int tick)
	{
		if (killName != null && isSkillingBoss(killName))
		{
			return null;
		}
		int from = fightStartTick != null ? fightStartTick : tick - FightStarts.STALE_TICKS;
		return new LootEvent.Team(1 + nearbyPlayers.count(from, tick));
	}

	/**
	 * The first tick that is known, or null.
	 */
	private static Integer firstKnown(Integer... ticks)
	{
		for (Integer tick : ticks)
		{
			if (tick != null)
			{
				return tick;
			}
		}
		return null;
	}

	/**
	 * The game's party size for a raid right now, from its party slots, or null if it has none or none are in use.
	 */
	private Integer raidPartySize(String chestName)
	{
		int[] slots = RAID_PARTY_SLOTS.get(chestName);
		if (slots == null)
		{
			return null;
		}
		int[] values = new int[slots.length];
		for (int i = 0; i < slots.length; i++)
		{
			values[i] = client.getVarbitValue(slots[i]);
		}
		int size = RaidTeams.occupiedSlots(values);
		return size > 0 ? size : null;
	}

	/**
	 * Keeps the other players in the scene, so the ones near the player can be found without scanning the scene.
	 */
	@Subscribe
	public void onPlayerSpawned(PlayerSpawned event)
	{
		if (event.getPlayer() != client.getLocalPlayer())
		{
			scenePlayers.add(event.getPlayer());
		}
	}

	@Subscribe
	public void onPlayerDespawned(PlayerDespawned event)
	{
		scenePlayers.remove(event.getPlayer());
	}

	/**
	 * Records the other players within {@link NearbyPlayers#RADIUS_TILES} of the player, while a fight is going on
	 * (a hit in the last {@link FightStarts#STALE_TICKS}), to count the team of a kill.
	 */
	private void recordNearbyPlayers(int tick)
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null || scenePlayers.isEmpty() || fightStarts.current(tick) == null)
		{
			return;
		}

		WorldPoint here = localPlayer.getWorldLocation();
		for (Player player : scenePlayers)
		{
			if (player.getWorldLocation().distanceTo(here) <= NearbyPlayers.RADIUS_TILES)
			{
				nearbyPlayers.onNear(player.getName(), tick);
			}
		}
	}

	/**
	 * The player's items at the start of the current trip, or null if there is none (e.g. the player didn't land
	 * a hit) or sending gear is turned off.
	 */
	private PlayerItems tripGear()
	{
		return config.sendGear() ? tripGear.get(client.getTickCount()) : null;
	}

	/**
	 * The tick the fight started, from a kill time just returned by {@link #consumeKillTime}: the tick its
	 * message came on, less the time. Null if there is no kill time.
	 */
	private Integer fightStartTick(LootEvent.KillTime killTime)
	{
		// lastKillTimeTick is left as it was when the kill time was consumed
		return killTime != null ? lastKillTimeTick - WaveRun.secondsToTicks(killTime.getDuration()) : null;
	}

	/**
	 * The plugin's own kill time, for a kill the game printed no time for: game ticks from the first hit until
	 * now, with no personal best. Null if the first hit isn't known.
	 */
	private static LootEvent.KillTime timeFromFirstHit(Integer firstHitTick, int tick)
	{
		return firstHitTick != null ? new LootEvent.KillTime(WaveRun.ticksToSeconds(tick - firstHitTick), null, false) : null;
	}

	/**
	 * Counts the player's own hits on NPCs, so kills the game prints no time for know when their fight started,
	 * and the first hit of a trip takes the gear snapshot.
	 */
	@Subscribe
	public void onHitsplatApplied(HitsplatApplied event)
	{
		if (!event.getHitsplat().isMine() || !(event.getActor() instanceof NPC))
		{
			return;
		}

		NPC npc = (NPC) event.getActor();
		int tick = client.getTickCount();
		fightStarts.onHit(FightStarts.key(npc.getId(), npc.getName()), tick);
		tripGear.onHit(tick, this::capturePlayerItems);
	}

	/**
	 * Opening the bank ends the trip: the next hit is the start of a new one, with a new gear setup.
	 */
	@Subscribe
	public void onItemContainerChanged(ItemContainerChanged event)
	{
		if (event.getContainerId() == InventoryID.BANK)
		{
			tripGear.end();
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
		int tick = client.getTickCount();

		// The Chambers of Xeric tells its party when the raid starts, when each level is completed, and when the
		// raid is completed
		if (event.getType() == ChatMessageType.FRIENDSCHATNOTIFICATION)
		{
			onRaidPartyMessage(Text.removeTags(event.getMessage()), tick);
			return;
		}

		if (event.getType() != ChatMessageType.GAMEMESSAGE && event.getType() != ChatMessageType.SPAM)
		{
			return;
		}

		Matcher matcher = COLOSSEUM_WAVE_PATTERN.matcher(event.getMessage());
		if (matcher.lookingAt())
		{
			int wave = Integer.parseInt(matcher.group("wave"));
			Double duration = optionalDuration(matcher);
			// Waves 1-11 show their own time, but wave 12 shows the whole run's (seen in game)
			if (wave == COLOSSEUM_FULL_RUN_WAVES && duration != null)
			{
				duration = colosseum.ownTime(wave, duration);
			}
			colosseum.onWaveCompleted(wave, duration, tick);
			return;
		}

		matcher = DOOM_LEVEL_PATTERN.matcher(event.getMessage());
		if (matcher.lookingAt())
		{
			String deep = matcher.group("deep");
			lastDoomLevel = Integer.parseInt(deep != null ? deep : matcher.group("level"));
			if (lastDoomLevel == 1)
			{
				doomFullRunDuration = null;
			}
			doom.onWaveCompleted(lastDoomLevel, optionalDuration(matcher), tick);
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

		matcher = TZHAAR_WAVE_PATTERN.matcher(message);
		if (matcher.matches())
		{
			WaveRun tzhaarRun = tzhaarRun();
			if (tzhaarRun != null)
			{
				tzhaarRun.onWaveStarted(Integer.parseInt(matcher.group("wave")), tick);
			}
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

		// Also checked before KILL_DURATION_PATTERN: a room's duration isn't the raid's time
		matcher = TOB_ROOM_COMPLETE_PATTERN.matcher(message);
		if (matcher.lookingAt())
		{
			String room = matcher.group("room");
			if (room.equals(TOB_FIRST_ROOM))
			{
				theatreOfBlood.onRunStarted();
			}
			theatreOfBlood.onBossCompleted(room, parseDuration(matcher.group("time")));
			LootEvent.KillTime completionTime = tobCompletionTime(event.getMessage());
			if (completionTime != null)
			{
				setKillTime(completionTime, tick, RaidTeams.THEATRE_OF_BLOOD);
			}
			return;
		}

		matcher = KILL_DURATION_PATTERN.matcher(event.getMessage());
		if (matcher.find())
		{
			setKillTime(parseKillTime(matcher), tick, killTimeRaid(message));
			return;
		}

		// Not returned: the Fractured Archive's completion message below is also a kill count message
		LootEvent.Kill killCount = killCounts.onMessage(event.getMessage(), tick);
		if (killCount != null)
		{
			log.debug("Kill count: {} {} (tick {})", killCount.getName(), killCount.getCount(), tick);
			// The last Fight Caves / Inferno wave ends with the kill
			if (FIGHT_CAVES_KILL.equals(killCount.getName()))
			{
				fightCaves.finish(tick);
			}
			else if (INFERNO_KILL.equals(killCount.getName()))
			{
				inferno.finish(tick);
			}

			// A raid's completion: its party is still in the slots, the chest is opened later
			String raidChest = RaidTeams.chestName(killCount.getName());
			raidTeams.onCompleted(raidChest, raidPartySize(raidChest));
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
	 * A message to the raid's party, tags removed: the Chambers of Xeric's start, a level's completion, or the
	 * raid's completion with its team size and time. The team size falls back to the party size the game keeps
	 * if the message shows none.
	 */
	private void onRaidPartyMessage(String message, int tick)
	{
		if (message.startsWith(ChambersOfXeric.RAID_START_MESSAGE))
		{
			chambersOfXeric.onRaidStarted();
			return;
		}

		Matcher matcher = ChambersOfXeric.LEVEL_COMPLETE_PATTERN.matcher(message);
		if (matcher.find())
		{
			chambersOfXeric.onLevelCompleted(matcher.group("level"), parseDuration(matcher.group("time")));
			return;
		}

		if (message.startsWith(RaidTeams.COX_COMPLETE_MESSAGE))
		{
			Integer size = RaidTeams.coxTeamSize(message);
			if (size == null)
			{
				size = client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSIZE);
			}
			log.debug("Chambers of Xeric completed, team size {}", size);
			raidTeams.onCompleted(RaidTeams.CHAMBERS_OF_XERIC, size);

			// The raid's time is the chest's, and ends the Great Olm's split
			LootEvent.KillTime raidTime = coxCompletionTime(message);
			if (raidTime != null)
			{
				setKillTime(raidTime, tick, RaidTeams.CHAMBERS_OF_XERIC);
			}
			// Read when the raid is completed, like RuneLite's raids plugin does
			chambersOfXeric.onRaidCompleted(raidTime != null ? raidTime.getDuration() : null,
				client.getVarbitValue(VarbitID.RAIDS_CLIENT_PARTYSCORE), client.getVarpValue(VarPlayerID.RAIDS_PLAYERSCORE));
		}
	}

	/**
	 * The raid's time and personal best in a Chambers of Xeric completion message, tags removed, or null if it
	 * shows none.
	 */
	static LootEvent.KillTime coxCompletionTime(String message)
	{
		Matcher matcher = ChambersOfXeric.COMPLETION_TIME_PATTERN.matcher(message);
		return matcher.find() ? parseKillTime(matcher) : null;
	}

	/**
	 * The time group of a wave or delve level message in seconds, or null if the message had none.
	 */
	private static Double optionalDuration(Matcher matcher)
	{
		String time = matcher.group("time");
		return time != null ? parseDuration(time) : null;
	}

	/**
	 * The Fight Caves or Inferno run the player is in, by map region, or null if in neither.
	 */
	private WaveRun tzhaarRun()
	{
		Player localPlayer = client.getLocalPlayer();
		if (localPlayer == null)
		{
			return null;
		}

		int region = WorldPoint.fromLocalInstance(client, localPlayer.getLocalLocation()).getRegionID();
		if (region == FIGHT_CAVES_REGION)
		{
			return fightCaves;
		}
		if (region == INFERNO_REGION)
		{
			return inferno;
		}
		return null;
	}

	/**
	 * The player's equipment, inventory, rune pouch and quiver right now.
	 */
	private PlayerItems capturePlayerItems()
	{
		Map<String, PlayerItems.SlotItem> equipment = new LinkedHashMap<>();
		ItemContainer worn = client.getItemContainer(InventoryID.WORN);
		if (worn != null)
		{
			for (EquipmentInventorySlot slot : EquipmentInventorySlot.values())
			{
				Item item = worn.getItem(slot.getSlotIdx());
				if (item != null && item.getId() > 0)
				{
					equipment.put(slot.name(), new PlayerItems.SlotItem(item.getId(), item.getQuantity()));
				}
			}
		}

		Map<String, PlayerItems.SlotItem> inventory = new LinkedHashMap<>();
		ItemContainer inv = client.getItemContainer(InventoryID.INV);
		if (inv != null)
		{
			Item[] items = inv.getItems();
			for (int slot = 0; slot < items.length; slot++)
			{
				if (items[slot].getId() > 0)
				{
					inventory.put(String.valueOf(slot), new PlayerItems.SlotItem(items[slot].getId(), items[slot].getQuantity()));
				}
			}
		}

		return new PlayerItems(equipment, inventory, captureRunePouch(), captureQuiver());
	}

	/**
	 * Raw contents of the extra ammo slot (Dizana's quiver / bolt pouch). The game retains this
	 * even when no quiver is worn, so the API decides whether it applies based on the equipment.
	 */
	private PlayerItems.SlotItem captureQuiver()
	{
		int ammoId = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO);
		int ammoQuantity = client.getVarpValue(VarPlayerID.DIZANAS_QUIVER_TEMP_AMMO_AMOUNT);
		if (ammoId <= 0 || ammoQuantity <= 0)
		{
			return null;
		}
		return new PlayerItems.SlotItem(ammoId, ammoQuantity);
	}

	/**
	 * Raw contents of the rune pouch. The game retains this even when no pouch is carried,
	 * so the API decides whether it applies based on the inventory. Null if the pouch is empty.
	 */
	private List<PlayerItems.SlotItem> captureRunePouch()
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
	 * Keeps a kill or completion time for the loot that follows. A raid's completion time is kept for the raid's
	 * chest (raidChest), so other loot can't take it.
	 */
	private void setKillTime(LootEvent.KillTime killTime, int tick, String raidChest)
	{
		lastKillTime = killTime;
		lastKillTimeTick = tick;
		lastKillTimeRaid = raidChest;
	}

	/**
	 * The raid chest a kill time message, tags removed, is the completion time of, or null if it is not a raid's:
	 * e.g. "Theatre of Blood" for "Theatre of Blood completion time: ...", or "Fractured Archive" for "The
	 * Fractured Archive: Total completion time: ...".
	 */
	static String killTimeRaid(String message)
	{
		String raid = RaidTeams.chestName(message);
		if (raid == null && message.startsWith("The " + FracturedArchive.LOOT_SOURCE))
		{
			raid = FracturedArchive.LOOT_SOURCE;
		}
		return raid;
	}

	/**
	 * Returns the last reported duration if it is recent enough, and consumes it so it can't be attached to a
	 * later, unrelated event. A raid's completion time is only for its own chest (lootName): other loot gets
	 * none and leaves it, so e.g. the Patient Record Verzik drops to one player can't take the Theatre of Blood's
	 * time from its chest.
	 */
	private LootEvent.KillTime consumeKillTime(String lootName, int maxAgeTicks)
	{
		if (lastKillTimeRaid != null && !lastKillTimeRaid.equals(lootName))
		{
			return null;
		}
		LootEvent.KillTime killTime = null;
		if (lastKillTime != null && client.getTickCount() - lastKillTimeTick <= maxAgeTicks)
		{
			killTime = lastKillTime;
		}
		lastKillTime = null;
		lastKillTimeRaid = null;
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
	 * The raid's completion time in a Theatre of Blood room message, or null if it has none. Verzik's room message
	 * ends with it, after the room's own duration (seen in game): "Wave 'The Final Challenge' (Normal Mode)
	 * complete!&lt;br&gt;Duration: &lt;col=ff0000&gt;5:18.00&lt;/col&gt;&lt;br&gt;Theatre of Blood completion time:
	 * &lt;col=ff0000&gt;18:23.40&lt;/col&gt;. Personal best: 16:00.60". The room's duration isn't the raid's time, so
	 * only the part from "completion time:" on is read.
	 */
	static LootEvent.KillTime tobCompletionTime(String message)
	{
		int start = message.toLowerCase().indexOf(TOB_COMPLETION_TIME);
		if (start < 0)
		{
			return null;
		}
		Matcher matcher = KILL_DURATION_PATTERN.matcher(message);
		matcher.region(start, message.length());
		// So the pattern can still see a "total " in front, which marks the time it ignores
		matcher.useTransparentBounds(true);
		return matcher.lookingAt() ? parseKillTime(matcher) : null;
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
