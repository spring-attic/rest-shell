package org.springframework.data.rest.shell.commands;

/**
 * @author Jon Brisbin
 */
public class PathOrRel {

  private final String path;

  public PathOrRel(String path) {
    this.path = path;
  }

  public String getPath() {
    return path;
  }

}
