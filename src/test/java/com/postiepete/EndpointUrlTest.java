package com.postiepete;

import static org.junit.Assert.assertEquals;
import org.junit.Test;

public class EndpointUrlTest
{
	@Test
	public void appendsEventsToBaseUrl()
	{
		assertEquals("http://localhost:3000/events", PostiePetePlugin.toEndpointUrl("http://localhost:3000"));
	}

	@Test
	public void trailingSlashDoesNotDoubleUp()
	{
		assertEquals("http://localhost:3000/events", PostiePetePlugin.toEndpointUrl("http://localhost:3000/"));
	}

	@Test
	public void keepsExistingPath()
	{
		assertEquals("https://example.com/api/events", PostiePetePlugin.toEndpointUrl("https://example.com/api"));
	}

	@Test
	public void trimsWhitespace()
	{
		assertEquals("http://localhost:3000/events", PostiePetePlugin.toEndpointUrl("  http://localhost:3000 "));
	}

	@Test
	public void leavesInvalidUrlUnchanged()
	{
		assertEquals("localhost:3000", PostiePetePlugin.toEndpointUrl(" localhost:3000 "));
	}
}
