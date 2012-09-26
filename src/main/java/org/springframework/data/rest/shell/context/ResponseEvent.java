package org.springframework.data.rest.shell.context;

import java.net.URI;

import org.springframework.context.ApplicationEvent;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;

/**
 * @author Jon Brisbin
 */
public class ResponseEvent extends ApplicationEvent {

  private URI        requestUri;
  private HttpMethod method;

  public ResponseEvent(URI requestUri, HttpMethod method, ResponseEntity<String> response) {
    super(response);
    this.requestUri = requestUri;
    this.method = method;
  }

  public URI getRequestUri() {
    return requestUri;
  }

  public HttpMethod getMethod() {
    return method;
  }

  @SuppressWarnings({"unchecked"})
  public ResponseEntity<String> getResponse() {
    return (ResponseEntity<String>)getSource();
  }

}
