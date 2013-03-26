package org.springframework.data.rest.shell;

import org.springframework.core.annotation.Order;
import org.springframework.shell.plugin.BannerProvider;
import org.springframework.stereotype.Component;

/**
 * Banner provider for the REST shell.
 *
 * @author Jon Brisbin
 */
@Component
@Order(Integer.MIN_VALUE)
public class RestShellBannerProvider implements BannerProvider {

	private static final String VERSION = "1.2.2";
	private static final String BANNER  = "\n ___ ___  __ _____  __  _  _     _ _  __    \n" +
			"| _ \\ __/' _/_   _/' _/| || |   / / | \\ \\   \n" +
			"| v / _|`._`. | | `._`.| >< |  / / /   > >  \n" +
			"|_|_\\___|___/ |_| |___/|_||_| |_/_/   /_/   \n";
	private static final String WELCOME = "Welcome to the REST shell. For assistance hit TAB or type \"help\".";

	@Override public String getBanner() {
		return BANNER + getVersion() + "\n";
	}

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
