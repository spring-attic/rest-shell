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

	@SuppressWarnings("unchecked")
	@Test
	public void getShouldNotDoubleEncode()
		throws Exception
	{
		HttpHeaders headers = new HttpHeaders();
		headers.add("Content-Type", "application/json");

		PathOrRel path = new PathOrRel("http://foo/test");
		Map<String, String> params = new HashMap<String, String>();
		String testMessage = "{message:\"Don't double encode!\"}";
		params.put("test", testMessage);

		URI queryURI = new URI("http", null, "foo", -1, "/test", "test="
				+ testMessage, null);
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

		Assert.assertTrue("Request URI should not be double encoded for GET",
				response.contains("{test:\"Pass\"}"));
	}
}
