package org.springframework.data.rest.shell.context;

import java.net.URI;

import org.springframework.context.ApplicationEvent;
import org.springframework.http.ResponseEntity;

/**
 * @author Jon Brisbin
 */
public class ResponseEvent extends ApplicationEvent {

  private URI requestUri;

  public ResponseEvent(URI requestUri, ResponseEntity<String> response) {
    super(response);
    this.requestUri = requestUri;
  }

  public URI getRequestUri() {
    return requestUri;
  }

  @SuppressWarnings({"unchecked"})
  public ResponseEntity<String> getResponse() {
    return (ResponseEntity<String>)getSource();
  }

}
