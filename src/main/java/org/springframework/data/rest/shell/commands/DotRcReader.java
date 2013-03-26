package org.springframework.data.rest.shell.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
public class DotRcReader {

	@Autowired
	private JLineShellComponent shell;
	private boolean readDotRc = false;

	public void readDotRc() throws Exception {
		if(readDotRc) {
			return;
		}
		String homeDir = System.getenv("HOME");
		File restShellInitDir = new File(homeDir + File.separator + ".rest-shell");
		if(restShellInitDir.exists() && restShellInitDir.isDirectory()) {
			File[] files = restShellInitDir.listFiles();
			if(null == files) {
				return;
			}
			for(File f : files) {
				sourceFile(f);
			}
			readDotRc = true;
		}
	}

	private void sourceFile(File f) throws IOException {
		BufferedReader dotRc = new BufferedReader(new FileReader(f));
		String line;
		while(null != (line = dotRc.readLine())) {
			shell.executeCommand(line);
		}
	}

}

