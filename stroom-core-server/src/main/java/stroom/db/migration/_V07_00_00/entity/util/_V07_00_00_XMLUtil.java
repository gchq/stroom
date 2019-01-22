/*
 * Copyright 2016 Crown Copyright
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

package stroom.db.migration._V07_00_00.entity.util;

import org.xml.sax.ErrorHandler;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.XMLReader;
import stroom.entity.util.TransformerFactoryFactory;
import stroom.util.io.StreamUtil;
import stroom.util.xml.SAXParserFactoryFactory;

import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.ErrorListener;
import javax.xml.transform.OutputKeys;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.TransformerException;
import javax.xml.transform.sax.SAXTransformerFactory;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.io.Writer;

public final class _V07_00_00_XMLUtil {
    public static final SAXParserFactory PARSER_FACTORY;
    private static final String XML = "xml";
    private static final String UTF_8 = "UTF-8";
    private static final String NO = "no";
    private static final String YES = "yes";
    private static final String VERSION = "1.1";

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    private _V07_00_00_XMLUtil() {
        // Hidden constructor.
    }

    /**
     * Convert a java type into a xml name E.g. XMLType = "xmlType", String =
     * "string", StringBIG = "StringBIG"
     */
    public static String toXMLName(final String name) {
        final StringBuilder builder = new StringBuilder();
        boolean firstWord = true;
        for (int i = 0; i < name.length(); i++) {
            final char c = name.charAt(i);

            if (i == 0) {
                builder.append(Character.toLowerCase(c));
            } else {
                if (firstWord) {
                    if (i + 2 < name.length() && Character.isLowerCase(name.charAt(i + 2))) {
                        firstWord = false;
                    }
                    builder.append(Character.toLowerCase(c));
                } else {
                    builder.append(c);
                }
            }
        }

        return builder.toString();
    }

    public static String prettyPrintXML(final String xml) {
        final Reader reader = new StringReader(xml);
        final Writer writer = new StringWriter(1000);

        prettyPrintXML(reader, writer);

        return writer.toString();
    }

    public static void prettyPrintXML(final InputStream inputStream, final OutputStream outputStream) {
        final Reader reader = new InputStreamReader(inputStream, StreamUtil.DEFAULT_CHARSET);
        final Writer writer = new OutputStreamWriter(outputStream, StreamUtil.DEFAULT_CHARSET);

        prettyPrintXML(reader, writer);
    }

    private static void prettyPrintXML(final Reader reader, final Writer writer) {
        try {
            final TransformerHandler handler = createTransformerHandler(new FatalErrorListener(), true);
            handler.setResult(new StreamResult(writer));

            final SAXParser parser = PARSER_FACTORY.newSAXParser();
            final XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setErrorHandler(new FatalErrorHandler());
            xmlReader.setContentHandler(handler);
            xmlReader.parse(new InputSource(reader));

        } catch (final ParserConfigurationException | TransformerConfigurationException | IOException | SAXException e) {
            throw new RuntimeException(e);
        }
    }

    public static TransformerHandler createTransformerHandler(final boolean indentOutput)
            throws TransformerConfigurationException {
        return createTransformerHandler(null, indentOutput);
    }

    public static TransformerHandler createTransformerHandler(final ErrorListener errorListener,
                                                              final boolean indentOutput) throws TransformerConfigurationException {
        final SAXTransformerFactory stf = (SAXTransformerFactory) TransformerFactoryFactory.newInstance();
        if (errorListener != null) {
            stf.setErrorListener(errorListener);
        }

        final TransformerHandler th = stf.newTransformerHandler();
        final Transformer transformer = th.getTransformer();
        setCommonOutputProperties(transformer, indentOutput);

        if (errorListener != null) {
            transformer.setErrorListener(errorListener);
        }

        return th;
    }

    public static void setCommonOutputProperties(final Transformer transformer, final boolean indentOutput) {
        transformer.setOutputProperty(OutputKeys.METHOD, XML);
        transformer.setOutputProperty(OutputKeys.ENCODING, UTF_8);
        transformer.setOutputProperty(OutputKeys.VERSION, VERSION);
        if (indentOutput) {
            transformer.setOutputProperty(OutputKeys.INDENT, YES);
            transformer.setOutputProperty("{http://xml.apache.org/xslt}indent-amount", "3");
        } else {
            transformer.setOutputProperty(OutputKeys.INDENT, NO);
        }
    }

    private static class FatalErrorHandler implements ErrorHandler {

        @Override
        public void warning(final SAXParseException exception) throws SAXException {
        }

        @Override
        public void error(final SAXParseException exception) throws SAXException {
            throw exception;
        }

        @Override
        public void fatalError(final SAXParseException exception) throws SAXException {
            throw exception;
        }
    }

    private static class FatalErrorListener implements ErrorListener {
        @Override
        public void warning(final TransformerException exception) throws TransformerException {
        }

        @Override
        public void error(final TransformerException exception) throws TransformerException {
            throw exception;
        }

        @Override
        public void fatalError(final TransformerException exception) throws TransformerException {
            throw exception;
        }
    }

}
