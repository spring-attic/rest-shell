package org.springframework.data.rest.shell.commands;

import java.io.IOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.codehaus.jackson.map.ObjectMapper;
import org.codehaus.jackson.map.SerializationConfig;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.InitializingBean;
import org.springframework.context.ApplicationContext;
import org.springframework.context.expression.BeanFactoryResolver;
import org.springframework.context.expression.EnvironmentAccessor;
import org.springframework.context.expression.MapAccessor;
import org.springframework.context.support.ClassPathXmlApplicationContext;
import org.springframework.core.env.Environment;
import org.springframework.core.env.StandardEnvironment;
import org.springframework.expression.BeanResolver;
import org.springframework.expression.ParserContext;
import org.springframework.expression.PropertyAccessor;
import org.springframework.expression.common.TemplateParserContext;
import org.springframework.expression.spel.standard.SpelExpressionParser;
import org.springframework.expression.spel.support.ReflectivePropertyAccessor;
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

  private static final Logger LOG = LoggerFactory.getLogger(ContextCommands.class);

  private static final PropertyAccessor MAP_ACCESOR  = new MapAccessor();
  private static final PropertyAccessor BEAN_ACCESOR = new ReflectivePropertyAccessor();
  private static final PropertyAccessor ENV_ACCESSOR = new EnvironmentAccessor();
  private static final Environment      ENV          = new StandardEnvironment();

  final Map<String, Object> variables = new HashMap<String, Object>();
  StandardEvaluationContext evalCtx;

  private final SpelExpressionParser parser              = new SpelExpressionParser();
  private final ParserContext        parserContext       = new TemplateParserContext();
  private final ObjectMapper         mapper              = new ObjectMapper();
  private       BeanResolver         beanFactoryResolver = null;

  private ApplicationContext userAppCtx;

  {
    mapper.configure(SerializationConfig.Feature.INDENT_OUTPUT, true);
    mapper.configure(SerializationConfig.Feature.FAIL_ON_EMPTY_BEANS, false);
  }

  @Override public void afterPropertiesSet() throws Exception {
    userAppCtx = new ClassPathXmlApplicationContext("classpath*:META-INF/rest-shell/**/*.xml");
    beanFactoryResolver = new BeanFactoryResolver(userAppCtx);
    clear();
  }

  @CliAvailabilityIndicator({"var clear", "var set", "var get", "var list"})
  public boolean available() {
    return true;
  }

  @CliCommand(value = "var clear", help = "Clear this shell's variable context")
  public void clear() {
    if(variables.containsKey("links")) {
      evalCtx.removePropertyAccessor(((Links)variables.get("links")).getPropertyAccessor());
    }
    variables.clear();
    setup();
    if(LOG.isDebugEnabled()) {
      LOG.debug("Cleared context variables...");
    }
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
      Object val = eval(value);
      variables.put(name, val);
    } else if(value.startsWith("{")) {
      try {
        variables.put(name, mapper.readValue(value.replaceAll("\\\\", "").replaceAll("'", "\""), Map.class));
      } catch(IOException e) {
        throw new IllegalArgumentException(e);
      }
    } else if(value.startsWith("[")) {
      try {
        variables.put(name, mapper.readValue(value.replaceAll("\\\\", "").replaceAll("'", "\""), List.class));
      } catch(IOException e) {
        throw new IllegalArgumentException(e);
      }
    } else if(userAppCtx.containsBean(value)) {
      variables.put(name, userAppCtx.getBean(value));
    } else {
      variables.put(name, value);
    }

    if(LOG.isDebugEnabled()) {
      LOG.debug("Set context variable '" + name + "' to " + variables.get(name));
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
          help = "The expression to evaluate") String value) {

    if(null != name) {
      if(variables.containsKey(name)) {
        return variables.get(name);
      } else if(userAppCtx.containsBean(name)) {
        return userAppCtx.getBean(name);
      }
    }

    if(null != value && value.contains("#{")) {
      return eval(value);
    }

    return null;
  }

  @CliCommand(value = "var list", help = "List variables currently set in this shell's context")
  public String list() {
    try {
      variables.remove("env");
      return mapper.writeValueAsString(variables);
    } catch(IOException e) {
      throw new IllegalStateException(e);
    } finally {
      variables.put("env", ENV);
    }
  }

  public Object eval(String expr) {
    if(null == expr || !expr.contains("#{")) {
      return expr;
    }
    return parser.parseExpression(expr, parserContext).getValue(evalCtx);
  }

  public String evalAsString(String expr) {
    Object o = eval(expr);
    if(null != o) {
      return o.toString();
    }
    return null;
  }

  private void setup() {
    evalCtx = new StandardEvaluationContext(variables);
    List<PropertyAccessor> accessors = new ArrayList<PropertyAccessor>();
    accessors.add(MAP_ACCESOR);
    accessors.add(BEAN_ACCESOR);
    accessors.add(ENV_ACCESSOR);
    evalCtx.setPropertyAccessors(accessors);
    evalCtx.setBeanResolver(beanFactoryResolver);
    variables.put("env", ENV);
  }

}
