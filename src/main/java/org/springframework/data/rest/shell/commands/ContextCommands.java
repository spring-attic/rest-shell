package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.springframework.beans.BeansException;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.StandardEvaluationContext;
import org.springframework.shell.core.CommandMarker;
import org.springframework.shell.core.annotation.CliAvailabilityIndicator;
import org.springframework.shell.core.annotation.CliCommand;
import org.springframework.shell.core.annotation.CliOption;
import org.springframework.stereotype.Component;

/**
 * @author Jon Brisbin
 */
@Component
public class ContextCommands implements CommandMarker, ApplicationContextAware {

  final         Map<String, Object>  variables = new HashMap<>();
  private final SpelExpressionParser parser    = new SpelExpressionParser();
  private ApplicationContext appCtx;
  private ObjectMapper mapper = new ObjectMapper();

  {
    mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
    mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
  }

  @Override public void setApplicationContext(ApplicationContext appCtx) throws BeansException {
    this.appCtx = appCtx;
    Map<String, Object> beans = new HashMap<>();
    for(String beanName : appCtx.getBeanDefinitionNames()) {
      beans.put(beanName, appCtx.getBean(beanName));
    }
    variables.put("beans", beans);
  }

  @CliAvailabilityIndicator({"var clear", "var set", "var get", "var list"})
  public boolean available() {
    return true;
  }

  @CliCommand(value = "var clear", help = "Clear this shell's variable context")
  public void clear() {
    variables.clear();
  }

  @CliCommand(value = "var set", help = "Set a variable in this shell's context")
  public void set(
      @CliOption(
          key = "name",
          mandatory = true,
          help = "The name of the variable to set") String name,
      @CliOption(
          key = "value",
          mandatory = false,
          help = "The value of the variable to set") String value) {

    if(null == value) {
      variables.remove(name);
      return;
    }

    if(value.startsWith("#")) {
      Object val = getValue(value);
      variables.put(name, val);
    } else if(value.startsWith("{")) {
      try {
        variables.put(name, mapper.readValue(value.replaceAll("\\\\", ""), Map.class));
      } catch(IOException e) {
        throw new IllegalArgumentException(e);
      }
    } else if(value.startsWith("[")) {
      try {
        variables.put(name, mapper.readValue(value.replaceAll("\\\\", ""), List.class));
      } catch(IOException e) {
        throw new IllegalArgumentException(e);
      }
    } else if(appCtx.containsBean(value)) {
      variables.put(name, appCtx.getBean(value));
    } else {
      variables.put(name, value);
    }

  }

  @CliCommand(value = "var get", help = "Get a variable in this shell's context")
  public Object get(
      @CliOption(
          key = "name",
          mandatory = false,
          help = "The name of the variable to get") String name,
      @CliOption(
          key = "value",
          mandatory = false,
          help = "The value of the variable to set") String value) {

    if(null != name) {
      if(variables.containsKey(name)) {
        return variables.get(name);
      } else if(appCtx.containsBean(name)) {
        return appCtx.getBean(name);
      }
    } else {
      if(null != value && value.startsWith("#")) {
        return getValue(value);
      }
    }

    return null;
  }

  @CliCommand(value = "var list", help = "List variables currently set in this shell's context")
  public String list() {
    try {
      return mapper.writeValueAsString(variables);
    } catch(IOException e) {
      throw new IllegalStateException(e);
    }
  }

  public Object getValue(String expr) {
    StandardEvaluationContext evalCtx = new StandardEvaluationContext();
    evalCtx.setVariables(variables);
    return parser.parseExpression(expr).getValue(evalCtx);
  }

}