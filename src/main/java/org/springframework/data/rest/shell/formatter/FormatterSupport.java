package org.springframework.data.rest.shell.formatter;

import java.util.Collection;

public abstract class FormatterSupport implements Formatter {
  @Override
  public boolean isSupported(String contentType) {
    boolean supported = false;
    for (String subType : getSupportedList()) {
      supported = contentType.indexOf(subType) > 0;
      if(supported) {
        break;
      }
    }
    return supported;
  }

  public abstract Collection<String> getSupportedList();
}
