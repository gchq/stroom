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

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.util.test.StroomUnitTest;
import stroom.util.xml.SAXParserFactoryFactory;
import org.apache.commons.io.output.ByteArrayOutputStream;
import org.junit.Assert;
import org.junit.Test;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import stroom.entity.server.util.XMLUtil;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.io.FileUtil;

public class TestXMLWriter extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestXMLWriter.class);

    public static final SAXParserFactory PARSER_FACTORY;

	static {
		PARSER_FACTORY = SAXParserFactoryFactory.newInstance();
		PARSER_FACTORY.setNamespaceAware(true);
	}

    @Test
    public void test() {
        final File testInputDir = StroomProcessTestFileUtil.getTestResourcesDir();
        final File testOutputDir = new File(StroomProcessTestFileUtil.getTestOutputDir(), "TestXMLWriter");

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

    private void processDir(final File inputDir, final File outputDir) {
        for (final File file : inputDir.listFiles()) {
            if (file.isDirectory()) {
                final File newOutputDir = new File(outputDir, file.getName());
                processDir(file, newOutputDir);
            } else if (file.getName().endsWith(".xml")) {
                processXML(file, outputDir);
            }
        }
    }

    private void processXML(final File inputFile, final File outputDir) {
        LOGGER.info("Processing file: " + inputFile.getAbsolutePath());
        FileUtil.mkdirs(outputDir);

        // First create SAXON formatted output.
        final File saxonOutput = new File(outputDir,
                inputFile.getName().substring(0, inputFile.getName().lastIndexOf('.')) + ".saxon.xml");
        try {
            // Pretty print with SAXON.
            XMLUtil.prettyPrintXML(new BufferedInputStream(new FileInputStream(inputFile)),
                    new BufferedOutputStream(new FileOutputStream(saxonOutput)));

            try {
                // Pretty print with XMLWriter.
                final File xwOutput = new File(outputDir,
                        inputFile.getName().substring(0, inputFile.getName().lastIndexOf('.')) + ".xw.xml");
                final FileWriter fw = new FileWriter(xwOutput);
                final BufferedWriter bw = new BufferedWriter(fw);
                final XMLWriter xmlWriter = new XMLWriter(bw);
                xmlWriter.setOutputXMLDecl(true);
                xmlWriter.setIndentation(3);

                final SAXParser saxParser = PARSER_FACTORY.newSAXParser();
                final XMLReader xmlReader = saxParser.getXMLReader();
                xmlReader.setContentHandler(xmlWriter);
                xmlReader.parse(new InputSource(new BufferedReader(new FileReader(inputFile))));

                bw.close();

            } catch (final Throwable t) {
                Assert.fail(t.getMessage());
            }
        } catch (final Throwable t) {
            // Ignore...
        }
    }
}
