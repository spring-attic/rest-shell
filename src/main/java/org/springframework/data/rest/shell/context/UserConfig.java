package org.springframework.data.rest.shell.context;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.context.annotation.Configuration;
import org.springframework.context.annotation.ImportResource;

/**
 * @author Jon Brisbin
 */
@Configuration
@ImportResource("classpath*:META-INF/rest-shell/**/*.xml")
@Qualifier("restShellUserConfig")
public class UserConfig {
}
