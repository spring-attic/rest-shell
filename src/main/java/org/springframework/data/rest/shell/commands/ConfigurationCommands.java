package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.net.URI;
import java.net.URISyntaxException;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.rest.shell.context.BaseUriChangedEvent;
import org.springframework.data.rest.shell.context.HeaderSetEvent;
import org.springframework.http.HttpHeaders;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Configuration commands responsible for changing the settings of the session.
 *
 * @author Jon Brisbin
 */
@Component
public class ConfigurationCommands implements CommandMarker, ApplicationEventPublisherAware {

  @Autowired
  private ContextCommands contextCmds;
  private URI                       baseUri = URI.create((System.getenv("REST_SHELL_BASEURI") == null ? "http://localhost:8080": System.getenv("REST_SHELL_BASEURI")));
  private ApplicationEventPublisher ctx     = null;
  private HttpHeaders               headers = new HttpHeaders();
  private ObjectMapper              mapper  = new ObjectMapper();

  {
    mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
  }

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.ctx = applicationEventPublisher;
  }

  /**
   * Get the current baseUri property.
   *
   * @return
   */
  public URI getBaseUri() {
    return baseUri;
  }

  /**
   * Get the current set of HTTP headers.
   *
   * @return
   */
  public HttpHeaders getHeaders() {
    return headers;
  }

  @CliAvailabilityIndicator({"baseUri", "headers list", "headers set", "headers clear"})
  public boolean isBaseUriAvailable() {
    return true;
  }

  /**
   * Set the session's baseUri to use from this point forward.
   *
   * @param baseUri
   *     Base URI to use for future relative URI calculations.
   *
   * @return
   *
   * @throws URISyntaxException
   */
  @CliCommand(value = "baseUri", help = "Set the base URI to use from this point forward.")
  public String setBaseUri(
      @CliOption(key = "",
                 mandatory = true,
                 help = "The base URI to use from this point forward.",
                 unspecifiedDefaultValue = "http://localhost:8080") String baseUri) throws URISyntaxException {
    if(baseUri.contains("#{")) {
      baseUri = contextCmds.evalAsString(baseUri);
    }
    if(baseUri.endsWith("/")) {
      baseUri = baseUri.substring(0, baseUri.length() - 1);
    }
    // Check whether absolute or relative URI
    if(baseUri.startsWith("http")) {
      // Absolute
      this.baseUri = URI.create(baseUri);
    } else if("/".equals(this.baseUri.getPath())) {
      // Relative to the root
      this.baseUri = UriComponentsBuilder.fromUri(this.baseUri)
                                         .path(baseUri)
                                         .build()
                                         .toUri();
    } else {
      // Relative
      this.baseUri = UriComponentsBuilder.fromUri(this.baseUri)
                                         .pathSegment(baseUri)
                                         .build()
                                         .toUri();
    }
    ctx.publishEvent(new BaseUriChangedEvent(this.baseUri));
    return "Base URI set to '" + this.baseUri + "'";
  }

  /**
   * Print out the current HTTP headers.
   *
   * @return
   *
   * @throws IOException
   */
  @CliCommand(value = "headers list", help = "Print all HTTP headers in use this session.")
  public String headers() throws IOException {
    return mapper.writeValueAsString(headers.toSingleValueMap());
  }

  /**
   * Set an HTTP header to use from this point forward in the session.
   *
   * @param name
   *     Name of the HTTP header.
   * @param value
   *     Value of this header.
   *
   * @return
   *
   * @throws IOException
   */
  @CliCommand(value = "headers set", help = "Set an HTTP header for use this session.")
  public String setHeader(
      @CliOption(key = "name",
                 mandatory = true,
                 help = "The name of the HTTP header.") String name,
      @CliOption(key = "value",
                 mandatory = true,
                 help = "The value of the HTTP header.") String value) throws IOException {
    if(value.contains("#{")) {
      value = contextCmds.evalAsString(value);
    }
    headers.set(name, value);
    ctx.publishEvent(new HeaderSetEvent(name, headers));
    return mapper.writeValueAsString(headers.toSingleValueMap());
  }

  /**
   * Clear the HTTP headers for this session.
   *
   * @return
   */
  @CliCommand(value = "headers clear", help = "Clear the current HTTP headers.")
  public String clearHeaders() {
    headers.clear();
    return "HTTP headers cleared...";
  }

}
