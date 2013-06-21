package org.springframework.data.rest.shell;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.data.rest.shell.commands.ConfigurationCommands;
import org.springframework.shell.plugin.PromptProvider;
import org.springframework.stereotype.Component;

/**
 * Provides the prompt for the shell, which should take the form of:
 * <p/>
 * <code>baseUri:&gt;</code> (e.g. <code>http://localhost:8080:/ &gt;</code>)
 *
 * @author Jon Brisbin
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class RestShellPromptProvider implements PromptProvider {

	@Autowired
	private ConfigurationCommands configCmds;

	@Override
	public String getPrompt() {
		return configCmds.getBaseUri().toString() + ":" + "> ";
	}

	@Override
	public String name() {
		return configCmds.getBaseUri().toString();
	}

}
