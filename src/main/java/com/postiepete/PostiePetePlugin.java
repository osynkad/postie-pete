package com.postiepete;

import com.google.inject.Provides;
import lombok.extern.slf4j.Slf4j;
import net.runelite.client.config.ConfigManager;
import net.runelite.client.plugins.Plugin;
import net.runelite.client.plugins.PluginDescriptor;

@Slf4j
@PluginDescriptor(
	name = "Postie Pete",
	description = "Collects in-game events like drops and kills for tracking over time."
)
public class PostiePetePlugin extends Plugin
{
	@Override
	protected void startUp() throws Exception
	{
		log.debug("Postie Pete started!");
	}

	@Override
	protected void shutDown() throws Exception
	{
		log.debug("Postie Pete stopped!");
	}

	@Provides
	PostiePeteConfig provideConfig(ConfigManager configManager)
	{
		return configManager.getConfig(PostiePeteConfig.class);
	}
}
