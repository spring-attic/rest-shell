package org.springframework.data.rest.shell.commands;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.List;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationListener;
import org.springframework.data.rest.shell.context.BaseUriChangedEvent;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.shell.support.util.OsUtils;
import org.springframework.stereotype.Component;

/**
 * Commands for tracking the baseUris that have been set in this session.
 *
 * @author Jon Brisbin
 */
@Component
public class HistoryCommands implements CommandMarker, ApplicationListener<BaseUriChangedEvent> {

  @Autowired
  private ConfigurationCommands configCmds;
  private List<URI> baseUris = new ArrayList<URI>();

  @Override public void onApplicationEvent(BaseUriChangedEvent event) {
    baseUris.add(event.getBaseUri());
  }

  @CliAvailabilityIndicator({"history list", "history go"})
  public boolean isHistoryAvailable() {
    return true;
  }

  public List<URI> getHistory() {
    return baseUris;
  }

  @CliCommand(value = "history list", help = "List the URLs in the history.")
  public String list() {
    StringBuilder buffer = new StringBuilder();
    for(int i = 0; i < baseUris.size(); i++) {
      URI uri = baseUris.get(i);
      buffer.append(i + 1).append(": ").append(uri.toString()).append(OsUtils.LINE_SEPARATOR);
    }
    return buffer.toString();
  }

  @CliCommand(value = "history go", help = "Go to a specific URL in the history.")
  public void go(
      @CliOption(key = "",
                 mandatory = true,
                 help = "The history entry to set the baseUri to.") Integer num) throws URISyntaxException {
    if(num < 1) {
      return;
    }
    if(num > baseUris.size()) {
      return;
    }
    URI uri = baseUris.get(num - 1);
    configCmds.setBaseUri(uri.toString());
  }

}
