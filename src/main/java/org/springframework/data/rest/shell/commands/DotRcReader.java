package org.springframework.data.rest.shell.commands;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileReader;
import java.io.IOException;

import org.springframework.beans.factory.InitializingBean;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.JLineShellComponent;
import org.springframework.shell.event.ShellStatus;
import org.springframework.shell.event.ShellStatusListener;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
public class DotRcReader implements InitializingBean, ShellStatusListener {

  @Autowired
  private JLineShellComponent shell;
  private boolean readDotRc = false;

  @Override public void afterPropertiesSet() throws Exception {
    shell.addShellStatusListener(this);
  }

  @Override public void onShellStatusChange(ShellStatus oldStatus, ShellStatus newStatus) {
    if(oldStatus.getStatus() == ShellStatus.Status.STARTED
        && newStatus.getStatus() == ShellStatus.Status.USER_INPUT) {
      if(!readDotRc) {
        try {
          readDotRc();
        } catch(Exception e) {
          throw new IllegalStateException(e.getMessage(), e);
        }
      }
    }
  }

  private void sourceFile(File f) throws IOException {
    BufferedReader dotRc = new BufferedReader(new FileReader(f));
    String line;
    while(null != (line = dotRc.readLine())) {
      shell.executeCommand(line);
    }
  }

  private void readDotRc() throws Exception {
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

}

