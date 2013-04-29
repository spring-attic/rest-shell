package org.springframework.data.rest.shell.commands;

import java.io.IOException;

import org.apache.commons.codec.binary.Base64;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

/**
 * Commands for managing user authentication.
 *
 * @author Jon Brisbin
 */
@Component
public class AuthCommands implements CommandMarker {

  private static final String AUTHORIZATION = "Authorization";
  private static final String BASIC         = "Basic ";
  private static final String HEADER        = AUTHORIZATION + ": ";

  @Autowired
  private ConfigurationCommands configCmds;

  /**
   * Set a Basic authentication header for use throughout this session.
   *
   * @param username
   * @param password
   *
   * @return
   *
   * @throws IOException
   */
  @CliCommand(value = "auth basic", help = "Set the Authorization header value for Basic auth for this session.")
  public String basic(
      @CliOption(
          key = "username",
          mandatory = true,
          help = "The username to use") String username,
      @CliOption(
          key = "password",
          mandatory = false,
          help = "The password to use") String password) throws IOException {

    String token = BASIC + Base64.encodeBase64String((username + ":" + ((password != null) ? password : "")).getBytes());
    configCmds.setHeader(AUTHORIZATION, token);
    return HEADER + token;
  }

  @CliCommand(value = "auth clear", help = "Clear all authentication tokens for this session.")
  public void clear() {
    configCmds.getHeaders().remove(AUTHORIZATION);
  }

}

