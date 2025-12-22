/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.xml.event;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.xml.event.simple.StartElement;
import stroom.util.CharBuffer;
import stroom.util.xml.XMLUtil;

import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.query.QueryResult;
import net.sf.saxon.s9api.Serializer.Property;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.xpath.XPathEvaluator;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.DefaultHandler;

import java.io.ByteArrayOutputStream;
import java.io.StringWriter;
import java.util.ArrayDeque;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

public final class EventListUtils {

    private static final Logger LOGGER = LoggerFactory.getLogger(EventListUtils.class);

    private static final String XML = "xml";
    private static final String UTF_8 = "UTF-8";
    private static final String NO = "no";
    private static final String XML_VERSION = "1.1";

    private EventListUtils() {
        // Utility class so private constructor.
    }

    /**
     * Creates an XML string from the event stack captured for the matched
     * record.
     */
    public static String getXML(final List<Event> events) {
        try {
            final TransformerHandler th = XMLUtil.createTransformerHandler(false);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            th.setResult(new StreamResult(outputStream));

            // Unwind all buffered events into the handler.
            final Deque<Event> openElements = new ArrayDeque<>();
            for (final Event event : events) {
                if (event.isStartElement()) {
                    openElements.push(event);
                } else if (event.isEndElement()) {
                    openElements.pop();
                }

                if (event.isEndDocument()) {
                    while (!openElements.isEmpty()) {
                        final StartElement startElement = (StartElement) openElements.pop();
                        th.endElement(startElement.getUri(), startElement.getLocalName(), startElement.getQName());
                    }

                    event.fire(th);
                } else {
                    event.fire(th);
                }
            }

            return outputStream.toString();

        } catch (final SAXException | TransformerConfigurationException | RuntimeException e) {
            throw ProcessException.create(e.getMessage());
        }
    }

    /**
     * Creates an XML string from the event stack captured for the matched
     * record.
     */
    public static String getXML(final NodeInfo nodeInfo) {
        try {
            final StringWriter sw = new StringWriter();
            final StreamResult sr = new StreamResult(sw);
            QueryResult.serialize(nodeInfo, sr, getOutputProperties());
            return sw.toString();
        } catch (final XPathException | RuntimeException e) {
            throw ProcessException.create(e.getMessage());
        }
    }

    /**
     * Creates an text string from the content events in the event stack
     * captured for the matched record.
     */
    public static String getText(final NodeInfo nodeInfo) {
        try {
            final String path = "//text()";
            final XPathEvaluator xPathEvaluator = new XPathEvaluator(nodeInfo.getConfiguration());

            final XPathExpression xPathExpression = xPathEvaluator.compile(path);
            return (String) xPathExpression.evaluate(nodeInfo, XPathConstants.STRING);

        } catch (final XPathExpressionException | RuntimeException e) {
            throw ProcessException.create(e.getMessage());
        }
    }

    /**
     * Creates an text string from the content events in the event stack
     * captured for the matched record.
     */
    public static String getText(final List<Event> events) {
        final CharBuffer cb = new CharBuffer(100);

        // Unwind all buffered events into the handler.
        for (final Event event : events) {
            if (event.isCharacters()) {
                cb.append(event.toString());
            }
        }

        final String text = cb.toString();
        cb.clear();

        return text;
    }

    /**
     * Creates an XML string from the event stack captured for the matched
     * record.
     */
    public static String getXML(final EventList events) {
        try {
            final TransformerHandler th = XMLUtil.createTransformerHandler(false);
            final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
            th.setResult(new StreamResult(outputStream));

            events.fire(th);

            return outputStream.toString();

        } catch (final SAXException | TransformerConfigurationException | RuntimeException e) {
            throw ProcessException.create(e.getMessage());
        }
    }

    /**
     * Create a Properties object holding the defined serialization properties.
     * This will be in the same format as JAXP interfaces such as
     * {@link javax.xml.transform.Transformer#getOutputProperties()}
     *
     * @return a newly-constructed Properties object holding the declared
     * serialization properties
     */
    private static Properties getOutputProperties() {
        final Map<Property, String> properties = new HashMap<>(10);
        properties.put(Property.METHOD, XML);
        properties.put(Property.ENCODING, UTF_8);
        properties.put(Property.INDENT, NO);
        properties.put(Property.VERSION, XML_VERSION);

        final Properties props = new Properties();
        for (final Property p : properties.keySet()) {
            final String value = properties.get(p);
            props.setProperty(p.toString(), value);
        }
        return props;
    }

    /**
     * Creates an text string from the content events in the event stack
     * captured for the matched record.
     */
    public static String getText(final EventList events) {
        final CharBuffer cb = new CharBuffer(100);
        try {
            events.fire(new DefaultHandler() {
                @Override
                public void characters(final char[] ch, final int start, final int length) {
                    cb.append(ch, start, length);
                }
            });
        } catch (final SAXException e) {
            LOGGER.error(e.getMessage(), e);
        }

        return cb.toString();
    }
}
