package org.springframework.data.rest.shell.formatter;

import java.util.ArrayList;
import java.util.Collection;

import org.springframework.stereotype.Component;

@Component
public class FormatProvider {
  private final Collection<Formatter> availableFormatter = new ArrayList<Formatter>();
  private final NoOpFormatter         noOpFormatter      = new NoOpFormatter();

  public FormatProvider() {
    availableFormatter.add(new XmlFormatter());
    availableFormatter.add(new JsonFormatter());
  }

  public Formatter getFormatter(String contentType) {
    for(Formatter formatter : availableFormatter) {
      if(formatter.isSupported(contentType)) {
        return formatter;
      }
    }

    return noOpFormatter;
  }
}
