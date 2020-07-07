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

package stroom.pipeline.xml.converter.ds3;


import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.filter.MergeFilter;
import stroom.pipeline.filter.SchemaFilter;
import stroom.pipeline.xml.converter.SchemaFilterFactory;
import stroom.pipeline.xml.converter.ds3.ref.VarMap;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Indicators;
import stroom.util.xml.XMLUtil;

import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// TODO : Reinstate tests.
//@Disabled("Removed test data")
class TestDS3 extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDS3.class);
    private static final String CONFIG_EXTENSION = ".ds3.xml";

    private final SchemaFilterFactory schemaFilterFactory = new SchemaFilterFactory();

    @Test
    void testBuildConfig() {
        final RootFactory rootFactory = new RootFactory();

        final ExpressionFactory headings = new RegexFactory(rootFactory, "headingsRegex", "^[^\n]+");
        final GroupFactory headingGroup = new GroupFactory(headings, "headingGroup");
        final ExpressionFactory heading = new RegexFactory(headingGroup, "headingRegex", "[^,]+");
        new VarFactory(heading, "heading");

        final ExpressionFactory record = new RegexFactory(rootFactory, "recordRegex", "^\n[\\S].+(\n[\\s]+.*)*");
        final GroupFactory partGroup = new GroupFactory(record, "partGroup");
        final ExpressionFactory part = new RegexFactory(partGroup, "partRegex", "\n?([^,]+),?");
        new DataFactory(part, "part", "$heading$", "$1");

        // Compile the configuration.
        rootFactory.compile();

        final Root linkedConfig = rootFactory.newInstance(new VarMap());
        LOGGER.debug("\n" + linkedConfig.toString());
    }

    // TODO : Add new test data to fully exercise DS3.

    @TestFactory
    Collection<DynamicTest> testProcessAll() throws IOException, TransformerConfigurationException, SAXException {
        // Get the testing directory.
        final Path testDir = getTestDir();
        final List<Path> paths = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(testDir, "*" + CONFIG_EXTENSION)) {
            stream.forEach(paths::add);
        }

        // Build a dynamic test for each data splitter config found
        final List<DynamicTest> dynamicTests = paths.stream()
                .peek(path -> LOGGER.info("Found: " + FileUtil.getCanonicalPath(path)))
                .filter(path -> path.getFileName().toString().endsWith(CONFIG_EXTENSION))
                .map(path -> {
                    final String name = path.getFileName().toString();
                    final String stem = name.substring(0, name.indexOf("."));

                    LOGGER.info("stem: {}", stem);

                    // Only process non variants.
                    if (stem.contains("_FAIL")) {
                        return negativeTest(stem);
                    } else {
                        return positiveTest(stem);
                    }
                })
                .collect(Collectors.toList());

        if (dynamicTests.isEmpty()) {
            fail("No %s files found to create test from", CONFIG_EXTENSION);
        } else {
            LOGGER.info("Created {} tests", dynamicTests.size());
        }

        return dynamicTests;
    }

    private DynamicTest negativeTest(final String stem, final String type) {
        return createTest(stem, "~" + type, true);
    }

    private DynamicTest negativeTest(final String stem) {
        return createTest(stem, "", true);
    }

    private DynamicTest positiveTest(final String stem) {
        return createTest(stem, "", false);
    }

    private DynamicTest positiveTest(final String stem, final String type) {
        return createTest(stem, "~" + type, false);
    }

    private DynamicTest createTest(final String stem, final String testType, final boolean expectedErrors) {
        return DynamicTest.dynamicTest(stem, () -> test(stem, testType, expectedErrors));
    }

    private void test(final String stem,
                      final String testType,
                      final boolean expectedErrors) throws IOException, TransformerConfigurationException, SAXException {
        // Get the testing directory.
        final Path testDir = getTestDir();

        LOGGER.info("Testing: {}, expecting errors: {}", stem, expectedErrors);

        boolean zipInput = false;
        Path input = testDir.resolve(stem + ".in");
        if (!Files.isRegularFile(input)) {
            final Path zip = testDir.resolve(stem + ".zip");
            if (Files.isRegularFile(zip)) {
                input = zip;
                zipInput = true;
            }
        }

        final Path config = testDir.resolve(stem + testType + CONFIG_EXTENSION);
        final Path out = testDir.resolve(stem + testType + ".out.xml");
        final Path outtmp = testDir.resolve(stem + testType + ".out.tmp.xml");
        final Path err = testDir.resolve(stem + testType + ".err");
        final Path errtmp = testDir.resolve(stem + testType + ".err.tmp");

        LOGGER.info("Data Splitter config: {}", config);
        LOGGER.info("Expected output: {}", out);
        LOGGER.info("Actual output: {}", outtmp);

        if (expectedErrors) {
            LOGGER.info("Expected errors: {}", err);
        }
        LOGGER.info("Actual errors: {}", err);

        // Delete temporary files.
        FileUtil.deleteFile(outtmp);
        FileUtil.deleteFile(errtmp);

        assertThat(Files.isRegularFile(config))
                .as(FileUtil.getCanonicalPath(config) + " does not exist")
                .isTrue();
        assertThat(Files.isRegularFile(input))
                .as(FileUtil.getCanonicalPath(input) + " does not exist")
                .isTrue();

        final OutputStream os = new BufferedOutputStream(Files.newOutputStream(outtmp));

        // Create an output writer.
        final TransformerHandler th = XMLUtil.createTransformerHandler(true);
        th.setResult(new StreamResult(os));

        final MergeFilter mergeFilter = new MergeFilter();
        mergeFilter.setContentHandler(th);

        LOGGER.debug("Creating reader for {}", config.toAbsolutePath().normalize().toString());
        // Create a reader from the config.
        try {
            final XMLReader reader = createReader(config, expectedErrors);
            reader.setContentHandler(mergeFilter);

            final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();

            try {
                mergeFilter.startProcessing();

                if (zipInput) {
                    final StreamLocationFactory locationFactory = new StreamLocationFactory();
                    reader.setErrorHandler(new ErrorHandlerAdaptor("DS3Parser", locationFactory, errorReceiver));

                    try (ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(input))) {
                        ZipEntry entry = zipInputStream.getNextEntry();
                        long streamNo = 0;
                        while (entry != null) {
                            streamNo++;
                            locationFactory.setStreamNo(streamNo);

                            try {
                                if (entry.getName().endsWith(".dat")) {
                                    reader.parse(new InputSource(new BufferedReader(new InputStreamReader(
                                            new IgnoreCloseInputStream(zipInputStream), StreamUtil.DEFAULT_CHARSET))));
                                }
                            } finally {
                                zipInputStream.closeEntry();
                                entry = zipInputStream.getNextEntry();
                            }
                        }
                    }

                } else {
                    final DefaultLocationFactory locationFactory = new DefaultLocationFactory();
                    reader.setErrorHandler(new ErrorHandlerAdaptor("DS3Parser", locationFactory, errorReceiver));

                    reader.parse(new InputSource(Files.newBufferedReader(input)));
                }
            } catch (final RuntimeException e) {
                e.printStackTrace();

            } finally {
                mergeFilter.endProcessing();
            }

            os.flush();
            os.close();

            // Write errors.
            if (!errorReceiver.isAllOk()) {
                final Writer errWriter = Files.newBufferedWriter(errtmp);

                for (final Entry<String, Indicators> entry : errorReceiver.getIndicatorsMap().entrySet()) {
                    final Indicators indicators = entry.getValue();
                    errWriter.write(indicators.toString());
                }

                errWriter.flush();
                errWriter.close();
            }

            // Only output errors if there were any.
            if (expectedErrors && errorReceiver.isAllOk()) {
                fail("Expected errors but none were found");
            } else if (!expectedErrors && !errorReceiver.isAllOk()) {
                fail("Did not expect any errors but some were found.");
            }

            compareFiles(outtmp, out);
            if (expectedErrors) {
                compareFiles(errtmp, err);
            }
        } catch (ReaderConfigurationException e) {
            if (expectedErrors) {
                // Do nothing, expected
            } else {
                throw e;
            }
        }
    }

    private Path getTestDir() {
        // Get the testing directory.
        final Path testDataDir = StroomPipelineTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestDS3");
        return testDir;
    }

    private XMLReader createReader(final Path config, final boolean expectingErrors) throws IOException {
        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(errorReceiver);

        final SchemaFilter schemaFilter = schemaFilterFactory.getSchemaFilter(DS3ParserFactory.NAMESPACE_URI,
                errorReceiverProxy);
        final DS3ParserFactory factory = new DS3ParserFactory(schemaFilter);

        final LocationFactory locationFactory = new DefaultLocationFactory();
        factory.configure(Files.newBufferedReader(config),
                new ErrorHandlerAdaptor("DS3ParserFactory", locationFactory, errorReceiver));

        if (!errorReceiver.isAllOk()) {
            if (!expectingErrors) {
                fail("Configuration of parser failed: " + errorReceiver.getMessage());
            } else {
                throw new ReaderConfigurationException();
            }
        }
        assertThat(errorReceiver.isAllOk() || expectingErrors)
                .as("Configuration of parser failed: " + errorReceiver.getMessage())
                .isTrue();

        final XMLReader reader = factory.getParser();
        reader.setErrorHandler(new ErrorHandlerAdaptor("DS3Parser", locationFactory, errorReceiver));
        return reader;
    }

    private void compareFiles(final Path actualFile, final Path expectedFile) {

        boolean areFilesTheSame = ComparisonHelper.unifiedDiff(expectedFile, actualFile);

        if (areFilesTheSame) {
            // If the files matched then delete the temporary file.
            FileUtil.deleteFile(actualFile);
            LOGGER.info("Files {} and {} matched", actualFile.getFileName(), expectedFile.getFileName());
        } else {
            fail("Files did not match, see diff");
        }
    }

    private static class ReaderConfigurationException extends RuntimeException {

    }

}
