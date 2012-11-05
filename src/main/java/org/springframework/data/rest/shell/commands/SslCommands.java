package org.springframework.data.rest.shell.commands;

import java.security.KeyManagementException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;

import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

/**
 * Commands for managing SSL configuration.
 *
 * @author Jon Brisbin
 */
@Component
public class SslCommands implements CommandMarker {

  private final SSLContext defaultContext;

  {
    try {
      defaultContext = SSLContext.getDefault();
    } catch(NoSuchAlgorithmException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  @CliCommand(value = "ssl validate", help = "Manage settings for validation of SSL certificates")
  public void validate(
      @CliOption(
          key = "truststore",
          mandatory = false,
          help = "Set the truststore file to use for SSL") String truststore,
      @CliOption(
          key = "password",
          mandatory = false,
          help = "Set the truststore password to use for SSL") String password,
      @CliOption(
          key = "enabled",
          mandatory = false,
          unspecifiedDefaultValue = "true",
          help = "Set the truststore file to use for SSL") boolean enabled) throws NoSuchAlgorithmException,
                                                                                   KeyManagementException {

    SSLContext ctx = SSLContext.getInstance("TLS");
    if(!enabled) {
      X509TrustManager tm = new X509TrustManager() {
        @Override public void checkClientTrusted(X509Certificate[] x509Certificates,
                                                 String s) throws CertificateException {

        }

        @Override public void checkServerTrusted(X509Certificate[] x509Certificates,
                                                 String s) throws CertificateException {

        }

        @Override public X509Certificate[] getAcceptedIssuers() {
          return null;
        }
      };
      ctx.init(null, new TrustManager[]{tm}, null);
      SSLContext.setDefault(ctx);
    } else {
      SSLContext.setDefault(defaultContext);
    }
  }

}
