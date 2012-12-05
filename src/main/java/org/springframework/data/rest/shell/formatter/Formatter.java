package org.springframework.data.rest.shell.formatter;

public interface Formatter {
  boolean isSupported(String contentType);
  String format(String nonFormattedString);
}
