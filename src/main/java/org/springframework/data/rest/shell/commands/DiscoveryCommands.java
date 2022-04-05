package org.springframework.data.rest.shell.commands;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.net.ssl.HostnameVerifier;
import javax.net.ssl.HttpsURLConnection;
import javax.net.ssl.SSLSession;

import org.codehaus.jackson.map.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.context.ApplicationEventPublisherAware;
import org.springframework.hateoas.Link;
import org.springframework.hateoas.MediaTypes;
import org.springframework.http.HttpMethod;
import org.springframework.http.MediaType;
import org.springframework.http.client.ClientHttpRequest;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;
import org.springframework.util.CollectionUtils;
import org.springframework.web.client.RequestCallback;
import org.springframework.web.client.ResponseExtractor;
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
  private ConfigurationCommands configCmds;
  @Autowired
  private ContextCommands       contextCmds;
  @Autowired
  private SslCommands           sslCmds;
  private SslAwareClientHttpRequestFactory requestFactory = new SslAwareClientHttpRequestFactory();
  @Autowired(required = false)
  private RestTemplate                     client         = new RestTemplate(requestFactory);
  @Autowired(required = false)
  private ObjectMapper                     mapper         = new ObjectMapper();
  private Map<String, String>              resources      = new HashMap<String, String>();
  private ApplicationEventPublisher ctx;

  public DiscoveryCommands() {
  }

  private static String pad(String s, int len) {
    char[] pad = new char[len - s.length()];
    Arrays.fill(pad, ' ');
    return s + new String(pad);
  }

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
  public boolean available() {
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
      @CliOption(key = {"", "rel"},
                 mandatory = false,
                 help = "The URI at which to discover resources.",
                 unspecifiedDefaultValue = "/") PathOrRel path) throws IOException, URISyntaxException {

    URI requestUri;
    if("/".equals(path)) {
      requestUri = configCmds.getBaseUri();
    } else if(path.getPath().startsWith("http")) {
      requestUri = URI.create(path.getPath());
    } else {
      requestUri = UriComponentsBuilder.fromUri(configCmds.getBaseUri()).path(path.getPath()).build().toUri();
    }

    configCmds.setBaseUri(requestUri.toString());

    return list(new PathOrRel(requestUri.toString()), null);
  }

  @CliCommand(value = "list", help = "Discover the resources available at a given URI.")
  public String list(
      @CliOption(key = {"", "rel"},
                 mandatory = false,
                 help = "The URI at which to discover resources.",
                 unspecifiedDefaultValue = "/") PathOrRel path,
      @CliOption(key = "params",
                 mandatory = false,
                 help = "Query parameters to add to the URL.") Map params) {

    URI requestUri;
    if("/".equals(path)) {
      requestUri = configCmds.getBaseUri();
    } else if(path.getPath().startsWith("http")) {
      requestUri = URI.create(path.getPath());
    } else if(resources.containsKey(path)) {
      requestUri = UriComponentsBuilder.fromUriString(resources.get(path))
                                       .build()
                                       .toUri();
    } else if("/".equals(configCmds.getBaseUri().getPath())) {
      requestUri = UriComponentsBuilder.fromUri(configCmds.getBaseUri())
                                       .path(path.getPath())
                                       .build()
                                       .toUri();
    } else {
      requestUri = UriComponentsBuilder.fromUri(configCmds.getBaseUri())
                                       .pathSegment(path.getPath())
                                       .build()
                                       .toUri();
    }

    if(null != params) {
      UriComponentsBuilder urib = UriComponentsBuilder.fromUri(requestUri);
      for(Object key : params.keySet()) {
        urib.queryParam(key.toString(), params.get(key));
      }
      requestUri = urib.build().toUri();
    }

    ExtractLinksHelper elh = new ExtractLinksHelper();
    List<Link> links = client.execute(requestUri, HttpMethod.GET, elh, elh);

    if(links.size() == 0) {
      return "No resources found...";
    }

    StringBuilder sb = new StringBuilder();

    int maxRelLen = 0;
    int maxHrefLen = 0;

    // First get max lengths
    for(Link l : links) {
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
    for(Link l : links) {
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
      @CliOption(key = {"", "rel"},
                 mandatory = true,
                 help = "The URI to follow.") PathOrRel path) throws IOException, URISyntaxException {
    configCmds.setBaseUri(path.getPath());
  }

  private class ExtractLinksHelper implements RequestCallback, ResponseExtractor<List<Link>> {

    @Override public void doWithRequest(ClientHttpRequest request) throws IOException {
      request.getHeaders().setAll(configCmds.getHeaders().toSingleValueMap());
      if(CollectionUtils.isEmpty(request.getHeaders().getAccept())) {
        if(LOG.isDebugEnabled()) {
          LOG.debug("No 'Accept' header specified, using " + COMPACT_JSON);
        }
        request.getHeaders().setAccept(Arrays.asList(COMPACT_JSON, MediaTypes.HAL_JSON, MediaType.APPLICATION_JSON));
      }
    }

    @Override public List<Link> extractData(ClientHttpResponse response) throws IOException {
      List<Link> links = new ArrayList<Link>();

      MediaType ct = response.getHeaders().getContentType();
      if(null != ct && ct.getSubtype().endsWith("json")) {
        Map m = mapper.readValue(response.getBody(), Map.class);

        Object o = m.get("links");
        if(o instanceof List) {
          for(Object lnk : (List)o) {
            if(lnk instanceof Map) {
              Map lnkmap = (Map)lnk;
              String href = String.format("%s", lnkmap.get("href"));
              String rel = String.format("%s", lnkmap.get("rel"));
              links.add(new Link(href, rel));
            }
          }
        } else {
          o = m.get("_links");
          if(o instanceof Map) {
            Map map = (Map) o;
            for (Object key : map.keySet()) {
              Map valueMap = (Map) map.get(key);
              links.add(new Link((String) valueMap.get("href"), (String)key));
            }
          }
        }
      } else if(null != ct && ct.getSubtype().endsWith("uri-list")) {
        BufferedReader rdr = new BufferedReader(new InputStreamReader(response.getBody()));
        String line;
        while(null != (line = rdr.readLine())) {
          links.add(new Link(URI.create(line).toString(), ""));
        }
      }

      if(LOG.isDebugEnabled()) {
        LOG.debug("Returning links: " + links);
      }

      return links;
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

