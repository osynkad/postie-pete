package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import java.util.LinkedHashMap;
import java.util.Map;
import org.junit.Test;

public class PlayerItemsTest
{
	@Test
	public void reusesAnUnchangedItem()
	{
		PlayerItems.SlotItem bow = new PlayerItems.SlotItem(20997, 1);

		assertSame(bow, PlayerItems.reuse(bow, 20997, 1));
	}

	@Test
	public void makesANewItemWhenItChanged()
	{
		PlayerItems.SlotItem arrows = new PlayerItems.SlotItem(11212, 412);

		PlayerItems.SlotItem fired = PlayerItems.reuse(arrows, 11212, 411);
		assertNotSame(arrows, fired);
		assertEquals(new PlayerItems.SlotItem(11212, 411), fired);
		assertEquals(new PlayerItems.SlotItem(560, 10), PlayerItems.reuse(null, 560, 10));
	}

	@Test
	public void sharesAnEqualPart()
	{
		Map<String, PlayerItems.SlotItem> before = inventory(6685);

		assertSame(before, PlayerItems.sameIfEqual(inventory(6685), before));
	}

	@Test
	public void keepsAChangedPart()
	{
		Map<String, PlayerItems.SlotItem> before = inventory(6685);
		Map<String, PlayerItems.SlotItem> after = inventory(3024);

		assertSame(after, PlayerItems.sameIfEqual(after, before));
		assertSame(after, PlayerItems.sameIfEqual(after, null));
	}

	@Test
	public void inventoryKeysAreShared()
	{
		assertEquals("0", PlayerItems.inventoryKey(0));
		assertEquals("27", PlayerItems.inventoryKey(27));
		assertSame(PlayerItems.inventoryKey(5), PlayerItems.inventoryKey(5));
	}

	private static Map<String, PlayerItems.SlotItem> inventory(int firstItemId)
	{
		Map<String, PlayerItems.SlotItem> inventory = new LinkedHashMap<>();
		inventory.put("0", new PlayerItems.SlotItem(firstItemId, 1));
		inventory.put("1", new PlayerItems.SlotItem(12791, 1));
		return inventory;
	}
}
