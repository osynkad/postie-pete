/*
 * Parts of this file are based on the Valiance clan plugin
 * (https://github.com/Sicryption/ValianceClanPlugin), distributed under the
 * following license:
 *
 * BSD 2-Clause License
 *
 * Copyright (c) 2025, Encryptiron
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions are met:
 *
 * 1. Redistributions of source code must retain the above copyright notice, this
 *    list of conditions and the following disclaimer.
 *
 * 2. Redistributions in binary form must reproduce the above copyright notice,
 *    this list of conditions and the following disclaimer in the documentation
 *    and/or other materials provided with the distribution.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS "AS IS"
 * AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT NOT LIMITED TO, THE
 * IMPLIED WARRANTIES OF MERCHANTABILITY AND FITNESS FOR A PARTICULAR PURPOSE ARE
 * DISCLAIMED. IN NO EVENT SHALL THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE
 * FOR ANY DIRECT, INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL
 * DAMAGES (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION) HOWEVER
 * CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT, STRICT LIABILITY,
 * OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE) ARISING IN ANY WAY OUT OF THE USE
 * OF THIS SOFTWARE, EVEN IF ADVISED OF THE POSSIBILITY OF SUCH DAMAGE.
 */
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
import net.runelite.api.GameState;
import net.runelite.api.MenuAction;
import net.runelite.api.events.GameStateChanged;
import net.runelite.api.events.GameTick;
import net.runelite.api.events.ScriptPreFired;
import net.runelite.api.events.WidgetClosed;
import net.runelite.api.events.WidgetLoaded;
import net.runelite.api.gameval.InterfaceID;
import net.runelite.api.gameval.VarPlayerID;
import net.runelite.client.eventbus.Subscribe;

/**
 * Based on the Valiance clan plugin (github.com/Sicryption/ValianceClanPlugin), copyright
 * Encryptiron, BSD-2-Clause: see the license at the top of this file.
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

	/**
	 * The collection log is on screen. A load while it already is (e.g. caused by the search toggle) isn't a new
	 * opening, so it doesn't start another read.
	 */
	private boolean visible;

	/**
	 * A read was started by opening the log and hasn't finished or given up yet.
	 */
	private boolean reading;
	private int searchTick = -1;
	private Map<Integer, Integer> items = new HashMap<>();

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
		visible = false;
		reading = false;
		searchTick = -1;
		items = new HashMap<>();
	}

	@Subscribe
	public void onWidgetLoaded(WidgetLoaded event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION && !visible)
		{
			visible = true;
			reading = true;
			searchTick = -1;
		}
	}

	@Subscribe
	public void onGameStateChanged(GameStateChanged event)
	{
		// Interfaces are gone after logging out or hopping, even if no close event came for the log
		if (event.getGameState() == GameState.LOGIN_SCREEN || event.getGameState() == GameState.HOPPING)
		{
			reset();
		}
	}

	@Subscribe
	public void onWidgetClosed(WidgetClosed event)
	{
		if (event.getGroupId() == InterfaceID.COLLECTION)
		{
			visible = false;
			reading = false;
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
		if (!reading)
		{
			return;
		}

		// Leave the interface alone unless the snapshot will actually be sent.
		if (PostiePetePlugin.endpointUrls(config.endpointUrl()).isEmpty())
		{
			reading = false;
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
				reading = false;
			}
			return;
		}

		// Sent on every opening, changed or not, so a request that failed is simply retried by opening the log again.
		reading = false;
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
