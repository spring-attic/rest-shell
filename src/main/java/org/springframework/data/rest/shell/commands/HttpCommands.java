package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URLEncoder;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.SimpleFileVisitor;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.rest.shell.context.ResponseEvent;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Commands that issue the HTTP requests.
 *
 * @author Jon Brisbin
 */
@Component
public class HttpCommands implements CommandMarker, ApplicationEventPublisherAware {

  private static final Logger LOG = LoggerFactory.getLogger(HttpCommands.class);

  @Autowired
  private ConfigurationCommands configCmds;
  @Autowired
  private DiscoveryCommands     discoveryCmds;
  @Autowired(required = false)
  private RestTemplate restTemplate = new RestTemplate();
  private ObjectMapper mapper       = new ObjectMapper();
  private ApplicationEventPublisher ctx;

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.ctx = applicationEventPublisher;
  }

  @CliAvailabilityIndicator({"get", "post", "put", "delete"})
  public boolean isHttpCommandAvailable() {
    return true;
  }

  /**
   * HTTP GET to retrieve a resource.
   *
   * @param path
   *     URI to resource.
   * @param params
   *     URL query parameters to pass for paging and search.
   *
   * @return
   */
  @CliCommand(value = "get", help = "Issue HTTP GET to a resource.")
  public String get(
      @CliOption(key = "",
                 mandatory = false,
                 help = "The path to the resource to GET.",
                 unspecifiedDefaultValue = "") String path,
      @CliOption(key = "params",
                 mandatory = false,
                 help = "Query parameters to add to the URL.") Map params) {
    UriComponentsBuilder ucb = createUriComponentsBuilder(path);
    if(null != params) {
      for(Object key : params.keySet()) {
        Object o = params.get(key);
        ucb.queryParam(key.toString(), encode(o.toString()));
      }
    }
    URI requestUri = ucb.build().toUri();

    return execute(requestUri, HttpMethod.GET, null);
  }

  /**
   * HTTP POST to create a new resource.
   *
   * @param path
   *     URI to resource.
   * @param data
   *     The JSON data to send.
   *
   * @return
   */
  @CliCommand(value = "post", help = "Issue HTTP POST to create a new resource.")
  public String post(
      @CliOption(key = "",
                 mandatory = false,
                 help = "The path to the resource collection.",
                 unspecifiedDefaultValue = "") String path,
      @CliOption(key = "data",
                 mandatory = false,
                 help = "The JSON data to use as the resource.") Map data,
      @CliOption(key = "from",
                 mandatory = false,
                 help = "The directory from which to read JSON files to POST to the server.") String fromDir)
      throws IOException {
    final URI requestUri = createUriComponentsBuilder(path).build().toUri();
    if(null != data) {
      return execute(requestUri, HttpMethod.POST, data);
    } else if(null != fromDir) {
      final AtomicInteger numItems = new AtomicInteger(0);

      Files.walkFileTree(
          Paths.get(fromDir),
          EnumSet.of(FileVisitOption.FOLLOW_LINKS),
          Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if(file.toString().endsWith("json")) {
                byte[] jsonData = Files.readAllBytes(file);
                String response = execute(requestUri,
                                          HttpMethod.POST,
                                          mapper.readValue(jsonData, Map.class));
                if(LOG.isDebugEnabled()) {
                  LOG.debug(response);
                }
                numItems.incrementAndGet();
              }
              return FileVisitResult.CONTINUE;
            }
          }
      );

      return numItems.get() + " items POSTed to the server.";
    } else {
      return null;
    }
  }

  /**
   * HTTP PUT to update a resource.
   *
   * @param path
   *     URI to resource.
   * @param data
   *     The JSON data to send.
   *
   * @return
   */
  @CliCommand(value = "put", help = "Issue HTTP PUT to update a resource.")
  public String put(
      @CliOption(key = "",
                 mandatory = false,
                 help = "The path to the resource.") String path,
      @CliOption(key = "data",
                 mandatory = true,
                 help = "The JSON data to use as the resource.") Map data) {
    URI requestUri = createUriComponentsBuilder(path).build().toUri();
    return execute(requestUri, HttpMethod.PUT, data);
  }

  /**
   * HTTP DELETE to delete a resource.
   *
   * @param path
   *     URI to resource.
   *
   * @return
   */
  @CliCommand(value = "delete", help = "Issue HTTP DELETE to delete a resource.")
  public String delete(
      @CliOption(key = "",
                 mandatory = true,
                 help = "Issue HTTP DELETE to delete a resource.") String path) {
    URI requestUri = createUriComponentsBuilder(path).build().toUri();
    return execute(requestUri, HttpMethod.DELETE, null);
  }

  public String execute(URI requestUri,
                        HttpMethod method,
                        Map data) {
    StringBuilder buffer = new StringBuilder();

    outputRequest(method.name(), requestUri, buffer);

    RequestHelper helper = (null == data ? new RequestHelper() : new RequestHelper(data, MediaType.APPLICATION_JSON));
    ResponseEntity<String> response = restTemplate.execute(requestUri, method, helper, helper);

    ctx.publishEvent(new ResponseEvent(requestUri, method, response));

    outputResponse(response, buffer);

    return buffer.toString();
  }

  private UriComponentsBuilder createUriComponentsBuilder(String path) {
    UriComponentsBuilder ucb;
    if(discoveryCmds.getResources().containsKey(path)) {
      ucb = UriComponentsBuilder.fromUriString(discoveryCmds.getResources().get(path));
    } else {
      ucb = UriComponentsBuilder.fromUri(configCmds.getBaseUri()).pathSegment(path);
    }
    return ucb;
  }

  private static String encode(String s) {
    try {
      return URLEncoder.encode(s, "ISO-8859-1");
    } catch(UnsupportedEncodingException e) {
      throw new IllegalStateException(e);
    }
  }

  private void outputRequest(String method, URI requestUri, StringBuilder buffer) {
    buffer.append("> ")
          .append(method)
          .append(" ")
          .append(requestUri.toString())
          .append(OsUtils.LINE_SEPARATOR);
    for(Map.Entry<String, String> entry : configCmds.getHeaders().toSingleValueMap().entrySet()) {
      buffer.append("> ")
            .append(entry.getKey())
            .append(": ")
            .append(entry.getValue())
            .append(OsUtils.LINE_SEPARATOR);
    }
    buffer.append(OsUtils.LINE_SEPARATOR);
  }

  private static void outputResponse(ResponseEntity<String> response, StringBuilder buffer) {
    buffer.append("< ")
          .append(response.getStatusCode().value())
          .append(" ")
          .append(response.getStatusCode().name())
          .append(OsUtils.LINE_SEPARATOR);
    for(Map.Entry<String, List<String>> entry : response.getHeaders().entrySet()) {
      buffer.append("< ")
            .append(entry.getKey())
            .append(": ");
      boolean first = true;
      for(String s : entry.getValue()) {
        if(!first) {
          buffer.append(",");
        } else {
          first = false;
        }
        buffer.append(s);
      }
      buffer.append(OsUtils.LINE_SEPARATOR);
    }
    buffer.append("< ").append(OsUtils.LINE_SEPARATOR);
    if(null != response.getBody()) {
      buffer.append(response.getBody());
    }
  }

  private class RequestHelper implements RequestCallback, ResponseExtractor<ResponseEntity<String>> {

    private Map       body;
    private MediaType contentType;
    private HttpMessageConverterExtractor<String> extractor = new HttpMessageConverterExtractor<>(String.class,
                                                                                                  restTemplate.getMessageConverters());

    private RequestHelper() {
    }

    private RequestHelper(Map body, MediaType contentType) {
      this.body = body;
      this.contentType = contentType;
    }

    @Override public void doWithRequest(ClientHttpRequest request) throws IOException {
      request.getHeaders().setAll(configCmds.getHeaders().toSingleValueMap());
      if(null != contentType) {
        request.getHeaders().setContentType(contentType);
      }
      if(null != body) {
        mapper.writeValue(request.getBody(), body);
      }
    }

    @Override public ResponseEntity<String> extractData(ClientHttpResponse response) throws IOException {
      String body = extractor.extractData(response);
      return new ResponseEntity<>(body, response.getHeaders(), response.getStatusCode());
    }
  }

}
