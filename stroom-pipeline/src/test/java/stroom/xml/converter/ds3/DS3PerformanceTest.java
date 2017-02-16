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

package stroom.xml.converter.ds3;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import org.junit.Assert;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import stroom.entity.server.util.XMLUtil;
import stroom.pipeline.server.DefaultLocationFactory;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.test.StroomProcessTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.xml.converter.SchemaFilterFactory;

public class DS3PerformanceTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(DS3PerformanceTest.class);

    private static final int ITERATIONS = 1;
    private static final int INPUT_LINES = 10000000;

    private final SchemaFilterFactory schemaFilterFactory = new SchemaFilterFactory();
    private DS3ParserFactory ds3ParserFactory;

    public void testCSVWithHeading() throws Exception {
        process("CSVWithHeading");
    }

    public void testCSVWithHeadingSplit() throws Exception {
        process("CSVWithHeadingSplit");
    }

    private void process(final String stem) throws Exception {
        LOGGER.info("Testing: " + stem);

        final File testDir = new File(getDS3TestDir(), "tmp");
        FileUtil.mkdirs(testDir);

        final File ds3Config = new File(getDS3TestDir(), stem + ".ds2");
        final File input = new File(testDir, stem + ".in");
        final File output = new File(testDir, stem + ".out");

        // Create an input file.
        createInput(input);

        final XMLReader ds3Reader = createDS3Reader(ds3Config);

        System.out.println("Testing Data Splitter v3");
        final long ds3Elapsed = process(input, output, ds3Reader);

        System.out.println("DS3 Compilation time " + ds3ParserFactory.getComp());
        System.out.println("DS3 Elapsed Time = " + ds3Elapsed);
    }

    private long process(final File input, final File output, final XMLReader parser) throws Exception {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        threadMXBean.setThreadContentionMonitoringEnabled(true);
        threadMXBean.setThreadCpuTimeEnabled(true);

        final long cpuTimeStart = threadMXBean.getCurrentThreadCpuTime();

        // Create output writers.
        final OutputStream os = createWriter(parser, output);

        for (int i = 0; i < ITERATIONS; i++) {
            parser.parse(new InputSource(new BufferedReader(new FileReader(input))));
        }

        os.flush();
        os.close();

        final long cpuTimeEnd = threadMXBean.getCurrentThreadCpuTime();
        return cpuTimeEnd - cpuTimeStart;
    }

    private void createInput(final File input) throws IOException {
        final Writer writer = new BufferedWriter(new FileWriter(input));
        writer.write("Time,Action,User,File\n");
        for (int i = 1; i <= INPUT_LINES; i++) {
            writer.write("01/01/2009:00:00:01" + i + ",OPEN" + i + ",userone" + i
                    + ",D:\\TranslationKit\\example\\VerySimple\\OpenFileEvents" + i + ".txt\n");
        }
        writer.flush();
        writer.close();
    }

    private OutputStream createWriter(final XMLReader reader, final File tmp) throws Exception {
        // Create an output writer.
        final TransformerHandler th = XMLUtil.createTransformerHandler(true);
        final OutputStream os = new BufferedOutputStream(new FileOutputStream(tmp));
        th.setResult(new StreamResult(os));

        reader.setContentHandler(th);

        return os;
    }

    private File getDS3TestDir() {
        // Get the testing directory.
        final File testDataDir = StroomProcessTestFileUtil.getTestResourcesDir();
        final File testDir = new File(testDataDir, "TestDS3");
        return testDir;
    }

    private XMLReader createDS3Reader(final File config) throws Exception {
        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(errorReceiver);

        ds3ParserFactory = new DS3ParserFactory();
        ds3ParserFactory.setSchemaFilter(
                schemaFilterFactory.getSchemaFilter(DS3ParserFactory.NAMESPACE_URI, errorReceiverProxy));

        final LocationFactory locationFactory = new DefaultLocationFactory();
        ds3ParserFactory.configure(new BufferedReader(new FileReader(config)),
                new ErrorHandlerAdaptor("DS3ParserFactory", locationFactory, errorReceiver));

        Assert.assertTrue("Configuration of parser failed: " + errorReceiver.getMessage(), errorReceiver.isAllOk());

        final XMLReader reader = ds3ParserFactory.getParser();
        reader.setErrorHandler(new ErrorHandlerAdaptor("XMLReader", locationFactory, errorReceiver));
        return reader;
    }

    private void compareFiles(final File input, final File actualFile, final File expectedFile) throws IOException {
        // Make sure the file exists.
        Assert.assertTrue("Cannot find actual output file " + actualFile.getAbsolutePath(), actualFile.isFile());
        // Make sure the file exists.
        Assert.assertTrue("Cannot find expected output file " + expectedFile.getAbsolutePath(), expectedFile.isFile());

        // Normalise both files.
        normalise(actualFile);
        normalise(expectedFile);

        final InputStream actualIS = new BufferedInputStream(new FileInputStream(actualFile));
        final InputStream expectedIS = new BufferedInputStream(new FileInputStream(expectedFile));

        boolean success = false;
        try {
            // Now compare.
            int a = 0;
            int b = 0;
            while (a != -1 && b != -1) {
                Assert.assertEquals("Expected and actual files do not match for: " + actualFile.getName(), a, b);
                a = actualIS.read();
                b = expectedIS.read();
            }
            Assert.assertEquals("Expected and actual files do not match for: " + actualFile.getName(), a, b);

            success = true;
        } finally {
            actualIS.close();
            expectedIS.close();

            // If the compare was successful then delete the files as they are
            // regenerated every time and are very large.
            if (success) {
                FileUtil.deleteFile(input);
                FileUtil.deleteFile(actualFile);
                FileUtil.deleteFile(expectedFile);
            }
        }
    }

    private void normalise(final File file) throws IOException {
        final File temp = new File(file.getParentFile(), file.getName() + ".tmp");

        final Reader reader = new BufferedReader(new FileReader(file));
        final Writer writer = new BufferedWriter(new FileWriter(temp));

        int i = 0;
        final StringBuilder sb = new StringBuilder();
        boolean buffer = false;
        while ((i = reader.read()) != -1) {
            final char c = Character.toLowerCase((char) i);

            if (c == '<') {
                buffer = true;
            }

            if (!buffer) {
                writer.write(c);
            } else {
                sb.append(c);
            }

            if (c == '>') {
                buffer = false;

                String str = sb.toString();
                sb.setLength(0);

                if (str.startsWith("<records")) {
                    final int index = str.indexOf(" ");
                    if (index != -1) {
                        str = str.substring(0, index);
                    }
                    writer.append(str);
                    writer.append(">");

                } else {
                    writer.append(str);
                }
            }
        }

        // Close the files.
        reader.close();
        writer.flush();
        writer.close();

        // Rename the temp file.
        FileUtil.rename(temp, file);
    }
}
