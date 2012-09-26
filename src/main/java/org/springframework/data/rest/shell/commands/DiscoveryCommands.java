package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.Arrays;
import java.util.HashMap;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.rest.shell.resources.PagableResources;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.Resources;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Commands that discover resources and create local helpers for defined links.
 *
 * @author Jon Brisbin
 */
@Component
public class DiscoveryCommands implements CommandMarker, ApplicationEventPublisherAware {

  private static final MediaType COMPACT_JSON = MediaType.valueOf("application/x-spring-data-compact+json");
  private static final Logger    LOG          = LoggerFactory.getLogger(DiscoveryCommands.class);

  @Autowired
  private ConfigurationCommands     configCmds;
  private ApplicationEventPublisher ctx;
  @Autowired(required = false)
  private RestTemplate        client    = new RestTemplate();
  private Map<String, String> resources = new HashMap<>();

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.ctx = applicationEventPublisher;
  }

  /**
   * Get the discovered resources.
   *
   * @return
   */
  public Map<String, String> getResources() {
    return resources;
  }

  @CliAvailabilityIndicator({"discover", "list", "follow"})
  public boolean isDiscoverAvailable() {
    return true;
  }

  /**
   * Issue a GET and discover what resources are available by looking in the links property of the JSON.
   *
   * @param path
   *     URI to resource.
   *
   * @return
   *
   * @throws IOException
   */
  @CliCommand(value = "discover", help = "Discover the resources available at a given URI.")
  public String discover(
      @CliOption(key = "",
                 mandatory = false,
                 help = "The base URI to use for this session.",
                 unspecifiedDefaultValue = "/") String path) throws IOException, URISyntaxException {
    URI requestUri;
    if("/".equals(path)) {
      requestUri = configCmds.getBaseUri();
    } else if(path.startsWith("http")) {
      requestUri = URI.create(path);
    } else {
      requestUri = UriComponentsBuilder.fromUri(configCmds.getBaseUri()).path(path).build().toUri();
    }

    configCmds.setBaseUri(requestUri.toString());

    return list(requestUri.toString());
  }

  @CliCommand(value = "list", help = "Discover the resources available at a given URI.")
  public String list(
      @CliOption(key = "",
                 mandatory = false,
                 help = "The URI at which to discover resources.",
                 unspecifiedDefaultValue = "/") String path) {
    URI requestUri;
    if("/".equals(path)) {
      requestUri = configCmds.getBaseUri();
    } else if(path.startsWith("http")) {
      requestUri = URI.create(path);
    } else if(resources.containsKey(path)) {
      requestUri = UriComponentsBuilder.fromUriString(resources.get(path)).build().toUri();
    } else {
      requestUri = UriComponentsBuilder.fromUri(configCmds.getBaseUri()).path(path).build().toUri();
    }

    Resources res;
    try {
      res = client.execute(
          requestUri,
          HttpMethod.GET,
          new RequestCallback() {
            @Override public void doWithRequest(ClientHttpRequest request) throws IOException {
              request.getHeaders().setAccept(Arrays.asList(COMPACT_JSON));
            }
          },
          new HttpMessageConverterExtractor<>(PagableResources.class,
                                              client.getMessageConverters())
      );

      if(res.getLinks().size() == 0) {
        return "No resources found...";
      }
    } catch(Throwable t) {
      LOG.error(t.getMessage(), t);
      return "No resources found...";
    }
    StringBuilder sb = new StringBuilder();

    int maxRelLen = 0;
    int maxHrefLen = 0;

    // First get max lengths
    for(Link l : res.getLinks()) {
      if(maxRelLen < l.getRel().length()) {
        maxRelLen = l.getRel().length();
      }
      if(maxHrefLen < l.getHref().length()) {
        maxHrefLen = l.getHref().length();
      }
    }
    maxRelLen += 4;

    sb.append(pad("rel", maxRelLen))
      .append(pad("href", maxHrefLen))
      .append(OsUtils.LINE_SEPARATOR);

    char[] line = new char[maxRelLen + maxHrefLen];
    Arrays.fill(line, '=');
    sb.append(new String(line))
      .append(OsUtils.LINE_SEPARATOR);

    // Now build a table
    for(Link l : res.getLinks()) {
      resources.put(l.getRel(), l.getHref());
      sb.append(pad(l.getRel(), maxRelLen))
        .append(pad(l.getHref(), maxHrefLen))
        .append(OsUtils.LINE_SEPARATOR);
    }

    return sb.toString();
  }

  /**
   * Follow a URI by setting the baseUri to this path, then discovering what resources are available there.
   *
   * @param path
   *     URI to resource.
   *
   * @return
   *
   * @throws IOException
   * @throws URISyntaxException
   */
  @CliCommand(value = "follow",
              help = "Follows a URI path, sets the base to that new path, and discovers what resources are available.")
  public void follow(
      @CliOption(key = "",
                 mandatory = true,
                 help = "The URI to follow.") String path) throws IOException, URISyntaxException {
    if(resources.containsKey(path)) {
      configCmds.setBaseUri(resources.get(path));
    } else {
      configCmds.setBaseUri(path);
    }
  }

  private static String pad(String s, int len) {
    char[] pad = new char[len - s.length()];
    Arrays.fill(pad, ' ');
    return s + new String(pad);
  }

}

