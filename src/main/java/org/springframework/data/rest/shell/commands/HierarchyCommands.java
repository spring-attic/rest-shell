package org.springframework.data.rest.shell.commands;

import java.net.URI;
import java.net.URISyntaxException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.stereotype.Component;
import org.springframework.web.util.UriComponentsBuilder;

/**
 * Commands that figure out how to traverse the URL hierarchy.
 *
 * @author Jon Brisbin
 */
@Component
public class HierarchyCommands implements CommandMarker {

  @Autowired
  private ConfigurationCommands configCmds;
  @Autowired
  private DiscoveryCommands     discoveryCmds;

  @CliAvailabilityIndicator({"up"})
  public boolean isHierarchyAvailable() {
    String path = configCmds.getBaseUri().getPath();
    return !("".equals(path) || "/".equals(path));
  }

  /**
   * Traverse one level up in the URL hierarchy.
   */
  @CliCommand(value = "up", help = "Traverse one level up in the URL hierarchy.")
  public void up() throws URISyntaxException {
    if(discoveryCmds.getResources().containsKey("parent")) {
      configCmds.setBaseUri(discoveryCmds.getResources().get("parent"));
      return;
    }

    URI baseUri = configCmds.getBaseUri();
    String path = baseUri.getPath();
    if (!("".equals(path) || "/".equals(path))) {
      int idx = path.lastIndexOf("/");
      String newPath = path.substring(0, idx);
      configCmds.setBaseUri(UriComponentsBuilder.fromUri(baseUri).replacePath(newPath).build().toUriString());
    }
  }

}
