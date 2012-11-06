package org.springframework.data.rest.shell.commands;

import java.io.File;
import java.io.FileInputStream;
import java.io.IOException;
import java.security.KeyManagementException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.KeyManager;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;
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
  private       SSLContext customContext;
  private boolean validate = true;
  private KeyManager[]   keyManagers;
  private TrustManager[] trustManagers;
  private File           truststore;
  private String         truststorePassword;
  private File           keystore;
  private String         keystorePassword;

  {
    try {
      defaultContext = SSLContext.getDefault();
    } catch(NoSuchAlgorithmException e) {
      throw new IllegalStateException(e.getMessage(), e);
    }
  }

  public SSLContext getCustomContext() {
    return (null == customContext ? defaultContext : customContext);
  }

  public KeyManager[] getKeyManagers() throws IOException,
                                              KeyStoreException,
                                              NoSuchAlgorithmException,
                                              CertificateException {
    if(null != keystore) {
      KeyStore keyks = KeyStore.getInstance("JKS");
      keyks.load(new FileInputStream(keystore), keystorePassword.toCharArray());
      KeyManagerFactory keyfac = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
      keyManagers = keyfac.getKeyManagers();
    }

    return keyManagers;
  }

  public TrustManager[] getTrustManagers() throws KeyStoreException,
                                                  IOException,
                                                  NoSuchAlgorithmException,
                                                  CertificateException {
    if(!validate) {
      return new TrustManager[]{
          new X509TrustManager() {
            @Override public void checkClientTrusted(X509Certificate[] x509Certificates,
                                                     String s) throws CertificateException {

            }

            @Override public void checkServerTrusted(X509Certificate[] x509Certificates,
                                                     String s) throws CertificateException {

            }

            @Override public X509Certificate[] getAcceptedIssuers() {
              return null;
            }
          }
      };
    } else if(null != truststore) {
      KeyStore trustks = KeyStore.getInstance("JKS");
      trustks.load(new FileInputStream(truststore), truststorePassword.toCharArray());
      TrustManagerFactory trustfac = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
      trustManagers = trustfac.getTrustManagers();
    }

    return trustManagers;
  }

  public boolean getValidate() {
    return validate;
  }

  @CliCommand(value = "ssl reset", help = "Reset the SSL configuration back to the system default")
  public void reset() {
    SSLContext.setDefault(defaultContext);
  }

  @CliCommand(value = "ssl truststore", help = "Manage the truststore TrustManagers")
  public void truststore(
      @CliOption(
          key = "file",
          mandatory = true,
          help = "Set the truststore file to use for SSL") File truststore,
      @CliOption(
          key = "password",
          mandatory = false,
          help = "Set the truststore password to use for SSL") String password) throws IOException,
                                                                                       NoSuchAlgorithmException,
                                                                                       KeyManagementException,
                                                                                       KeyStoreException,
                                                                                       CertificateException {
    this.truststore = truststore;
    this.truststorePassword = (null == password ? "" : password);
    setCustomContext();
  }

  @CliCommand(value = "ssl keystore", help = "Manage the keystore KeyManagers")
  public void keystore(
      @CliOption(
          key = "file",
          mandatory = true,
          help = "Set the keystore file to use for SSL") File keystore,
      @CliOption(
          key = "password",
          mandatory = false,
          help = "Set the keystore password to use for SSL") String password) throws IOException,
                                                                                     NoSuchAlgorithmException,
                                                                                     KeyManagementException,
                                                                                     KeyStoreException,
                                                                                     CertificateException {
    this.keystore = keystore;
    this.keystorePassword = (null == password ? "" : password);
    setCustomContext();
  }

  @CliCommand(value = "ssl validate", help = "Manage settings for validation of SSL certificates")
  public void validate(
      @CliOption(
          key = "enabled",
          mandatory = false,
          unspecifiedDefaultValue = "true",
          help = "Turns certificate checking on or off") boolean enabled) throws
                                                                          NoSuchAlgorithmException,
                                                                          KeyManagementException,
                                                                          IOException,
                                                                          KeyStoreException,
                                                                          CertificateException {
    this.validate = enabled;
    setCustomContext();
  }

  private void setCustomContext() throws NoSuchAlgorithmException,
                                         IOException,
                                         KeyStoreException,
                                         CertificateException,
                                         KeyManagementException {
    customContext = SSLContext.getInstance("TLS");
    customContext.init(getKeyManagers(), getTrustManagers(), null);
  }

}
