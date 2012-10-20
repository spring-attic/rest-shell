package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.expression.AccessException;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.EvaluationContext;
import org.springframework.expression.ParserContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.TypedValue;
import org.springframework.expression.common.TemplateParserContext;
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
public class ContextCommands implements CommandMarker, InitializingBean {

  private static final PropertyAccessor LINK_ACCESSOR = new LinkPropertyAccessor();
  private static final PropertyAccessor MAP_ACCESOR   = new MapAccessor();
  private static final PropertyAccessor ENV_ACCESSOR  = new EnvironmentAccessor();
  private static final Environment      ENV           = new StandardEnvironment();

  final Map<String, Object> variables = new HashMap<>();

  private final SpelExpressionParser parser              = new SpelExpressionParser();
  private final ParserContext        parserContext       = new TemplateParserContext();
  private final ObjectMapper         mapper              = new ObjectMapper();
  private       BeanResolver         beanFactoryResolver = null;

  private ApplicationContext userAppCtx;

  {
    mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
    mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);

    variables.put("env", ENV);
  }

  @Override public void afterPropertiesSet() throws Exception {
    userAppCtx = new ClassPathXmlApplicationContext("classpath*:META-INF/rest-shell/**/*.xml");
    beanFactoryResolver = new BeanFactoryResolver(userAppCtx);
  }

  @CliAvailabilityIndicator({"var clear", "var set", "var get", "var list"})
  public boolean available() {
    return true;
  }

  @CliCommand(value = "var clear", help = "Clear this shell's variable context")
  public void clear() {
    variables.clear();
    variables.put("env", ENV);
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

    if(value.contains("#{")) {
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
    } else if(userAppCtx.containsBean(value)) {
      variables.put(name, userAppCtx.getBean(value));
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
      } else if(userAppCtx.containsBean(name)) {
        return userAppCtx.getBean(name);
      }
    } else {
      if(null != value && value.contains("#{")) {
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
    StandardEvaluationContext evalCtx = new StandardEvaluationContext(variables);
    evalCtx.setPropertyAccessors(Arrays.<PropertyAccessor>asList(
        LINK_ACCESSOR,
        ENV_ACCESSOR,
        MAP_ACCESOR
    ));
    evalCtx.setBeanResolver(beanFactoryResolver);
    evalCtx.setVariable("env", ENV);

    return parser.parseExpression(expr, parserContext).getValue(evalCtx);
  }

  private static class LinkPropertyAccessor implements PropertyAccessor {
    @Override public Class[] getSpecificTargetClasses() {
      return new Class[]{ArrayList.class};
    }

    @Override public boolean canRead(EvaluationContext context,
                                     Object target,
                                     String name) throws AccessException {
      return (target instanceof List
          && ((List)target).size() > 0
          && ((List)target).get(0) instanceof Map
          && ((Map)((List)target).get(0)).containsKey("rel"));
    }

    @SuppressWarnings({"unchecked"})
    @Override public TypedValue read(EvaluationContext context,
                                     Object target,
                                     String name) throws AccessException {

      List<Map<String, String>> links = (List<Map<String, String>>)target;
      for(Map<String, String> link : links) {
        if(link.get("rel").equals(name)) {
          return new TypedValue(link.get("href"));
        }
      }

      return null;
    }

    @Override public boolean canWrite(EvaluationContext context,
                                      Object target,
                                      String name) throws AccessException {
      return false;
    }

    @SuppressWarnings({"unchecked"})
    @Override public void write(EvaluationContext context,
                                Object target,
                                String name,
                                Object newValue) throws AccessException {
      throw new AccessException("Cannot update the value of a link using the shell.");
    }
  }

}