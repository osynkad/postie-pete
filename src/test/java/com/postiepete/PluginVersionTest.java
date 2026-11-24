package com.postiepete;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import com.google.gson.Gson;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;
import org.junit.Test;

public class PluginVersionTest
{
	private static final Gson GSON = new Gson();

	@Test
	public void versionMatchesPluginProperties() throws IOException
	{
		Properties properties = new Properties();
		// Gradle runs tests from the project directory
		try (InputStream in = new FileInputStream("runelite-plugin.properties"))
		{
			properties.load(in);
		}
		assertEquals(properties.getProperty("version"), PostiePetePlugin.VERSION);
	}

	@Test
	public void outdatedResponseGivesMinVersion()
	{
		assertEquals("0.3.0", min("{\"error\":\"outdated_plugin\",\"minPluginVersion\":\"0.3.0\"}"));
		assertEquals("?", min("{\"error\":\"outdated_plugin\"}"));
	}

	@Test
	public void otherResponsesAreNotOutdated()
	{
		assertNull(min("{\"error\":\"Event not found\"}"));
		assertNull(min("{\"id\":\"8e2c\",\"receivedAt\":\"2026-11-24T12:00:00Z\",\"data\":{}}"));
		assertNull(min(""));
		assertNull(min("not json"));
		assertNull(min("[1,2]"));
		assertNull(min("{\"error\":"));
	}

	private static String min(String body)
	{
		return PostiePetePlugin.outdatedPluginMinVersion(GSON, body);
	}
}
