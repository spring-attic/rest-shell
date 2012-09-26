package org.springframework.data.rest.shell.context;

import java.net.URI;

import org.springframework.context.ApplicationEvent;

/**
 * @author Jon Brisbin
 */
public class BaseUriChangedEvent extends ApplicationEvent {

  public BaseUriChangedEvent(URI baseUri) {
    super(baseUri);
  }

  public URI getBaseUri() {
    return (URI)getSource();
  }

}
