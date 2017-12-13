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

package stroom.xml.util;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import stroom.entity.server.util.XMLUtil;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.test.StroomUnitTest;
import stroom.util.xml.SAXParserFactoryFactory;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

public class TestXMLWriter extends StroomUnitTest {
    public static final SAXParserFactory PARSER_FACTORY;
    private static final Logger LOGGER = LoggerFactory.getLogger(TestXMLWriter.class);

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
        PARSER_FACTORY.setNamespaceAware(true);
    }

    @Test
    public void test() {
        final Path testInputDir = StroomPipelineTestFileUtil.getTestResourcesDir();
        final Path testOutputDir = StroomPipelineTestFileUtil.getTestOutputDir().resolve("TestXMLWriter");

        processDir(testInputDir, testOutputDir);
    }

    @Test
    public void testPrettyPrintErrorHandler() {
        boolean error = false;
        try {
            XMLUtil.prettyPrintXML(new ByteArrayInputStream("Test".getBytes()), new ByteArrayOutputStream());
        } catch (final Exception e) {
            error = true;
        }
        Assert.assertTrue(error);
    }

    private void processDir(final Path inputDir, final Path outputDir) {
        try (final Stream<Path> stream = Files.list(inputDir)) {
            stream.forEach(p -> {
                if (Files.isDirectory(p)) {
                    final Path newOutputDir = outputDir.resolve(p.getFileName().toString());
                    processDir(p, newOutputDir);
                } else if (p.getFileName().toString().endsWith(".xml")) {
                    processXML(p, outputDir);
                }
            });
        } catch (final IOException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private void processXML(final Path inputFile, final Path outputDir) {
        LOGGER.info("Processing file: " + FileUtil.getCanonicalPath(inputFile));
        FileUtil.mkdirs(outputDir);

        // First create SAXON formatted output.
        final String fileName = inputFile.getFileName().toString();
        final Path saxonOutput = outputDir.resolve(fileName.substring(0, fileName.lastIndexOf('.')) + ".saxon.xml");
        try {
            // Pretty print with SAXON.
            XMLUtil.prettyPrintXML(new BufferedInputStream(Files.newInputStream(inputFile)),
                    new BufferedOutputStream(Files.newOutputStream(saxonOutput)));

            try {
                // Pretty print with XMLWriter.
                final Path xwOutput = outputDir.resolve(fileName.substring(0, fileName.lastIndexOf('.')) + ".xw.xml");
                final BufferedWriter bw = Files.newBufferedWriter(xwOutput);
                final XMLWriter xmlWriter = new XMLWriter(bw);
                xmlWriter.setOutputXMLDecl(true);
                xmlWriter.setIndentation(3);

                final SAXParser saxParser = PARSER_FACTORY.newSAXParser();
                final XMLReader xmlReader = saxParser.getXMLReader();
                xmlReader.setContentHandler(xmlWriter);
                xmlReader.parse(new InputSource(Files.newBufferedReader(inputFile)));

                bw.close();

            } catch (final Throwable t) {
                Assert.fail(t.getMessage());
            }
        } catch (final Throwable t) {
            // Ignore...
        }
    }
}
