package org.springframework.data.rest.shell;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.annotation.Order;
import org.springframework.data.rest.shell.commands.ConfigurationCommands;
import org.springframework.data.rest.shell.commands.DotRcReader;
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
@Order(Integer.MIN_VALUE)
public class RestShellPromptProvider implements PromptProvider {

	@Autowired
	private ConfigurationCommands configCmds;
	@Autowired
	private DotRcReader           dotRcReader;
	private boolean readDotRc = false;

	@Override public String getPrompt() {
		if(!readDotRc) {
			try {
				dotRcReader.readDotRc();
			} catch(Exception e) {
				throw new IllegalStateException(e);
			}
		}
		return configCmds.getBaseUri().toString() + ":" + "> ";
	}

	@Override public String name() {
		return configCmds.getBaseUri().toString();
	}

}
