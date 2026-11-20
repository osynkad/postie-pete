package com.postiepete;

import static org.junit.Assert.assertEquals;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;
import okhttp3.HttpUrl;
import org.junit.Test;

public class EndpointUrlsTest
{
	@Test
	public void emptySettingHasNoUrls()
	{
		assertEquals(Collections.emptyList(), urls(null));
		assertEquals(Collections.emptyList(), urls(""));
		assertEquals(Collections.emptyList(), urls(" , ,"));
	}

	@Test
	public void singleUrl()
	{
		assertEquals(Collections.singletonList("http://localhost:3000/events"), urls(" http://localhost:3000/events "));
	}

	@Test
	public void commaSeparatedUrlsInOrder()
	{
		assertEquals(Arrays.asList("http://localhost:3000/events", "https://example.com/osrs"),
			urls("http://localhost:3000/events, https://example.com/osrs,"));
	}

	@Test
	public void skipsInvalidAndDuplicateUrls()
	{
		assertEquals(Arrays.asList("https://example.com/a", "https://example.com/b"),
			urls("https://example.com/a,not a url,https://example.com/b, https://example.com/a"));
	}

	private static List<String> urls(String setting)
	{
		return PostiePetePlugin.endpointUrls(setting).stream().map(HttpUrl::toString).collect(Collectors.toList());
	}
}
