package org.springframework.data.rest.shell;

import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.shell.plugin.BannerProvider;
import org.springframework.shell.plugin.support.DefaultBannerProvider;
import org.springframework.stereotype.Component;

/**
 * Banner provider for the REST shell.
 *
 * @author Jon Brisbin
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestShellBannerProvider extends DefaultBannerProvider {

	private static final String VERSION = "1.2.2";
	private static final String WELCOME = "Welcome to the REST shell. For assistance hit TAB or type \"help\".";

	@Override public String getVersion() {
		return VERSION;
	}

	@Override public String getWelcomeMessage() {
		return WELCOME;
	}

	@Override public String name() {
		return "rest-shell";
	}

}
