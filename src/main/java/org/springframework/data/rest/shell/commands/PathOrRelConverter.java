package org.springframework.data.rest.shell.commands;

import java.util.List;
import java.util.Map;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.shell.core.Completion;
import org.springframework.shell.core.Converter;
import org.springframework.shell.core.MethodTarget;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
public class PathOrRelConverter implements Converter<PathOrRel> {


  @Autowired
  private DiscoveryCommands discoveryCmds;
  @Autowired
  private ContextCommands   contextCmds;

  @Override public boolean supports(Class<?> type, String optionContext) {
    return PathOrRel.class.isAssignableFrom(type);
  }

  @Override public PathOrRel convertFromText(String value,
                                             Class<?> targetType,
                                             String optionContext) {
    String relOrPath = contextCmds.evalAsString(value);
    if(discoveryCmds.getResources().containsKey(relOrPath)) {
      return new PathOrRel(discoveryCmds.getResources().get(relOrPath));
    } else {
      return new PathOrRel(relOrPath);
    }
  }

  @Override
  public boolean getAllPossibleValues(List<Completion> completions,
                                      Class<?> targetType,
                                      String existingData,
                                      String optionContext,
                                      MethodTarget target) {
    for(Map.Entry<String, String> entry : discoveryCmds.getResources().entrySet()) {
      if(entry.getKey().startsWith(existingData)) {
        completions.add(new Completion(entry.getKey()));
      }
    }
    return true;
  }

}
