package org.springframework.data.rest.shell.context;

import org.springframework.context.ApplicationEvent;
import org.springframework.http.HttpHeaders;

/**
 * Event emitted when an HTTP header is set.
 *
 * @author Jon Brisbin
 */
public class HeaderSetEvent extends ApplicationEvent {

  private String name;

  public HeaderSetEvent(String name, HttpHeaders headers) {
    super(headers);
    this.name = name;
  }

  public String getName() {
    return name;
  }

  public HttpHeaders getHeaders() {
    return (HttpHeaders)getSource();
  }

}
