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

package stroom.pipeline.xml.converter.ds3;


import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.xml.converter.SchemaFilterFactory;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.shared.ElementId;
import stroom.util.xml.XMLUtil;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.Reader;
import java.io.Writer;
import java.lang.management.ManagementFactory;
import java.lang.management.ThreadMXBean;
import java.nio.file.Files;
import java.nio.file.Path;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import static org.assertj.core.api.Assertions.assertThat;

class DS3PerformanceTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(DS3PerformanceTest.class);

    private static final int ITERATIONS = 1;
    private static final int INPUT_LINES = 10000000;

    private final SchemaFilterFactory schemaFilterFactory = new SchemaFilterFactory();
    private DS3ParserFactory ds3ParserFactory;

    void testCSVWithHeading() throws IOException, SAXException, TransformerConfigurationException {
        process("CSVWithHeading");
    }

    void testCSVWithHeadingSplit() throws IOException, SAXException, TransformerConfigurationException {
        process("CSVWithHeadingSplit");
    }

    private void process(final String stem) throws IOException, SAXException, TransformerConfigurationException {
        LOGGER.info("Testing: " + stem);

        final Path testDir = getDS3TestDir().resolve("tmp");
        FileUtil.mkdirs(testDir);

        final Path ds3Config = getDS3TestDir().resolve(stem + ".ds2");
        final Path input = testDir.resolve(stem + ".in");
        final Path output = testDir.resolve(stem + ".out");

        // Create an input file.
        createInput(input);

        final XMLReader ds3Reader = createDS3Reader(ds3Config);

        System.out.println("Testing Data Splitter v3");
        final long ds3Elapsed = process(input, output, ds3Reader);

        System.out.println("DS3 Compilation time " + ds3ParserFactory.getComp());
        System.out.println("DS3 Elapsed Time = " + ds3Elapsed);
    }

    private long process(final Path input, final Path output, final XMLReader parser)
            throws IOException, SAXException, TransformerConfigurationException {
        final ThreadMXBean threadMXBean = ManagementFactory.getThreadMXBean();
        threadMXBean.setThreadContentionMonitoringEnabled(true);
        threadMXBean.setThreadCpuTimeEnabled(true);

        final long cpuTimeStart = threadMXBean.getCurrentThreadCpuTime();

        // Create output writers.
        final OutputStream os = createWriter(parser, output);

        for (int i = 0; i < ITERATIONS; i++) {
            parser.parse(new InputSource(Files.newBufferedReader(input)));
        }

        os.flush();
        os.close();

        final long cpuTimeEnd = threadMXBean.getCurrentThreadCpuTime();
        return cpuTimeEnd - cpuTimeStart;
    }

    private void createInput(final Path input) throws IOException {
        final Writer writer = Files.newBufferedWriter(input);
        writer.write("Time,Action,User,File\n");
        for (int i = 1; i <= INPUT_LINES; i++) {
            writer.write("01/01/2009:00:00:01" + i + ",OPEN" + i + ",userone" + i
                         + ",D:\\TranslationKit\\example\\VerySimple\\OpenFileEvents" + i + ".txt\n");
        }
        writer.flush();
        writer.close();
    }

    private OutputStream createWriter(final XMLReader reader, final Path tmp)
            throws IOException, TransformerConfigurationException {
        // Create an output writer.
        final TransformerHandler th = XMLUtil.createTransformerHandler(true);
        final OutputStream os = new BufferedOutputStream(Files.newOutputStream(tmp));
        th.setResult(new StreamResult(os));

        reader.setContentHandler(th);

        return os;
    }

    private Path getDS3TestDir() {
        // Get the testing directory.
        final Path testDataDir = StroomPipelineTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestDS3");
        return testDir;
    }

    private XMLReader createDS3Reader(final Path config) throws IOException {
        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(errorReceiver);

        final SchemaFilter schemaFilter = schemaFilterFactory.getSchemaFilter(DS3ParserFactory.NAMESPACE_URI,
                errorReceiverProxy);
        ds3ParserFactory = new DS3ParserFactory(schemaFilter);

        final LocationFactory locationFactory = new DefaultLocationFactory();
        ds3ParserFactory.configure(Files.newBufferedReader(config),
                new ErrorHandlerAdaptor(new ElementId("DS3ParserFactory"), locationFactory, errorReceiver));

        assertThat(errorReceiver.isAllOk())
                .as("Configuration of parser failed: " + errorReceiver.getMessage())
                .isTrue();

        final XMLReader reader = ds3ParserFactory.getParser();
        reader.setErrorHandler(new ErrorHandlerAdaptor(new ElementId("XMLReader"), locationFactory, errorReceiver));
        return reader;
    }

    private void compareFiles(final Path input, final Path actualFile, final Path expectedFile) throws IOException {
        // Make sure the file exists.
        assertThat(Files.isRegularFile(actualFile))
                .as("Cannot find actual output file " + FileUtil.getCanonicalPath(actualFile))
                .isTrue();
        // Make sure the file exists.
        assertThat(Files.isRegularFile(expectedFile))
                .as("Cannot find expected output file " + FileUtil.getCanonicalPath(expectedFile))
                .isTrue();

        // Normalise both files.
        normalise(actualFile);
        normalise(expectedFile);

        final InputStream actualIS = new BufferedInputStream(Files.newInputStream(actualFile));
        final InputStream expectedIS = new BufferedInputStream(Files.newInputStream(expectedFile));

        boolean success = false;
        try {
            // Now compare.
            int a = 0;
            int b = 0;
            while (a != -1 && b != -1) {
                assertThat(b)
                        .as("Expected and actual files do not match for: " +
                            FileUtil.getCanonicalPath(actualFile))
                        .isEqualTo(a);
                a = actualIS.read();
                b = expectedIS.read();
            }
            assertThat(b)
                    .as("Expected and actual files do not match for: " +
                        FileUtil.getCanonicalPath(actualFile))
                    .isEqualTo(a);

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

    private void normalise(final Path file) throws IOException {
        final Path temp = file.getParent().resolve(file.getFileName().toString() + ".tmp");

        final Reader reader = Files.newBufferedReader(file);
        final Writer writer = Files.newBufferedWriter(temp);

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
