package org.springframework.data.rest.shell.commands;

import java.util.ArrayList;
import java.util.List;

import org.codehaus.jackson.annotate.JsonIgnore;
import org.codehaus.jackson.annotate.JsonUnwrapped;
import org.springframework.expression.AccessException;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.hateoas.Link;

/**
 * @author Jon Brisbin
 */
public class Links {

  private final PropertyAccessor propertyAccessor = new Accessor();
  private final List<Link>       links            = new ArrayList<Link>();

  public Links() {
  }

  @JsonUnwrapped
  public List<Link> getLinks() {
    return this.links;
  }

  public void addLink(Link link) {
    links.add(link);
  }

  @JsonIgnore
  public PropertyAccessor getPropertyAccessor() {
    return propertyAccessor;
  }

  private class Accessor implements PropertyAccessor {

    @Override public Class[] getSpecificTargetClasses() {
      return new Class[]{Links.class};
    }

    @Override public boolean canRead(EvaluationContext context, Object target, String name) throws AccessException {
      return (target instanceof Links);
    }

    @Override public TypedValue read(EvaluationContext context, Object target, String name) throws AccessException {
      for(Link link : links) {
        if(link.getRel().equals(name)) {
          return new TypedValue(link);
        }
      }
      return null;
    }

    @Override public boolean canWrite(EvaluationContext context, Object target, String name) throws AccessException {
      return false;
    }

    @Override public void write(EvaluationContext context, Object target, String name, Object newValue)
        throws AccessException {

    }

  }

}
