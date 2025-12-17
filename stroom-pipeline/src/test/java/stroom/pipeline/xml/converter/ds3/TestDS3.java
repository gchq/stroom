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
import stroom.util.logging.AsciiTable;
import stroom.util.logging.AsciiTable.Column;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.Location;
import stroom.util.shared.TextRange;
import stroom.util.xml.XMLUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipArchiveInputStream;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.Attributes;
import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.Locator;
import org.xml.sax.SAXException;
import org.xml.sax.XMLReader;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.StringReader;
import java.io.Writer;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map.Entry;
import java.util.stream.Collectors;
import javax.xml.transform.TransformerConfigurationException;
import javax.xml.transform.sax.TransformerHandler;
import javax.xml.transform.stream.StreamResult;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.fail;

// TODO : Need to try and migrate tests from v4 code base to give better coverage
class TestDS3 extends StroomUnitTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestDS3.class);
    private static final String CONFIG_EXTENSION = ".ds3.xml";

    private final SchemaFilterFactory schemaFilterFactory = new SchemaFilterFactory();

    @TestFactory
    Collection<DynamicTest> testProcessInputFiles() throws IOException {
        // Get the testing directory.
        final Path testDir = getTestDir();
        final List<Path> paths = new ArrayList<>();
        try (final DirectoryStream<Path> stream = Files.newDirectoryStream(
                testDir,
                "*" + CONFIG_EXTENSION)) {
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

    @Test
    void testBuildConfig() {
        final RootFactory rootFactory = new RootFactory();

        final ExpressionFactory headings = new RegexFactory(
                rootFactory,
                "headingsRegex",
                "^[^\n]+");
        final GroupFactory headingGroup = new GroupFactory(headings, "headingGroup");
        final ExpressionFactory heading = new RegexFactory(
                headingGroup,
                "headingRegex",
                "[^,]+");
        new VarFactory(heading, "heading");

        final ExpressionFactory record = new RegexFactory(
                rootFactory,
                "recordRegex",
                "^\n[\\S].+(\n[\\s]+.*)*");
        final GroupFactory partGroup = new GroupFactory(record, "partGroup");
        final ExpressionFactory part = new RegexFactory(
                partGroup,
                "partRegex",
                "\n?([^,]+),?");
        new DataFactory(part, "part", "$heading$", "$1");

        // Compile the configuration.
        rootFactory.compile();

        final Root linkedConfig = rootFactory.newInstance(new VarMap());
        LOGGER.debug("\n" + linkedConfig.toString());
    }

    @Test
    void testLocation_rows() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(rootFactory, "split", "\n");
        new DataFactory(splitFactory, "rowData", "row", "$1");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "hello\nworld\ngoodbye\nworld";

        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(
                        makeRange(1, 1, 1, 5),
                        makeRange(2, 1, 2, 5),
                        makeRange(3, 1, 3, 7),
                        makeRange(4, 1, 4, 5)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "hello",
                        "world",
                        "goodbye",
                        "world");
    }

    @Test
    void testLocation_singleLine() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(
                rootFactory,
                "split",
                "|");
        new DataFactory(splitFactory, "rowData", "row", "$1");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "hello|world|goodbye|world";
        //                       000000000111111111122222222222
        //                       123456789012345678901234567890

        // source record is taken to be word + delim, e.g. hello|
        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(makeRange(1, 1, 1, 6),
                        makeRange(1, 7, 1, 12),
                        makeRange(1, 13, 1, 20),
                        makeRange(1, 21, 1, 25)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "hello",
                        "world",
                        "goodbye",
                        "world");
    }

    @Test
    void testLocation_singleLine_contained() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(
                rootFactory,
                "split",
                0,
                -1,
                null,
                "|",
                null,
                "\"",
                "\"");
        new DataFactory(splitFactory, "rowData", "row", "$1");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "\"hello\"|\"world\"|\"goodbye\"|\"world\"";
        //                        000000 00 011111 11 11122222 22 222233 33333333
        //                        123456 78 901234 56 78901234 56 789012 34567890

        // source record is taken to be word + delim, e.g. hello|
        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(makeRange(1, 1, 1, 8),
                        makeRange(1, 9, 1, 16),
                        makeRange(1, 17, 1, 26),
                        makeRange(1, 27, 1, 33)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "hello",
                        "world",
                        "goodbye",
                        "world");
    }

    @Test
    void testLocation_singleLine2() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(
                rootFactory,
                "split",
                0,
                -1,
                null,
                "=",
                "\\",
                null,
                null);
        new DataFactory(splitFactory, "rowData", "row", "$3");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "flexString1=test\\nmessage\\=123";

        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(makeRange(1, 1, 1, 12),
                        makeRange(1, 13, 1, 30)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "flexString1",
                        "test\\nmessage=123");
    }

    @Test
    void testLocation_singleLine_contained_2charDelim() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(
                rootFactory,
                "split",
                0,
                -1,
                null,
                "||",
                null,
                "<",
                ">");
        new DataFactory(splitFactory, "rowData", "row", "$1");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "<hello>||<world>||<goodbye>||<world>";
        //                       0000000001111111111222222222233333333333
        //                       1234567890123456789012345678901234567890

        // source record is taken to be word + delim, e.g. hello|
        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(makeRange(1, 1, 1, 9),
                        makeRange(1, 10, 1, 18),
                        makeRange(1, 19, 1, 29),
                        makeRange(1, 30, 1, 36)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "hello",
                        "world",
                        "goodbye",
                        "world");
    }

    @Test
    void testLocation_singleLine_2charDelim() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(rootFactory, "split", "||");
        new DataFactory(splitFactory, "rowData", "row", "$1");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "hello||world||goodbye||world";
        //                       000000000011111111112222222222
        //                       123456789012345678901234567890

        // Make sure the location ranges for each record are correct
        // source record is taken to be word + delim, e.g. hello||
        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(
                        makeRange(1, 1, 1, 7),
                        makeRange(1, 8, 1, 14),
                        makeRange(1, 15, 1, 23),
                        makeRange(1, 24, 1, 28)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "hello",
                        "world",
                        "goodbye",
                        "world");
    }

    @Test
    void testLocation_emojis() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final SplitFactory splitFactory = new SplitFactory(
                rootFactory,
                "split",
                "|");
        new DataFactory(splitFactory, "rowData", "row", "$1");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        // Emojis take up two chars each
        final String inputStr = "🙂|🙁|👊|🖐";
        //                       0 00 00 0000111111111122222222222
        //                       1 23 45 6789012345678901234567890

        // source record is taken to be word + delim, e.g. hello|
        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(makeRange(1, 1, 1, 3),
                        makeRange(1, 4, 1, 6),
                        makeRange(1, 7, 1, 9),
                        makeRange(1, 10, 1, 11)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "🙂",
                        "🙁",
                        "👊",
                        "🖐");
    }

    @Test
    void testLocation_singleLine_regex() throws IOException, SAXException {
        final RootFactory rootFactory = new RootFactory();
        final RegexFactory regexFactory = new RegexFactory(
                rootFactory,
                "regex",
                "[A-Z][a-z]{2}");
        new DataFactory(regexFactory, "rowData", "row", "$0");

        rootFactory.compile();

        final Root root = rootFactory.newInstance(new VarMap());

        final String inputStr = "FriSatSunMon";
        //                       000000000011
        //                       123456789012

        // Make sure the location ranges for each record are correct
        // source record is taken to be word + delim, e.g. hello||
        final LoggingContentHandler loggingContentHandler = doLocationTest(
                root,
                inputStr,
                List.of(makeRange(1, 1, 1, 3),
                        makeRange(1, 4, 1, 6),
                        makeRange(1, 7, 1, 9),
                        makeRange(1, 10, 1, 12)));

        Assertions.assertThat(loggingContentHandler.getValues())
                .containsExactly(
                        "Fri",
                        "Sat",
                        "Sun",
                        "Mon");
    }


    private LoggingContentHandler doLocationTest(final Root root,
                                                 final String inputStr,
                                                 final List<TextRange> expectedRanges)
            throws IOException, SAXException {

        LOGGER.info("root:\n{}", root.toString());

        final DS3Parser ds3Parser = new DS3Parser(root, 1_000, 10_000);

        LOGGER.info("Input:\n-----\n{}\n-----", inputStr);

        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();
        final LoggingContentHandler contentHandler = new LoggingContentHandler(ds3Parser);

        ds3Parser.setContentHandler(contentHandler);
        ds3Parser.setErrorHandler(new ErrorHandlerAdaptor(new ElementId("DS3Parser"), locationFactory, errorReceiver));
        ds3Parser.parse(new InputSource(new StringReader(inputStr)));

        LOGGER.info("Expecting source ranges:\n{}",
                rangesToString(expectedRanges, inputStr));

        LOGGER.info("Actual source ranges:\n{}",
                rangesToString(contentHandler.textRanges, inputStr));

        Assertions.assertThat(contentHandler.getTextRanges())
                .containsExactlyElementsOf(expectedRanges);

        return contentHandler;
    }

    private String rangesToString(final List<TextRange> ranges, final String inputStr) {

        return AsciiTable.builder(ranges)
                .withColumn(Column.of("Source", (TextRange range) ->
                        "[" + extractRange(inputStr, range.getFrom(), range.getTo()) + "]"))
                .withColumn(Column.of("From", range ->
                        range.getFrom().toString()))
                .withColumn(Column.of("To", range ->
                        range.getTo().toString()))
                .build();
    }

    private TextRange makeRange(final int fromLine,
                                final int fromCol,
                                final int toLine,
                                final int toCol) {
        return new TextRange(
                DefaultLocation.of(fromLine, fromCol),
                DefaultLocation.of(toLine, toCol));
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
                      final boolean expectedErrors)
            throws IOException, TransformerConfigurationException, SAXException {

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
                    reader.setErrorHandler(
                            new ErrorHandlerAdaptor(new ElementId("DS3Parser"), locationFactory, errorReceiver));

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
                                            new IgnoreCloseInputStream(zipInputStream),
                                            StreamUtil.DEFAULT_CHARSET))));
                                }
                            } finally {
                                entry = zipInputStream.getNextEntry();
                            }
                        }
                    }

                } else {
                    final DefaultLocationFactory locationFactory = new DefaultLocationFactory();
                    reader.setErrorHandler(new ErrorHandlerAdaptor(
                            new ElementId("DS3Parser"),
                            locationFactory,
                            errorReceiver));

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

            compareFiles(outtmp, out);
            if (expectedErrors) {
                compareFiles(errtmp, err);
            }
        } catch (final ReaderConfigurationException e) {
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
                new ErrorHandlerAdaptor(new ElementId("DS3Parser"), locationFactory, errorReceiver));

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
        reader.setErrorHandler(new ErrorHandlerAdaptor(new ElementId("DS3Parser"), locationFactory, errorReceiver));
        return reader;
    }

    private void compareFiles(final Path actualFile, final Path expectedFile) {

        final boolean areFilesTheSame = ComparisonHelper.unifiedDiff(expectedFile, actualFile);

        if (areFilesTheSame) {
            // If the files matched then delete the temporary file.
            FileUtil.deleteFile(actualFile);
            LOGGER.info("Files {} and {} matched", actualFile.getFileName(), expectedFile.getFileName());
        } else {
            fail("Files did not match, see diff");
        }
    }

    /**
     * @param input The string to extract the range from
     * @param from  Inclusive
     * @param to    Inclusive
     * @return The extracted string
     */
    public String extractRange(final String input, final Location from, final Location to) {
        final StringReader stringReader = new StringReader(input);
        final StringBuilder output = new StringBuilder();
        int line = 1;
        int col = 1;
        int lastChar = -1;
        boolean isFirstChar = true;

        int chr;

        try {
            while (true) {
                chr = stringReader.read();
                if (chr == -1) {
                    break;
                }

                if (isFirstChar) {
                    isFirstChar = false;
                } else if (lastChar == '\n') {
                    line++;
                    col = 1;
                } else if (chr == '\n') {
                    // Do nothing
                } else {
                    col++;
                }
                lastChar = chr;

                final boolean isOnOrPastFromLocation = line > from.getLineNo()
                                                       || (line == from.getLineNo() && col >= from.getColNo());
                final boolean isBeforeOrOnToLocation = line < to.getLineNo()
                                                       || (line == to.getLineNo() && col <= to.getColNo());

                if (isOnOrPastFromLocation && isBeforeOrOnToLocation) {
                    output.append(charToVisible((char) chr));
                }
                if (!isBeforeOrOnToLocation) {
                    break;
                }
            }
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
        return output.toString();
    }

    private String charToVisible(final char c) {
        if (c == 0) {
            return "";
        } else if (c == '\n') {
            return "↵";
        } else if (c == '\t') {
            return "↹";
        } else {
            return String.valueOf(c);
        }
    }


    private static class ReaderConfigurationException extends RuntimeException {

    }

    private static class LoggingContentHandler implements ContentHandler {

        private final DS3Parser ds3Parser;
        private Locator locator;
        private List<TextRange> textRanges = new ArrayList<>();
        private List<String> values = new ArrayList<>();

        private LoggingContentHandler(final DS3Parser ds3Parser) {
            this.ds3Parser = ds3Parser;
        }

        public List<TextRange> getTextRanges() {
            return textRanges;
        }

        public List<String> getValues() {
            return values;
        }

        @Override
        public void setDocumentLocator(final Locator locator) {
//            LOGGER.info("setDocumentLocator() called");
            this.locator = locator;
        }

        @Override
        public void startDocument() throws SAXException {
            LOGGER.info("startDocument() called");
        }

        @Override
        public void endDocument() throws SAXException {
            LOGGER.info("endDocument() called");
        }

        @Override
        public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
//            LOGGER.info("startPrefixMapping() called");
        }

        @Override
        public void endPrefixMapping(final String prefix) throws SAXException {
//            LOGGER.info("endPrefixMapping() called");
        }

        @Override
        public void startElement(final String uri,
                                 final String localName,
                                 final String qName,
                                 final Attributes atts) throws SAXException {
            LOGGER.info("startElement({}) called", localName);
            if (localName.equals("record")) {
//                LOGGER.info("  [{}:{}]", locator.getLineNumber(), locator.getColumnNumber());
//                stringBuilder = new StringBuilder();
            }
            if (localName.equals("data")) {
                final String value = atts.getValue("value");
                LOGGER.info("  value: [{}]", value);
                values.add(value);
            }
        }

        @Override
        public void endElement(final String uri,
                               final String localName,
                               final String qName) throws SAXException {
            LOGGER.info("endElement({}) called", localName);
            if (localName.equals("record")) {

                if (locator instanceof DSLocator) {
                    final DSLocator dsLocator = (DSLocator) locator;
                    LOGGER.info("  [{}:{}] => [{}:{}]",
                            dsLocator.getLineNumber(),
                            dsLocator.getColumnNumber(),
                            dsLocator.getRecordEndLocator().getLineNumber(),
                            dsLocator.getRecordEndLocator().getColumnNumber());

                    textRanges.add(new TextRange(
                            DefaultLocation.of(dsLocator.getLineNumber(), dsLocator.getColumnNumber()),
                            DefaultLocation.of(
                                    dsLocator.getRecordEndLocator().getLineNumber(),
                                    dsLocator.getRecordEndLocator().getColumnNumber())));
                } else {
                    throw new RuntimeException("Expecting a DSLocator");
                }
            }
        }

//        private void logLocation(final Locator from, final Locator to) {
//            LOGGER.info("  Line: {}, col: {}", locator.getLineNumber(), locator.getColumnNumber());
//        }
//
//        private void logLocation(final Locator from, final Locator to) {
//            LOGGER.info("  Line: {}, col: {}", locator.getLineNumber(), locator.getColumnNumber());
//        }

        @Override
        public void characters(final char[] ch, final int start, final int length) throws SAXException {
//            LOGGER.info("characters() called");
        }

        @Override
        public void ignorableWhitespace(final char[] ch, final int start, final int length) throws SAXException {
//            LOGGER.info("ignorableWhitespace() called");
        }

        @Override
        public void processingInstruction(final String target, final String data) throws SAXException {
//            LOGGER.info("processingInstruction() called");
        }

        @Override
        public void skippedEntity(final String name) throws SAXException {
//            LOGGER.info("skippedEntity() called");
        }
    }

}
