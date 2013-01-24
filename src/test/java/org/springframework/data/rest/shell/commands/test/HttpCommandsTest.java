/*
 * VMware
 * Copyright, VMware, Inc.
 */
package org.springframework.data.rest.shell.commands.test;

import static org.hamcrest.Matchers.not;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.argThat;
import static org.mockito.Matchers.eq;
import static org.mockito.Mockito.when;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Resource;

import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.springframework.data.rest.shell.commands.HttpCommands;
import org.springframework.data.rest.shell.commands.PathOrRel;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageConverter;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;

/**
 * Test to validate HttpCommand functionality
 * 
 * @author <a href="mailto:cdelashmutt@vmware.com">cdelashmutt</a>
 * @version 1.0
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration
public class HttpCommandsTest
{
	@Resource
	private HttpCommands command;

	@Resource
	private RestTemplate restTemplate;

	@Resource(name = "messageConverters")
	private List<HttpMessageConverter<?>> converters;

	@Test
	public void getShouldNotDoubleEncode()
		throws Exception
	{
		String testMessage = "{message:\"Don't double encode!\"}";
		testQueryStringConvertion("Request URI should not be double encoded for GET", "test", testMessage, "test="+testMessage);
	}

	@Test
	public void getWithMultiValueParameter()
		throws Exception
	{
		List<String> paramValue = Arrays.asList("value1", "value2");		
		testQueryStringConvertion("Multivalue paramters should be handled properly", "test", paramValue, "test=value1&test=value2" );
	}

	@Test
	public void getWithSingleValueParameter()
		throws Exception
	{
		testQueryStringConvertion("Multivalue paramters should be handled properly", "test", "value", "test=value" );
	}

	/**
	 * Utility to test if a parameter generates the proper expected query string to a URI.
	 *
	 * @param assertionMessage The message to include with the assertion test if it fails.
	 * @param paramValue The value of the parameter to test conversion of.
	 * @param expectedQueryString The query string that is expected to be the result of converting paramValue
	 * 
	 * @throws URISyntaxException
	 */
	private void testQueryStringConvertion(String assertionMessage, String paramName, Object paramValue, String expectedQueryString)
		throws URISyntaxException
	{
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");

		PathOrRel path = new PathOrRel("http://foo/test");
		@SuppressWarnings("rawtypes")
		Map params = new HashMap();
		
		params.put(paramName, paramValue);

		URI queryURI = new URI("http", null, "foo", -1, "/test", expectedQueryString, null);
		when(restTemplate.getMessageConverters()).thenReturn(converters);
		when(
				restTemplate.execute(eq(queryURI), any(HttpMethod.class),
						any(RequestCallback.class),
						any(ResponseExtractor.class))).thenReturn(
				new ResponseEntity<String>("{test:\"Pass\"}", headers,
						HttpStatus.OK));
		when(
				restTemplate.execute(argThat(not(queryURI)),
						any(HttpMethod.class), any(RequestCallback.class),
						any(ResponseExtractor.class))).thenReturn(
				new ResponseEntity<String>("{test:\"Fail\"}", headers,
						HttpStatus.OK));

		String response = command.get(path, false, params, null);
		Assert.assertTrue(assertionMessage,
				response.contains("{test:\"Pass\"}"));

	}
}
