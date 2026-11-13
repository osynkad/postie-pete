package com.postiepete;

import net.runelite.client.RuneLite;
import net.runelite.client.externalplugins.ExternalPluginManager;

public class PostiePetePluginTest
{
	public static void main(String[] args) throws Exception
	{
		ExternalPluginManager.loadBuiltin(PostiePetePlugin.class);
		RuneLite.main(args);
	}
}
