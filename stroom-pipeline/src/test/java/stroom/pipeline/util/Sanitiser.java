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

package stroom.pipeline.util;

import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.filter.SafeXMLFilter;
import stroom.pipeline.filter.XMLFilterContentHandlerAdaptor;
import stroom.util.io.FileUtil;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ElementId;
import stroom.util.xml.SAXParserFactoryFactory;
import stroom.util.xml.XMLUtil;

import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

public class Sanitiser {

    private static final SAXParserFactory PARSER_FACTORY;

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    public Sanitiser(final Path in, final Path out)
            throws IOException, TransformerConfigurationException, SAXException {
        process(in, out);
    }

    public static void main(final String[] args) throws IOException, TransformerConfigurationException, SAXException {
        if (args.length != 2) {
            System.out.println("Bad arguments - provide input and output files.");
        }

        new Sanitiser(Paths.get(args[0]), Paths.get(args[1]));
    }

    private void process(final Path in, final Path out)
            throws IOException, TransformerConfigurationException, SAXException {
        try (final Reader reader = Files.newBufferedReader(in, StreamUtil.DEFAULT_CHARSET);
                final Writer writer = Files.newBufferedWriter(out, StreamUtil.DEFAULT_CHARSET)) {
            final TransformerHandler th = XMLUtil.createTransformerHandler(true);
            th.setResult(new StreamResult(writer));

            final SAXParser parser;
            try {
                parser = PARSER_FACTORY.newSAXParser();
            } catch (final ParserConfigurationException e) {
                throw ProcessException.wrap(e);
            }

            final SafeXMLFilter filter = new SafeXMLFilter();
            filter.setContentHandler(new XMLFilterContentHandlerAdaptor(th));

            final LocationFactory locationFactory = new DefaultLocationFactory();

            final XMLReader xmlReader = parser.getXMLReader();
            xmlReader.setContentHandler(filter);
            xmlReader.setErrorHandler(new ErrorHandlerAdaptor(
                    new ElementId("XMLReader"), locationFactory, new FatalErrorReceiver()));
            xmlReader.parse(new InputSource(reader));

        } catch (final RuntimeException e) {
            System.out.println("Error processing file: " + FileUtil.getCanonicalPath(in));
            e.printStackTrace();
        }
    }
}
