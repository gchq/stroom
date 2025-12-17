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

package stroom.importexport.impl;

import stroom.docref.DocRef;
import stroom.util.date.DateUtil;
import stroom.util.xml.TransformerFactoryFactory;
import stroom.util.xml.XMLUtil;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;
import org.xml.sax.ext.Attributes2Impl;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Date;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.TreeMap;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

public class Config {

    private static final Attributes2Impl BLANK_ATTRIBUTES = new Attributes2Impl();
    private static final String YES = "yes";

    private final Map<String, List<Object>> propertyValues = new TreeMap<>();

    public void read(final Reader reader) throws IOException {
        try {
            final SAXParser parser = XMLUtil.PARSER_FACTORY.newSAXParser();
            final XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setContentHandler(new ImportContentHandler() {
                @Override
                void handleAttribute(final String property, final Object value) {
                    propertyValues.computeIfAbsent(property, k -> new ArrayList<>()).add(value);
                }
            });
            xmlReader.parse(new InputSource(reader));
        } catch (final IOException e) {
            throw e;
        } catch (final ParserConfigurationException | SAXException | RuntimeException e) {
            throw new IOException(e.getMessage());
        }
    }

    public void write(final Writer writer, final String entityType) throws IOException {
        try {
            final SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactoryFactory.newInstance();
            final TransformerHandler th = stf.newTransformerHandler();
            final Transformer transformer = th.getTransformer();

            writer.write("<?xml version=\"1.1\" encoding=\"UTF-8\"?>\n");
            XMLUtil.setCommonOutputProperties(transformer, true);
            transformer.setOutputProperty(OutputKeys.OMIT_XML_DECLARATION, YES);

            th.setResult(new StreamResult(writer));
            th.startDocument();

            final String xmlName = XMLUtil.toXMLName(entityType);

            writeStartElement(th, xmlName);

            for (final Entry<String, List<Object>> entry : propertyValues.entrySet()) {
                final String propertyName = entry.getKey();
                final List<Object> list = entry.getValue();

                for (final Object value : list) {
                    if (value != null) {
                        if (value instanceof Collection) {
                            for (final Object valueItem : (Collection<?>) value) {
                                writeStartElement(th, propertyName);
                                writeContent(th, String.valueOf(valueItem));
                                writeEndElement(th, propertyName);
                            }
                        } else if (value instanceof DocRef) {
                            final DocRef docRef = (DocRef) value;

                            writeStartElement(th, propertyName);

                            writeStartElement(th, "doc");
                            writeStartElement(th, "type");
                            writeContent(th, docRef.getType());
                            writeEndElement(th, "type");
                            writeStartElement(th, "uuid");
                            writeContent(th, docRef.getUuid());
                            writeEndElement(th, "uuid");
                            writeStartElement(th, "name");
                            writeContent(th, docRef.getName());
                            writeEndElement(th, "name");
                            writeEndElement(th, "doc");

                            writeEndElement(th, propertyName);
                        } else if (value instanceof Date) {
                            writeStartElement(th, propertyName);
                            writeContent(th, DateUtil.createNormalDateTimeString(((Date) value).getTime()));
                            writeEndElement(th, propertyName);

                        } else {
                            writeStartElement(th, propertyName);
                            writeContent(th, String.valueOf(value));
                            writeEndElement(th, propertyName);
                        }
                    }
                }
            }

            writeEndElement(th, xmlName);
            th.endDocument();

            writer.close();

        } catch (final IOException e) {
            throw e;
        } catch (final TransformerConfigurationException | SAXException | RuntimeException e) {
            throw new IOException(e.getMessage());
        }
    }

    private void writeStartElement(final TransformerHandler th, final String name) throws SAXException {
        th.startElement("", name, name, BLANK_ATTRIBUTES);
    }

    private void writeEndElement(final TransformerHandler th, final String name) throws SAXException {
        th.endElement("", name, name);
    }

    private void writeContent(final TransformerHandler th, final String content) throws SAXException {
        th.characters(content.toCharArray(), 0, content.length());
    }

    public String getString(final String key) {
        final List<Object> list = propertyValues.get(key);
        if (list == null || list.size() != 1) {
            return null;
        }

        return list.get(0).toString();
    }

    public List<Object> get(final String key) {
        return propertyValues.get(key);
    }

    public void add(final String key, final Object object) {
        propertyValues.computeIfAbsent(key, k -> new ArrayList<>()).add(object);
    }

    public Iterable<String> getProperties() {
        return propertyValues.keySet();
    }

    public boolean hasProperty(final String property) {
        return propertyValues.containsKey(property);
    }
}
