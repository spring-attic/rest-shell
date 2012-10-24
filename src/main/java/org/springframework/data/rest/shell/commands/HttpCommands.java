package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.io.StringWriter;
import java.io.UnsupportedEncodingException;
import java.net.URI;
import java.net.URISyntaxException;
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

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.data.rest.shell.context.ResponseEvent;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;
import org.springframework.web.client.DefaultResponseErrorHandler;
import org.springframework.web.client.HttpMessageConverterExtractor;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.ResponseErrorHandler;
import org.springframework.web.client.ResponseExtractor;
import org.springframework.web.client.RestTemplate;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Commands that issue the HTTP requests.
 *
 * @author Jon Brisbin
 */
@Component
public class HttpCommands implements CommandMarker, ApplicationEventPublisherAware, InitializingBean {

  private static final Logger LOG = LoggerFactory.getLogger(HttpCommands.class);

  private static final String LOCATION_HEADER = "Location";

  @Autowired
  private ConfigurationCommands configCmds;
  @Autowired
  private DiscoveryCommands     discoveryCmds;
  @Autowired
  private ContextCommands       contextCmds;
  private ClientHttpRequestFactory requestFactory = new SimpleClientHttpRequestFactory();
  @Autowired(required = false)
  private RestTemplate             restTemplate   = new RestTemplate(requestFactory);
  @Autowired(required = false)
  private ObjectMapper             mapper         = new ObjectMapper();
  private ApplicationEventPublisher ctx;
  private Object                    lastResult;
  private URI                       requestUri;

  @Override public void setApplicationEventPublisher(ApplicationEventPublisher applicationEventPublisher) {
    this.ctx = applicationEventPublisher;
  }

  @Override public void afterPropertiesSet() throws Exception {
    mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
    mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);

    restTemplate.setErrorHandler(new DefaultResponseErrorHandler() {
      @Override public void handleError(ClientHttpResponse response) throws IOException {
      }
    });
  }

  @CliAvailabilityIndicator({"timeout", "get", "post", "put", "delete"})
  public boolean isHttpCommandAvailable() {
    return true;
  }

  @CliCommand(value = "timeout", help = "Set the read timeout for requests.")
  public void timeout(@CliOption(key = "",
                                 mandatory = true,
                                 help = "The timeout (in milliseconds) to wait for a response.",
                                 unspecifiedDefaultValue = "30000") int timeout) {
    if(requestFactory instanceof SimpleClientHttpRequestFactory) {
      ((SimpleClientHttpRequestFactory)requestFactory).setReadTimeout(timeout);
    } else {
      if(LOG.isWarnEnabled()) {
        LOG.warn("Cannot set timeout on ClientHttpRequestFactory type " + requestFactory.getClass().getName());
      }
    }
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
      @CliOption(key = {"", "rel"},
                 mandatory = false,
                 help = "The path to the resource to GET.",
                 unspecifiedDefaultValue = "") PathOrRel path,
      @CliOption(key = "follow",
                 mandatory = false,
                 help = "If a Location header is returned, immediately follow it.",
                 unspecifiedDefaultValue = "false") final boolean follow,
      @CliOption(key = "params",
                 mandatory = false,
                 help = "Query parameters to add to the URL as a simplified JSON fragment '{paramName:\"paramValue\"}'.") Map params,
      @CliOption(key = "output",
                 mandatory = false,
                 help = "The path to dump the output to.") String outputPath) {

    outputPath = contextCmds.evalAsString(outputPath);

    UriComponentsBuilder ucb = createUriComponentsBuilder(path.getPath());
    if(null != params) {
      for(Object key : params.keySet()) {
        Object o = params.get(key);
        ucb.queryParam(key.toString(), encode(o.toString()));
      }
    }
    requestUri = ucb.build().toUri();

    return execute(HttpMethod.GET, null, follow, outputPath);
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
      @CliOption(key = {"", "rel"},
                 mandatory = false,
                 help = "The path to the resource collection.",
                 unspecifiedDefaultValue = "") PathOrRel path,
      @CliOption(key = "data",
                 mandatory = false,
                 help = "The JSON data to use as the resource.") String data,
      @CliOption(key = "from",
                 mandatory = false,
                 help = "The directory from which to read JSON files to POST to the server.") String fromDir,
      @CliOption(key = "follow",
                 mandatory = false,
                 help = "If a Location header is returned, immediately follow it.",
                 unspecifiedDefaultValue = "false") final boolean follow,
      @CliOption(key = "output",
                 mandatory = false,
                 help = "The path to dump the output to.") String outputTo) throws IOException {

    fromDir = contextCmds.evalAsString(fromDir);
    final String outputPath = contextCmds.evalAsString(outputTo);

    requestUri = createUriComponentsBuilder(path.getPath()).build().toUri();

    Object obj = null;
    if(null != data) {
      if(data.contains("#{")) {
        obj = contextCmds.eval(data);
      } else {
        Class<?> targetType = Map.class;
        if(data.startsWith("[")) {
          targetType = List.class;
        }
        obj = mapper.readValue(data.replaceAll("\\\\", "").replaceAll("'", "\""), targetType);
      }
    }

    if(null != fromDir) {
      fromDir = contextCmds.evalAsString(fromDir);
      try {
        return readFileOrFiles(HttpMethod.POST, fromDir, follow, outputPath);
      } catch(IOException e) {
        throw new IllegalStateException(e);
      }
    }

    return execute(HttpMethod.POST, obj, follow, outputPath);
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
      @CliOption(key = {"", "rel"},
                 mandatory = false,
                 help = "The path to the resource.") PathOrRel path,
      @CliOption(key = "data",
                 mandatory = true,
                 help = "The JSON data to use as the resource.") String data,
      @CliOption(key = "from",
                 mandatory = false,
                 help = "The directory from which to read JSON files to POST to the server.") String fromDir,
      @CliOption(key = "output",
                 mandatory = false,
                 help = "The path to dump the output to.") String outputPath) {

    fromDir = contextCmds.evalAsString(fromDir);
    outputPath = contextCmds.evalAsString(outputPath);
    data = data.replaceAll("\\\\", "").replaceAll("'", "\"");

    requestUri = createUriComponentsBuilder(path.getPath()).build().toUri();

    Object obj;
    if(null != data) {
      if(data.contains("#{")) {
        obj = contextCmds.eval(data);
      } else {
        obj = data;
      }
      return execute(HttpMethod.PUT, obj, false, outputPath);
    }

    if(null != fromDir) {
      fromDir = contextCmds.evalAsString(fromDir);
      try {
        return readFileOrFiles(HttpMethod.PUT, fromDir, false, outputPath);
      } catch(IOException e) {
        throw new IllegalStateException(e);
      }
    }

    return null;
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
      @CliOption(key = {"", "rel"},
                 mandatory = true,
                 help = "Issue HTTP DELETE to delete a resource.") PathOrRel path,
      @CliOption(key = "output",
                 mandatory = false,
                 help = "The path to dump the output to.") String outputPath) {

    outputPath = contextCmds.evalAsString(outputPath);

    requestUri = createUriComponentsBuilder(path.getPath()).build().toUri();

    return execute(HttpMethod.DELETE, null, false, outputPath);
  }

  public String execute(final HttpMethod method,
                        final Object data,
                        final boolean follow,
                        final String outputPath) {
    final StringBuilder buffer = new StringBuilder();

    ResponseErrorHandler origErrHandler = restTemplate.getErrorHandler();
    RequestHelper helper = (null == data ? new RequestHelper() : new RequestHelper(data, MediaType.APPLICATION_JSON));
    ResponseEntity<String> response;
    try {
      restTemplate.setErrorHandler(new ResponseErrorHandler() {
        @Override public boolean hasError(ClientHttpResponse response) throws IOException {
          HttpStatus status = response.getStatusCode();
          return (status == HttpStatus.BAD_GATEWAY || status == HttpStatus.GATEWAY_TIMEOUT);
        }

        @Override public void handleError(ClientHttpResponse response) throws IOException {
          if(LOG.isWarnEnabled()) {
            LOG.warn("Client encountered an error " + response.getRawStatusCode() + ". Retrying...");
          }
          System.out.println(execute(method, data, follow, outputPath));
        }
      });

      if(LOG.isInfoEnabled()) {
        LOG.info("Sending " + method + " to " + requestUri + " using " + data);
      }
      response = restTemplate.execute(requestUri, method, helper, helper);

    } catch(ResourceAccessException e) {
      if(LOG.isWarnEnabled()) {
        LOG.warn("Client encountered an error. Retrying. (" + e.getMessage() + ")", e);
      }
      // Calling this method recursively results in hang, so just retry once.
      response = restTemplate.execute(requestUri, method, helper, helper);
    } finally {
      restTemplate.setErrorHandler(origErrHandler);
    }

    if(follow && response.getHeaders().containsKey(LOCATION_HEADER)) {
      try {
        configCmds.setBaseUri(response.getHeaders().getFirst(LOCATION_HEADER));
      } catch(URISyntaxException e) {
        LOG.error("Error following Location header: " + e.getMessage(), e);
      }
    }

    outputRequest(method.name(), requestUri, buffer);
    ctx.publishEvent(new ResponseEvent(requestUri, method, response));
    outputResponse(response, buffer);

    if(null != outputPath) {
      try {
        Files.write(Paths.get(outputPath), buffer.toString().getBytes());
      } catch(IOException e) {
        LOG.error(e.getMessage(), e);
        throw new IllegalArgumentException(e);
      }
      return ">> " + outputPath;
    } else {
      return buffer.toString();
    }
  }

  private String readFileOrFiles(final HttpMethod method,
                                 final String fromDir,
                                 final boolean follow,
                                 final String outputPath) throws IOException {
    final AtomicInteger numItems = new AtomicInteger(0);

    Path filePath = Paths.get(fromDir);

    if(!Files.exists(filePath)) {
      throw new IllegalArgumentException("Path " + fromDir + " not found.");
    }

    if(Files.isDirectory(filePath)) {
      Files.walkFileTree(
          filePath,
          EnumSet.of(FileVisitOption.FOLLOW_LINKS),
          Integer.MAX_VALUE,
          new SimpleFileVisitor<Path>() {
            @Override public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                throws IOException {
              if(!file.toString().endsWith("json")) {
                return FileVisitResult.CONTINUE;
              }

              Object body = readFile(file);
              String response = execute(method,
                                        body,
                                        follow,
                                        outputPath);
              if(LOG.isDebugEnabled()) {
                LOG.debug(response);
              }

              numItems.incrementAndGet();

              return FileVisitResult.CONTINUE;
            }
          }
      );
    } else {
      Object body = readFile(filePath);
      String response = execute(HttpMethod.POST,
                                body,
                                follow,
                                outputPath);
      if(LOG.isDebugEnabled()) {
        LOG.debug(response);
      }
      numItems.incrementAndGet();
    }

    return numItems.get() + " files uploaded to the server using " + method;
  }

  private Object readFile(Path file) throws IOException {
    byte[] jsonData = Files.readAllBytes(file);
    Object body = "";
    if(jsonData.length > 0) {
      if(jsonData[0] == '{') {
        body = mapper.readValue(jsonData, Map.class);
      } else if(jsonData[0] == '[') {
        body = mapper.readValue(jsonData, List.class);
      } else {
        body = jsonData;
      }
    }
    return body;
  }

  private UriComponentsBuilder createUriComponentsBuilder(String path) {
    UriComponentsBuilder ucb;
    if(discoveryCmds.getResources().containsKey(path)) {
      ucb = UriComponentsBuilder.fromUriString(discoveryCmds.getResources().get(path));
    } else {
      if(path.startsWith("http")) {
        ucb = UriComponentsBuilder.fromUriString(path);
      } else {
        ucb = UriComponentsBuilder.fromUri(configCmds.getBaseUri()).pathSegment(path);
      }
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

  private void outputResponse(ResponseEntity<String> response, StringBuilder buffer) {
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

  private class RequestHelper implements RequestCallback,
                                         ResponseExtractor<ResponseEntity<String>> {

    private Object    body;
    private MediaType contentType;
    private HttpMessageConverterExtractor<String> extractor = new HttpMessageConverterExtractor<String>(String.class,
                                                                                                        restTemplate.getMessageConverters());
    private ObjectMapper                          mapper    = new ObjectMapper();

    {
      mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
    }

    private RequestHelper() {
    }

    private RequestHelper(Object body, MediaType contentType) {
      this.body = body;
      this.contentType = contentType;
    }

    @Override public void doWithRequest(ClientHttpRequest request) throws IOException {
      request.getHeaders().setAll(configCmds.getHeaders().toSingleValueMap());
      if(null != contentType) {
        request.getHeaders().setContentType(contentType);
      }
      if(null != body) {
        if(body instanceof String) {
          request.getBody().write(((String)body).getBytes());
        } else if(body instanceof byte[]) {
          request.getBody().write((byte[])body);
        } else {
          mapper.writeValue(request.getBody(), body);
        }
      }
    }

    @SuppressWarnings({"unchecked"})
    @Override public ResponseEntity<String> extractData(ClientHttpResponse response) throws IOException {
      String body = extractor.extractData(response);
      contextCmds.variables.put("requestUrl", requestUri.toString());
      contextCmds.variables.put("responseHeaders", response.getHeaders());
      contextCmds.variables.put("responseBody", null);

      MediaType ct = response.getHeaders().getContentType();
      if(null != ct && ct.getSubtype().endsWith("json")) {
        // Pretty-print the JSON
        if(body.startsWith("{")) {
          lastResult = mapper.readValue(body.getBytes(), Map.class);
        } else if(body.startsWith("[")) {
          lastResult = mapper.readValue(body.getBytes(), List.class);
        } else {
          lastResult = new String(body.getBytes());
        }

        contextCmds.variables.put("responseBody", lastResult);

        if(lastResult instanceof Map && ((Map)lastResult).containsKey("links")) {
          Links linksobj;
          if(contextCmds.variables.containsKey("links")) {
            linksobj = (Links)contextCmds.variables.get("links");
          } else {
            linksobj = new Links();
            contextCmds.evalCtx.addPropertyAccessor(linksobj.getPropertyAccessor());
          }
          linksobj.getLinks().clear();
          for(Map<String, String> linkmap : (List<Map<String, String>>)((Map)lastResult).get("links")) {
            linksobj.addLink(new Link(linkmap.get("href"), linkmap.get("rel")));
          }
          contextCmds.variables.put("links", linksobj);
        }

        StringWriter sw = new StringWriter();
        mapper.writeValue(sw, lastResult);
        body = sw.toString();
      }

      return new ResponseEntity<String>(body, response.getHeaders(), response.getStatusCode());
    }
  }

}
