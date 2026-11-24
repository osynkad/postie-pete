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

	@Test
	public void syncUrlsReplaceTheLastPathSegment()
	{
		assertEquals(Arrays.asList("http://localhost:3000/sync/cas", "https://example.com/osrs/sync/cas"),
			syncUrls("http://localhost:3000/events, https://example.com/osrs/events", "sync/cas"));
		assertEquals(Collections.singletonList("https://example.com/sync/clogs"),
			syncUrls("https://example.com", "sync/clogs"));
	}

	@Test
	public void syncUrlsKeepTheQueryString()
	{
		assertEquals(Collections.singletonList("https://example.com/sync/cas?key=abc"),
			syncUrls("https://example.com/events?key=abc", "sync/cas"));
	}

	@Test
	public void syncUrlsSkipDuplicates()
	{
		assertEquals(Collections.singletonList("https://example.com/sync/cas"),
			syncUrls("https://example.com/events, https://example.com/other", "sync/cas"));
		assertEquals(Collections.emptyList(), syncUrls("", "sync/cas"));
	}

	private static List<String> urls(String setting)
	{
		return PostiePetePlugin.endpointUrls(setting).stream().map(HttpUrl::toString).collect(Collectors.toList());
	}

	private static List<String> syncUrls(String setting, String path)
	{
		return PostiePetePlugin.syncUrls(setting, path).stream().map(HttpUrl::toString).collect(Collectors.toList());
	}
}
