package org.springframework.data.rest.shell.formatter;

public class NoOpFormatter implements Formatter {
  @Override
  public boolean isSupported(String contentType) {
    return true;
  }

  @Override
  public String format(String nonFormattedString) {
    return nonFormattedString;
  }
}
