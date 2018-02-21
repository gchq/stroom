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

import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;
import stroom.entity.util.XMLUtil;
import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.filter.MergeFilter;
import stroom.pipeline.filter.SchemaFilter;
import stroom.test.ComparisonHelper;
import stroom.test.StroomPipelineTestFileUtil;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Indicators;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.StroomUnitTest;
import stroom.xml.converter.SchemaFilterFactory;
import stroom.xml.converter.ds3.ref.VarMap;

import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;
import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

// TODO : Reinstate tests.

@Ignore("Removed test data")
@RunWith(StroomJUnit4ClassRunner.class)
public class TestDS3 extends StroomUnitTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestDS3.class);

    private final SchemaFilterFactory schemaFilterFactory = new SchemaFilterFactory();

    @Test
    public void testBuildConfig() throws Exception {
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

    @Test
    public void testProcessAll() throws Exception {
        // Get the testing directory.
        final Path testDir = getTestDir();
        final List<Path> paths = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(testDir, "*.ds2")) {
            stream.forEach(paths::add);
        }

        for (final Path path : paths) {
            final String name = path.getFileName().toString();
            if (name.endsWith(".ds2")) {
                String type = null;
                String variant = null;
                // String extension = null;

                int index = name.lastIndexOf(".");
                if (index != -1) {
                    type = name.substring(0, index);
                    // extension = name.substring(index + 1);

                    index = type.indexOf("~");
                    if (index != -1) {
                        variant = type.substring(index + 1);
                        type = type.substring(0, index);
                    }
                }

                // Only process non variants.
                if (variant == null) {
                    positiveTest(type);
                }
            }
        }
    }

    private void negativeTest(final String stem, final String type) throws Exception {
        test(stem, "~" + type, true);
    }

    private void positiveTest(final String stem) throws Exception {
        test(stem, "", false);
    }

    private void positiveTest(final String stem, final String type) throws Exception {
        test(stem, "~" + type, false);
    }

    private void test(final String stem, final String testType, final boolean expectedErrors) throws Exception {
        // Get the testing directory.
        final Path testDir = getTestDir();

        LOGGER.info("Testing: " + stem);

        boolean zipInput = false;
        Path input = testDir.resolve(stem + ".in");
        if (!Files.isRegularFile(input)) {
            final Path zip = testDir.resolve(stem + ".zip");
            if (Files.isRegularFile(zip)) {
                input = zip;
                zipInput = true;
            }
        }

        final Path config = testDir.resolve(stem + testType + ".ds2");
        final Path out = testDir.resolve(stem + testType + ".out");
        final Path outtmp = testDir.resolve(stem + testType + ".out_tmp");
        final Path err = testDir.resolve(stem + testType + ".err");
        final Path errtmp = testDir.resolve(stem + testType + ".err_tmp");

        // Delete temporary files.
        FileUtil.deleteFile(outtmp);
        FileUtil.deleteFile(errtmp);

        Assert.assertTrue(FileUtil.getCanonicalPath(config) + " does not exist", Files.isRegularFile(config));
        Assert.assertTrue(FileUtil.getCanonicalPath(input) + " does not exist", Files.isRegularFile(input));

        final OutputStream os = new BufferedOutputStream(Files.newOutputStream(outtmp));

        // Create an output writer.
        final TransformerHandler th = XMLUtil.createTransformerHandler(true);
        th.setResult(new StreamResult(os));

        final MergeFilter mergeFilter = new MergeFilter();
        mergeFilter.setContentHandler(th);

        // Create a reader from the config.
        final XMLReader reader = createReader(config);
        reader.setContentHandler(mergeFilter);

        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();

        try {
            mergeFilter.startProcessing();

            if (zipInput) {
                final StreamLocationFactory locationFactory = new StreamLocationFactory();
                reader.setErrorHandler(new ErrorHandlerAdaptor("DS3Parser", locationFactory, errorReceiver));

                final ZipInputStream zipInputStream = new ZipInputStream(Files.newInputStream(input));
                try {
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
                } finally {
                    zipInputStream.close();
                }

            } else {
                final DefaultLocationFactory locationFactory = new DefaultLocationFactory();
                reader.setErrorHandler(new ErrorHandlerAdaptor("DS3Parser", locationFactory, errorReceiver));

                reader.parse(new InputSource(Files.newBufferedReader(input)));
            }
        } catch (final Exception e) {
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
            Assert.fail("Expected errors but none were found");
        } else if (!expectedErrors && !errorReceiver.isAllOk()) {
            Assert.fail("Did not expect any errors but some were found.");
        }

        compareFiles(outtmp, out);
        if (expectedErrors) {
            compareFiles(errtmp, err);
        }
    }

    private Path getTestDir() {
        // Get the testing directory.
        final Path testDataDir = StroomPipelineTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestDS3");
        return testDir;
    }

    private XMLReader createReader(final Path config) throws Exception {
        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(errorReceiver);

        final SchemaFilter schemaFilter = schemaFilterFactory.getSchemaFilter(DS3ParserFactory.NAMESPACE_URI,
                errorReceiverProxy);
        final DS3ParserFactory factory = new DS3ParserFactory();
        factory.setSchemaFilter(schemaFilter);

        final LocationFactory locationFactory = new DefaultLocationFactory();
        factory.configure(Files.newBufferedReader(config),
                new ErrorHandlerAdaptor("DS3ParserFactory", locationFactory, errorReceiver));

        Assert.assertTrue("Configuration of parser failed: " + errorReceiver.getMessage(), errorReceiver.isAllOk());

        final XMLReader reader = factory.getParser();
        reader.setErrorHandler(new ErrorHandlerAdaptor("DS3Parser", locationFactory, errorReceiver));
        return reader;
    }

    private void compareFiles(final Path actualFile, final Path expectedFile) {
        ComparisonHelper.compareFiles(expectedFile, actualFile);
        // If the files matched then delete the temporary file.
        FileUtil.deleteFile(actualFile);
    }
}
