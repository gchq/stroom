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

package stroom.pipeline.xml.util;

import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.AbstractFileVisitor;
import stroom.util.io.FileUtil;
import stroom.util.logging.LogUtil;
import stroom.util.xml.SAXParserFactoryFactory;
import stroom.util.xml.XMLUtil;

import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.file.FileVisitOption;
import java.nio.file.FileVisitResult;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.BasicFileAttributes;
import java.util.EnumSet;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestXMLWriter extends StroomUnitTest {

    private static final SAXParserFactory PARSER_FACTORY;
    private static final Logger LOGGER = LoggerFactory.getLogger(TestXMLWriter.class);

    static {
        PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
    }

    @Test
    void test() {
        final Path testInputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve("TestXMLWriter");
        final Path testOutputDir = StroomPipelineTestFileUtil.getTestOutputDir().resolve("TestXMLWriter");

        processDir(testInputDir, testOutputDir);
    }

    @Test
    void testPrettyPrintErrorHandler() {
        boolean error = false;
        try {
            XMLUtil.prettyPrintXML(new ByteArrayInputStream("Test".getBytes()), new ByteArrayOutputStream());
        } catch (final RuntimeException e) {
            error = true;
        }
        assertThat(error).isTrue();
    }

    private void processDir(final Path inputDir, final Path outputDir) {
        try {
            Files.walkFileTree(inputDir,
                    EnumSet.of(FileVisitOption.FOLLOW_LINKS),
                    Integer.MAX_VALUE,
                    new AbstractFileVisitor() {
                        @Override
                        public FileVisitResult visitFile(final Path file, final BasicFileAttributes attrs) {
                            if (file.getFileName().toString().endsWith(".xml")) {
                                processXML(file, outputDir);
                            }
                            return super.visitFile(file, attrs);
                        }
                    });
        } catch (final IOException e) {
            LOGGER.error(e.getMessage(), e);
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

            } catch (final IOException | SAXException | ParserConfigurationException | RuntimeException e) {
                LOGGER.error(e.getMessage(), e);
                fail(LogUtil.message("Error processing file {}. {}", inputFile, e.getMessage()), e);
            }
        } catch (final IOException | RuntimeException e) {
            // Ignore...
        }
    }
}
