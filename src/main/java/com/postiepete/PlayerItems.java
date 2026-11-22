package com.postiepete;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.Value;
import lombok.With;

/**
 * The player's equipment, inventory, rune pouch and quiver at one moment.
 */
@Value
class PlayerItems
{
	/**
	 * How the moment was picked: see {@link CapturedAt}. Null while the snapshot is only kept in the history.
	 */
	@With
	CapturedAt capturedAt;

	/**
	 * Keyed by equipment slot name (e.g. "WEAPON", "AMMO"). Empty slots are omitted.
	 */
	Map<String, SlotItem> equipment;

	/**
	 * Keyed by inventory slot index ("0" - "27"). Empty slots are omitted.
	 */
	Map<String, SlotItem> inventory;

	/**
	 * Runes in the rune pouch, or null if empty. Empty pouch slots are omitted.
	 * Sent unconditionally: the game keeps this value even when no pouch is carried,
	 * so the receiver should check the inventory to decide whether it is in use.
	 */
	@SerializedName("rune_pouch")
	List<SlotItem> runePouch;

	/**
	 * Contents of the extra ammo slot (Dizana's quiver / bolt pouch), or null if empty.
	 * Sent unconditionally: the game keeps this value even when no quiver is worn,
	 * so the receiver should check the equipped cape to decide whether it is in use.
	 */
	SlotItem quiver;

	@Value
	static class SlotItem
	{
		int id;
		int quantity;
	}

	/**
	 * Where the start of a kill came from, and so which moment the items are from. Tried in this order.
	 */
	enum CapturedAt
	{
		/**
		 * The start of a run: Colosseum wave 1, Doom delve level 1, Fight Caves / Inferno wave 1, or the
		 * Fractured Archive's first boss.
		 */
		@SerializedName("runStart")
		RUN_START,

		/**
		 * The kill's tick minus the kill time the game printed.
		 */
		@SerializedName("fightTime")
		FIGHT_TIME,

		/**
		 * The player's first landed hit on the boss, for kills the game prints no time for.
		 */
		@SerializedName("firstHit")
		FIRST_HIT,

		/**
		 * When the kill or loot was reported, because no start was known.
		 */
		@SerializedName("kill")
		KILL
	}
}
