package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import net.runelite.client.plugins.loottracker.LootTrackerPlugin;
import org.junit.Test;

public class LootTrackerCheckTest
{
	@Test
	public void keyIsTheLootTrackerClassNameInLowerCase()
	{
		// How RuneLite's PluginManager names the on/off setting of a plugin without a configName
		assertEquals(LootTrackerPlugin.class.getSimpleName().toLowerCase(), PostiePetePlugin.LOOT_TRACKER_KEY);
	}

	@Test
	public void noValueMeansOn()
	{
		assertTrue(PostiePetePlugin.isPluginOn(null));
	}

	@Test
	public void readsTheStoredValue()
	{
		assertTrue(PostiePetePlugin.isPluginOn("true"));
		assertFalse(PostiePetePlugin.isPluginOn("false"));
	}
}
