package com.postiepete;

import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Set;
import net.runelite.api.coords.WorldPoint;
import net.runelite.api.gameval.ObjectID;

/**
 * Finds when each Chambers of Xeric room is completed. The game doesn't say, so like the CoX Timers hub plugin, each
 * room's blocking obstacle (the crystal or barrier that opens when the room is done) is followed until it stops
 * blocking its tile.
 * <p>
 * The room layout and obstacles come from dey0's CoX Timers plugin (BSD 2-Clause License, Copyright (c) 2021,
 * dey0).
 */
class CoxRooms
{
	// A raid's map is built from template chunks: pp_xxxxxxxxxx_yyyyyyyyyyy_rr0 (plane, chunk x, chunk y, rotation).
	// Each room takes 4x4 chunks, so the low two bits of x and y, and the rotation, don't matter.
	private static final int ROOM_MASK = 0b11_1111111100_11111111100_00_0;

	private static final Map<Integer, String> ROOMS = new HashMap<>();

	static
	{
		// The room's plane and chunk y in the template, from the upper floor's rooms to the lower's. Each room comes
		// in three versions, at chunk x 102 to 104: the path turning one way, going through, or turning the other.
		room(0, 164, "Shamans");
		room(0, 165, "Vasa");
		room(0, 166, "Vanguards");
		room(0, 167, "Ice demon");
		room(0, 168, "Thieving");
		room(1, 164, "Mystics");
		room(1, 165, "Tekton");
		room(1, 166, "Muttadiles");
		room(1, 167, "Tightrope");
		room(2, 164, "Guardians");
		room(2, 165, "Vespula");
		room(2, 167, "Crabs");
	}

	/**
	 * The objects that block a room until it is completed.
	 */
	private static final Set<Integer> OBSTACLES = Collections.unmodifiableSet(new HashSet<>(Arrays.asList(
		ObjectID.INVISIBLE_TYPE8_BLOCKING, // Shamans, Thieving, Guardians
		ObjectID.RAIDS_SKELETALMYSTICS_SYMBOL,
		ObjectID.RAIDS_TIGHTROPE_BARRIER,
		ObjectID.RAIDS_LASERCRABS_BIGCRYSTAL_1,
		ObjectID.RAIDS_LASERCRABS_BIGCRYSTAL_2,
		ObjectID.RAIDS_LASERCRABS_BIGCRYSTAL_3,
		ObjectID.RAIDS_LASERCRABS_BIGCRYSTAL_4,
		ObjectID.RAIDS_LASERCRABS_BIGCRYSTAL_5,
		ObjectID.RAIDS_ICEDEMON_SNOW,
		ObjectID.RAIDS_BLOCKAGE_PURPLE_SMALL, // Vasa
		ObjectID.RAIDS_BLOCKAGE_ORANGE, // Tekton, Vanguards
		ObjectID.RAIDS_BLOCKAGE_GREEN, // Muttadiles
		ObjectID.RAIDS_VESPULA_BOIL_BLOCKING)));

	// Each room's obstacle that is still blocking, by room name
	private final Map<String, WorldPoint> obstacles = new LinkedHashMap<>();

	private static void room(int plane, int chunkY, String name)
	{
		for (int chunkX = 102; chunkX <= 104; chunkX++)
		{
			ROOMS.put(plane << 24 | chunkX << 16 | chunkY << 5, name);
		}
	}

	/**
	 * The room a raid map template chunk is part of, or null if it isn't a room with an obstacle.
	 */
	static String roomName(int templateChunk)
	{
		return ROOMS.get(templateChunk & ROOM_MASK);
	}

	static boolean isObstacle(int objectId)
	{
		return OBSTACLES.contains(objectId);
	}

	/**
	 * Called when a room's obstacle spawns, with where it is.
	 */
	void onObstacleSpawned(String room, WorldPoint location)
	{
		obstacles.put(room, location);
	}

	/**
	 * The rooms whose obstacle is still blocking, and where it is. The caller removes a room once it is completed
	 * or its obstacle is out of view.
	 */
	Map<String, WorldPoint> obstacles()
	{
		return obstacles;
	}

	void reset()
	{
		obstacles.clear();
	}
}
