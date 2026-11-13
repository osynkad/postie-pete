package com.postiepete;

import com.google.gson.annotations.SerializedName;
import java.util.List;
import java.util.Map;
import lombok.Value;

@Value
class PlayerItems
{
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
}
