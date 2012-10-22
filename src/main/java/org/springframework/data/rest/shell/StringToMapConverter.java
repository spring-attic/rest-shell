package org.springframework.data.rest.shell;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.JsonParser;
import org.codehaus.jackson.map.ObjectMapper;
import org.springframework.shell.core.Completion;
import org.springframework.shell.core.Converter;
import org.springframework.shell.core.MethodTarget;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
public class StringToMapConverter implements Converter<Map> {

  private ObjectMapper mapper = new ObjectMapper();

  {
    mapper.configure(JsonParser.Feature.ALLOW_UNQUOTED_FIELD_NAMES, true);
  }

  @Override public boolean supports(Class<?> type,
                                    String optionContext) {
    return Map.class.isAssignableFrom(type);
  }

  @Override public Map convertFromText(String value,
                                       Class<?> targetType,
                                       String optionContext) {
    try {
      return (Map)mapper.readValue(value.replaceAll("\\\\", "").replaceAll("'", "\""), targetType);
    } catch(IOException e) {
      throw new IllegalArgumentException(e);
    }
  }

  @Override
  public boolean getAllPossibleValues(List<Completion> completions,
                                      Class<?> targetType,
                                      String existingData,
                                      String optionContext,
                                      MethodTarget target) {
    System.out
          .println("getAllPossibleValues: " + completions + ", ex: " + existingData + ", ctx: " + optionContext + ", tgt: " + target);
    return false;
  }

}
