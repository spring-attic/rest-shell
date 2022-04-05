package org.springframework.data.rest.shell.formatter;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.sax.SAXSource;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.stream.StreamResult;

import org.xml.sax.InputSource;

public class XmlFormatter extends FormatterSupport {
  private final List<String> supportedContentTypes = Arrays.asList("xml");

  @Override
  public Collection<String> getSupportedList() {
    return supportedContentTypes;
  }

  @Override
  public String format(String nonFormattedString) {
    try {
      Transformer serializer = SAXTransformerFactory.newInstance().newTransformer();
      serializer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, "yes");
      serializer.setOutputProperty(OutputKeys.INDENT, "yes");
      serializer.setOutputProperty("{https://xml.apache.org/xslt}indent-amount", "2");
      Source xmlSource = new SAXSource(new InputSource(new ByteArrayInputStream(nonFormattedString.getBytes())));
      StreamResult res = new StreamResult(new ByteArrayOutputStream());

      serializer.transform(xmlSource, res);

      return new String(((ByteArrayOutputStream) res.getOutputStream()).toByteArray());
    } catch (Exception e) {
      return nonFormattedString;
    }
  }
}
