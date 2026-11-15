package com.postiepete;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import javax.inject.Inject;
import lombok.extern.slf4j.Slf4j;
import net.runelite.api.ChatMessageType;
import net.runelite.api.Client;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Reads the whole collection log when the player opens it, so slots obtained before the plugin
 * was installed can be backfilled.
 *
 * <p>Opening the log's search makes the game run a script once for every obtained item, with the
 * item ID and count as arguments. On the tick after the log opens, the search is toggled on and
 * straight off again, and the script calls are collected until there are as many as the game's
 * own obtained counter says. This is the one use of {@code Client.menuAction} RuneLite accepts
 * (see the "Rejected or Rolled-Back Features" wiki page).
 *
 * <p>Based on the Valiance clan plugin by Sicryption (github.com/Sicryption/ValianceClanPlugin,
 * BSD-2-Clause).
 */
@Slf4j
class CollectionLogReader
{
	/**
	 * Runs once per obtained item while the search is drawn. Arguments: [script, item ID, count, ...].
	 */
	private static final int SCRIPT_DRAW_SEARCH_ITEM = 4100;

	/**
	 * Closes the collection log search again.
	 */
	private static final int SCRIPT_CLOSE_SEARCH = 2240;

	/**
	 * Items the collection log lists under two IDs that count as one slot: original -> duplicate.
	 */
	private static final Map<Integer, Integer> DUPLICATE_ITEMS = Map.of(
		12013, 29472, // Prospector helmet
		12014, 29474, // Prospector jacket
		12015, 29476, // Prospector legs
		12016, 29478  // Prospector boots
	);

	/**
	 * Give up if the items haven't all arrived after this many ticks.
	 */
	private static final int MAX_WAIT_TICKS = 10;

	private final Client client;
	private final PostiePeteConfig config;

	private BiConsumer<List<CollectionLogSnapshotEvent.Item>, Integer> onRead;
	private boolean open;
	private int searchTick = -1;
	private Map<Integer, Integer> items = new HashMap<>();
	private Map<Integer, Integer> lastSent;

	@Inject
	CollectionLogReader(Client client, PostiePeteConfig config)
	{
		this.client = client;
		this.config = config;
	}

	/**
	 * Called on the client thread with the items and obtained slot count once the whole log has been read.
	 */
	void setOnRead(BiConsumer<List<CollectionLogSnapshotEvent.Item>, Integer> onRead)
	{
		this.onRead = onRead;
	}

	void reset()
	{
		open = false;
		searchTick = -1;
		items = new HashMap<>();
		lastSent = null;
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			open = true;
			searchTick = -1;
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			open = false;
			searchTick = -1;
		}
	}

	@Subscribe
	public void onScriptPreFired(ScriptPreFired event)
	{
		if (searchTick == -1 || event.getScriptId() != SCRIPT_DRAW_SEARCH_ITEM || event.getScriptEvent() == null)
		{
			return;
		}

		Object[] args = event.getScriptEvent().getArguments();
		items.put((int) args[1], (int) args[2]);
	}

	@Subscribe
	public void onGameTick(GameTick event)
	{
		if (!open)
		{
			return;
		}

		// Leave the interface alone unless the snapshot will actually be sent.
		String endpointUrl = config.endpointUrl();
		if (!config.sendCollectionLogSnapshot() || endpointUrl == null || endpointUrl.trim().isEmpty())
		{
			open = false;
			return;
		}

		if (searchTick == -1)
		{
			// The interface has finished loading: toggle the search on and off to list every item.
			items = new HashMap<>();
			searchTick = client.getTickCount();
			client.menuAction(-1, InterfaceID.Collection.SEARCH_TOGGLE, MenuAction.CC_OP, 1, -1, "Search", null);
			client.runScript(SCRIPT_CLOSE_SEARCH);
			return;
		}

		int expected = client.getVarpValue(VarPlayerID.COLLECTION_COUNT);
		int slots = countSlots(items);
		if (slots < expected)
		{
			if (client.getTickCount() - searchTick > MAX_WAIT_TICKS)
			{
				log.debug("Collection log read gave up with {} of {} slots", slots, expected);
				chat("Postie Pete couldn't read your whole collection log. Close it and open it again to retry.");
				open = false;
			}
			return;
		}

		// Read once per opening. Sending again in the same session is pointless if nothing changed.
		open = false;
		if (items.equals(lastSent))
		{
			return;
		}
		lastSent = items;

		List<CollectionLogSnapshotEvent.Item> list = new ArrayList<>(items.size());
		items.forEach((id, quantity) -> list.add(new CollectionLogSnapshotEvent.Item(id, quantity)));
		if (onRead != null)
		{
			onRead.accept(list, slots);
		}
	}

	/**
	 * Obtained slots as the game counts them: an item listed under two IDs counts once.
	 */
	static int countSlots(Map<Integer, Integer> items)
	{
		int count = items.size();
		for (Map.Entry<Integer, Integer> pair : DUPLICATE_ITEMS.entrySet())
		{
			if (items.containsKey(pair.getKey()) && items.containsKey(pair.getValue()))
			{
				count--;
			}
		}
		return count;
	}

	private void chat(String message)
	{
		client.addChatMessage(ChatMessageType.GAMEMESSAGE, "", message, null);
	}
}
