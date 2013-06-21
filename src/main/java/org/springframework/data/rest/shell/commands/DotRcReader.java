package org.springframework.data.rest.shell.commands;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.SmartLifecycle;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.stereotype.Component;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

/**
 * @author Jon Brisbin
 */
@Component
public class DotRcReader implements SmartLifecycle {

	@Autowired
	private JLineShellComponent shell;
	private boolean readDotRc = false;

	@Override
	public boolean isAutoStartup() {
		return true;
	}

	@Override
	public void stop(Runnable callback) {
		callback.run();
	}

	@Override
	public void start() {
		try {
			readDotRc();
		} catch (Exception e) {
			throw new IllegalStateException(e.getMessage(), e);
		}
	}

	@Override
	public void stop() {
		readDotRc = false;
	}

	@Override
	public boolean isRunning() {
		return readDotRc;
	}

	@Override
	public int getPhase() {
		return Integer.MAX_VALUE;
	}

	private void readDotRc() throws Exception {
		if (readDotRc) {
			return;
		}
		String homeDir = System.getenv("HOME");
		File restShellInitDir = new File(homeDir + File.separator + ".rest-shell");
		if (restShellInitDir.exists() && restShellInitDir.isDirectory()) {
			File[] files = restShellInitDir.listFiles();
			if (null == files) {
				return;
			}
			for (File f : files) {
				sourceFile(f);
			}
			readDotRc = true;
		}
	}

	private void sourceFile(File f) throws IOException {
		BufferedReader dotRc = new BufferedReader(new FileReader(f));
		String line;
		while (null != (line = dotRc.readLine())) {
			shell.executeCommand(line);
		}
	}

}

