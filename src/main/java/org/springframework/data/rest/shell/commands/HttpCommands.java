package org.springframework.data.rest.shell.commands;

import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.FilenameFilter;
import java.io.IOException;
import java.io.StringWriter;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

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
import org.springframework.data.rest.shell.formatter.FormatProvider;
import org.springframework.data.rest.shell.formatter.Formatter;
import org.springframework.hateoas.Link;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.http.client.ClientHttpRequest;
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
  @Autowired
  private SslCommands           sslCmds;
  private SslAwareClientHttpRequestFactory requestFactory = new SslAwareClientHttpRequestFactory();
  @Autowired(required = false)
  private RestTemplate                     restTemplate   = new RestTemplate(requestFactory);
  @Autowired(required = false)
  private ObjectMapper                     mapper         = new ObjectMapper();
  private ApplicationEventPublisher ctx;
  private Object                    lastResult;
  private URI                       requestUri;
  @Autowired
  private FormatProvider formatProvider;

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
    requestFactory.setReadTimeout(timeout);
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
                 help = "Query parameters to add to the URL as a simplified JSON fragment '{paramName:\"paramValue\"}' or '{paramName: [\"paramValue1\",\"paramValue2\"]}' for multiple values.") Map params,
      @CliOption(key = "output",
                 mandatory = false,
                 help = "The path to dump the output to.") String outputPath) {

    outputPath = contextCmds.evalAsString(outputPath);

    UriComponentsBuilder ucb = createUriComponentsBuilder(path.getPath());
    if(null != params) {
      for(Object key : params.keySet()) {
        Object o = params.get(key);
        if(List.class.isAssignableFrom(o.getClass()))
        {
        	@SuppressWarnings("rawtypes")
			List list = (List)o;
        	for(Object item : list)
        	{
        		ucb.queryParam(key.toString(), item.toString());
        	}
        }
        else
        {
        	ucb.queryParam(key.toString(), o.toString());
        }
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
                 help = "The path to the resource.",
                 unspecifiedDefaultValue = "") PathOrRel path,
      @CliOption(key = "data",
                 mandatory = true,
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
                 help = "The path to dump the output to.") String outputPath) throws IOException {

    fromDir = contextCmds.evalAsString(fromDir);
    outputPath = contextCmds.evalAsString(outputPath);

    requestUri = createUriComponentsBuilder(path.getPath()).build().toUri();

    Object obj;
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
      return execute(HttpMethod.PUT, obj, follow, outputPath);
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
                 mandatory = false,
                 help = "Issue HTTP DELETE to delete a resource.",
                 unspecifiedDefaultValue = "") PathOrRel path,
      @CliOption(key = "follow",
                 mandatory = false,
                 help = "If a Location header is returned, immediately follow it.",
                 unspecifiedDefaultValue = "false") final boolean follow,
      @CliOption(key = "output",
                 mandatory = false,
                 help = "The path to dump the output to.") String outputPath) {

    outputPath = contextCmds.evalAsString(outputPath);

    requestUri = createUriComponentsBuilder(path.getPath()).build().toUri();

    return execute(HttpMethod.DELETE, null, follow, outputPath);
  }

  public String execute(final HttpMethod method,
                        final Object data,
                        final boolean follow,
                        final String outputPath) {
    final StringBuilder buffer = new StringBuilder();
    MediaType contentType = configCmds.getHeaders().getContentType();
    if(contentType == null) {
      contentType = MediaType.APPLICATION_JSON;
    }

    ResponseErrorHandler origErrHandler = restTemplate.getErrorHandler();
    RequestHelper helper = (null == data ? new RequestHelper() : new RequestHelper(data, contentType));
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
      FileWriter writer = null;
      try {
        writer = new FileWriter(new File(outputPath));
        writer.write(buffer.toString());
        writer.flush();
      } catch(IOException e) {
        LOG.error(e.getMessage(), e);
        throw new IllegalArgumentException(e);
      } finally {
        if(null != writer) {
          try {
            writer.close();
          } catch(IOException e) {
          }
        }
      }
      return ">> " + outputPath;
    } else {
      return buffer.toString();
    }
  }

  private String readFileOrFiles(final HttpMethod method,
                                 final String fromPath,
                                 final boolean follow,
                                 final String outputPath) throws IOException {
    String output = "";

    File fromFile = new File(fromPath);
    if(!fromFile.exists()) {
      throw new IllegalArgumentException("Path " + fromPath + " not found.");
    }

    if(fromFile.isDirectory()) {
      final AtomicInteger numItems = new AtomicInteger(0);

      FilenameFilter jsonFilter = new FilenameFilter() {
        @Override public boolean accept(File file, String s) {
          return s.endsWith(".json");
        }
      };
      for(File file : fromFile.listFiles(jsonFilter)) {
        Object body = readFile(file);
        String response = execute(method,
                                  body,
                                  follow,
                                  outputPath);
        if(LOG.isDebugEnabled()) {
          LOG.debug(response);
        }

        numItems.incrementAndGet();
      }

      output = numItems.get() + " files uploaded to the server using " + method;
    } else {
      Object body = readFile(fromFile);
      String response = execute(HttpMethod.POST,
                                body,
                                follow,
                                outputPath);
      if(LOG.isDebugEnabled()) {
        LOG.debug(response);
      }

      output = response;
    }

    return output;
  }

  private Object readFile(File file) throws IOException {
    StringBuilder builder = new StringBuilder();
    FileReader reader = new FileReader(file);
    char[] buffer = new char[8 * 1024];
    int read;
    while(-1 < (read = reader.read(buffer))) {
      String s = new String(buffer, 0, read);
      builder.append(s);
    }

    String bodyAsString = builder.toString();
    Object body = "";
    if(bodyAsString.length() > 0) {
      if(bodyAsString.charAt(0) == '{') {
        body = mapper.readValue(bodyAsString, Map.class);
      } else if(bodyAsString.charAt(0) == '[') {
        body = mapper.readValue(bodyAsString, List.class);
      } else {
        body = bodyAsString;
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
      final Formatter formatter = formatProvider.getFormatter(response.getHeaders().getContentType().getSubtype());
      buffer.append(formatter.format(response.getBody()));
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
      if(null != body && null != ct && ct.getSubtype().endsWith("json")) {
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

  private class SslAwareClientHttpRequestFactory extends SimpleClientHttpRequestFactory {
    @Override protected void prepareConnection(HttpURLConnection connection, String httpMethod) throws IOException {
      if(connection instanceof HttpsURLConnection) {
        HttpsURLConnection httpsConnection = (HttpsURLConnection)connection;
        if(!sslCmds.getValidate()) {
          httpsConnection.setHostnameVerifier(new HostnameVerifier() {
            @Override public boolean verify(String s, SSLSession sslSession) {
              return true;
            }
          });
          httpsConnection.setSSLSocketFactory(sslCmds.getCustomContext().getSocketFactory());
        }
      }
      super.prepareConnection(connection, httpMethod);
    }
  }
}
