package com.postiepete;

import net.runelite.client.config.Config;
import net.runelite.client.config.ConfigGroup;
import net.runelite.client.config.ConfigItem;

@ConfigGroup("postiepete")
public interface PostiePeteConfig extends Config
{
	@ConfigItem(
		keyName = "apiUrl",
		name = "API URL",
		description = "URL to the API that should receive the events",
		warning = "This feature submits your IP address to a 3rd-party server not controlled or verified by RuneLite developers"
	)
	default String apiUrl()
	{
		return "";
	}
}
