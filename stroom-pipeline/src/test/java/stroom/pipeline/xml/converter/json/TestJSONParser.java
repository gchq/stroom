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

package stroom.pipeline.xml.converter.json;


import stroom.pipeline.DefaultLocationFactory;
import stroom.pipeline.LocationFactory;
import stroom.pipeline.StreamLocationFactory;
import stroom.pipeline.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.filter.XMLFilter;
import stroom.pipeline.filter.XMLFilterFork;
import stroom.pipeline.writer.JSONWriter;
import stroom.pipeline.writer.OutputStreamAppender;
import stroom.pipeline.writer.XMLWriter;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.test.common.util.test.StroomUnitTest;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

class TestJSONParser extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestJSONParser.class);

    @Test
    void testKV() throws IOException {
        positiveTest("KV");
    }

    @Test
    void testSimple() throws IOException {
        positiveTest("Simple");
    }

    @Test
    void testMulti() throws IOException {
        positiveTest("Multi");
    }

    private void negativeTest(final String stem, final String type) throws IOException {
        test(stem, "~" + type, true);
    }

    private void positiveTest(final String stem) throws IOException {
        test(stem, "", false);
    }

    private void positiveTest(final String stem, final String type) throws IOException {
        test(stem, "~" + type, false);
    }

    @Disabled
    @Test
    void testVeryLargeArray() throws IOException {
        final int maxDepth = 5;
        final JsonType[][] structure = new JsonType[maxDepth][];
        structure[0] = new JsonType[]{JsonType.ARRAY};
        structure[1] = new JsonType[]{JsonType.OBJECT};
        structure[2] = new JsonType[]{
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE};
        structure[3] = new JsonType[]{
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE};
        structure[4] = new JsonType[]{
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE};

        final String stem = "VeryLargeArray";
        final Path testDir = getTestDir();
        final Path input = testDir.resolve(stem + ".in");
        new JSONTestDataCreator().writeFile(input, structure);
        test(stem, "", false);
    }

    @Disabled
    @Test
    void testVeryLargeObject() throws IOException {
        final int maxDepth = 5;
        final JsonType[][] structure = new JsonType[maxDepth][];
        structure[0] = new JsonType[]{JsonType.OBJECT};
        structure[1] = new JsonType[]{JsonType.OBJECT};
        structure[2] = new JsonType[]{
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE};
        structure[3] = new JsonType[]{
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.OBJECT,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.ARRAY,
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE};
        structure[4] = new JsonType[]{
                JsonType.VALUE,
                JsonType.VALUE,
                JsonType.VALUE};

        final String stem = "VeryLargeObject";
        final Path testDir = getTestDir();
        final Path input = testDir.resolve(stem + ".in");
        new JSONTestDataCreator().writeFile(input, structure);
        test(stem, "", false);
    }

    private void test(final String stem, final String testType, final boolean expectedErrors) throws IOException {
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

        final Path outXML = testDir.resolve(stem + testType + ".xml_out");
        final Path outTempXML = testDir.resolve(stem + testType + ".xml_out_tmp");
        final Path outJSON = testDir.resolve(stem + testType + ".json_out");
        final Path outTempJSON = testDir.resolve(stem + testType + ".json_out_tmp");
        final Path err = testDir.resolve(stem + testType + ".err");
        final Path errTemp = testDir.resolve(stem + testType + ".err_tmp");

        // Delete temporary files.
        FileUtil.deleteFile(outTempXML);
        FileUtil.deleteFile(outTempJSON);
        FileUtil.deleteFile(errTemp);

        assertThat(Files.isRegularFile(input)).as(FileUtil.getCanonicalPath(input) + " does not exist").isTrue();

        final OutputStream xmlOS = new BufferedOutputStream(Files.newOutputStream(outTempXML));
        final OutputStream jsonOS = new BufferedOutputStream(Files.newOutputStream(outTempJSON));

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
        final OutputStreamAppender xmlAppender = new OutputStreamAppender(errorReceiverProxy, xmlOS);
        final OutputStreamAppender jsonAppender = new OutputStreamAppender(errorReceiverProxy, jsonOS);

        final XMLWriter xmlWriter = new XMLWriter(errorReceiverProxy, null, null, null, null, null, null, null);
        xmlWriter.setIndentOutput(true);
        xmlWriter.setTarget(xmlAppender);

        final JSONWriter jsonWriter = new JSONWriter(errorReceiverProxy);
        jsonWriter.setIndentOutput(true);
        jsonWriter.setTarget(jsonAppender);

        final XMLFilter[] filters = new XMLFilter[]{xmlWriter, jsonWriter};
        final XMLFilterFork fork = new XMLFilterFork(filters);

        final XMLFilter filter = fork;

        // Create a reader from the config.
        final XMLReader reader = createReader();
        reader.setContentHandler(filter);

        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();

        try {
            filter.startProcessing();

            if (zipInput) {
                final StreamLocationFactory locationFactory = new StreamLocationFactory();
                reader.setErrorHandler(new ErrorHandlerAdaptor(
                        new ElementId("JSONParser"), locationFactory, errorReceiver));

                try (final ZipArchiveInputStream zipInputStream =
                        new ZipArchiveInputStream(Files.newInputStream(input))) {
                    ZipArchiveEntry entry = zipInputStream.getNextEntry();
                    long partIndex = -1;
                    while (entry != null) {
                        partIndex++;
                        locationFactory.setPartIndex(partIndex);

                        try {
                            if (entry.getName().endsWith(".dat")) {
                                reader.parse(new InputSource(new BufferedReader(new InputStreamReader(
                                        new IgnoreCloseInputStream(zipInputStream), StreamUtil.DEFAULT_CHARSET))));
                            }
                        } finally {
                            entry = zipInputStream.getNextEntry();
                        }
                    }
                }

            } else {
                final DefaultLocationFactory locationFactory = new DefaultLocationFactory();
                reader.setErrorHandler(new ErrorHandlerAdaptor(
                        new ElementId("JSONParser"), locationFactory, errorReceiver));

                reader.parse(new InputSource(Files.newBufferedReader(input)));
            }
        } catch (final SAXException | RuntimeException e) {
            e.printStackTrace();

        } finally {
            filter.endProcessing();
        }

        xmlOS.flush();
        xmlOS.close();
        jsonOS.flush();
        jsonOS.close();

        // Write errors.
        if (!errorReceiver.isAllOk()) {
            final Writer errWriter = Files.newBufferedWriter(errTemp);

            for (final Entry<ElementId, Indicators> entry : errorReceiver.getIndicatorsMap().entrySet()) {
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

        compareFiles(outTempXML, outXML);
        compareFiles(outTempJSON, outJSON);
        if (expectedErrors) {
            compareFiles(errTemp, err);
        }
    }

    private Path getTestDir() {
        // Get the testing directory.
        final Path testDataDir = StroomPipelineTestFileUtil.getTestResourcesDir();
        final Path testDir = testDataDir.resolve("TestJSON");
        return testDir;
    }

    private XMLReader createReader() {
        final JSONParserFactory factory = new JSONParserFactory();
        factory.setAddRootObject(false);

        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();

        final XMLReader reader = factory.getParser();
        reader.setErrorHandler(new ErrorHandlerAdaptor(
                new ElementId("JSONParser"), locationFactory, errorReceiver));
        return reader;
    }

    private void compareFiles(final Path actualFile, final Path expectedFile) {
        ComparisonHelper.compareFiles(expectedFile, actualFile, true, false);
        // If the files matched then delete the temporary file.
        FileUtil.deleteFile(actualFile);
    }
}
