package org.springframework.data.rest.shell.resources;

import org.springframework.hateoas.Resources;

/**
 * @author Jon Brisbin
 */
public class PagableResources<T> extends Resources<T> {

  private Page page;

  public Page getPage() {
    return page;
  }

  public void setPage(Page page) {
    this.page = page;
  }

}
