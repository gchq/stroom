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

package stroom.xml.converter.json;

import java.io.BufferedOutputStream;
import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.Writer;
import java.util.Map.Entry;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import stroom.test.StroomProcessTestFileUtil;
import stroom.util.logging.StroomLogger;
import stroom.util.test.StroomUnitTest;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import stroom.pipeline.server.DefaultLocationFactory;
import stroom.pipeline.server.LocationFactory;
import stroom.pipeline.server.StreamLocationFactory;
import stroom.pipeline.server.errorhandler.ErrorHandlerAdaptor;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.FatalErrorReceiver;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.filter.XMLFilter;
import stroom.pipeline.server.filter.XMLFilterFork;
import stroom.pipeline.server.writer.JSONWriter;
import stroom.pipeline.server.writer.OutputStreamAppender;
import stroom.pipeline.server.writer.XMLWriter;
import stroom.test.ComparisonHelper;
import stroom.util.io.FileUtil;
import stroom.util.io.IgnoreCloseInputStream;
import stroom.util.io.StreamUtil;
import stroom.util.shared.Indicators;
import stroom.util.test.StroomJUnit4ClassRunner;
import stroom.util.test.FileSystemTestUtil;
import stroom.xmlschema.server.MockXMLSchemaService;
import stroom.xmlschema.shared.FindXMLSchemaCriteria;
import stroom.xmlschema.shared.XMLSchema;
import stroom.xmlschema.shared.XMLSchemaService;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestJSONParser extends StroomUnitTest {
    private static final StroomLogger LOGGER = StroomLogger.getLogger(TestJSONParser.class);

    private final XMLSchemaService xmlSchemaService = new MockXMLSchemaService();

    @Test
    public void testKV() throws Exception {
        positiveTest("KV");
    }

    @Test
    public void testSimple() throws Exception {
        positiveTest("Simple");
    }

    @Test
    public void testMulti() throws Exception {
        positiveTest("Multi");
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
        final File testDir = getTestDir();

        LOGGER.info("Testing: " + stem);

        boolean zipInput = false;
        File input = new File(testDir, stem + ".in");
        if (!input.isFile()) {
            final File zip = new File(testDir, stem + ".zip");
            if (zip.isFile()) {
                input = zip;
                zipInput = true;
            }
        }

        final File outXML = new File(testDir, stem + testType + ".xml_out");
        final File outTempXML = new File(testDir, stem + testType + ".xml_out_tmp");
        final File outJSON = new File(testDir, stem + testType + ".json_out");
        final File outTempJSON = new File(testDir, stem + testType + ".json_out_tmp");
        final File err = new File(testDir, stem + testType + ".err");
        final File errTemp = new File(testDir, stem + testType + ".err_tmp");

        // Delete temporary files.
        FileUtil.deleteFile(outTempXML);
        FileUtil.deleteFile(outTempJSON);
        FileUtil.deleteFile(errTemp);

        Assert.assertTrue(input.getAbsolutePath() + " does not exist", input.isFile());

        final OutputStream xmlOS = new BufferedOutputStream(new FileOutputStream(outTempXML));
        final OutputStream jsonOS = new BufferedOutputStream(new FileOutputStream(outTempJSON));

        final OutputStreamAppender xmlAppender = new OutputStreamAppender(xmlOS);
        final OutputStreamAppender jsonAppender = new OutputStreamAppender(jsonOS);

        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());

        final XMLWriter xmlWriter = new XMLWriter(errorReceiverProxy, null);
        xmlWriter.setIndentOutput(true);
        xmlWriter.setTarget(xmlAppender);

        final JSONWriter jsonWriter = new JSONWriter(errorReceiverProxy);
        jsonWriter.setIndentOutput(true);
        jsonWriter.setTarget(jsonAppender);

        final XMLFilter[] filters = new XMLFilter[] { xmlWriter, jsonWriter };
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
                reader.setErrorHandler(new ErrorHandlerAdaptor("JSONParser", locationFactory, errorReceiver));

                final FileInputStream inputStream = new FileInputStream(input);
                final ZipInputStream zipInputStream = new ZipInputStream(inputStream);
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
                reader.setErrorHandler(new ErrorHandlerAdaptor("JSONParser", locationFactory, errorReceiver));

                reader.parse(new InputSource(new BufferedReader(new FileReader(input))));
            }
        } catch (final Exception e) {
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
            final Writer errWriter = new BufferedWriter(new FileWriter(errTemp));

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

        compareFiles(outTempXML, outXML);
        compareFiles(outTempJSON, outJSON);
        if (expectedErrors) {
            compareFiles(errTemp, err);
        }
    }

    private File getTestDir() {
        // Get the testing directory.
        final File testDataDir = StroomProcessTestFileUtil.getTestResourcesDir();
        final File testDir = new File(testDataDir, "TestJSON");
        return testDir;
    }

    private XMLReader createReader() throws Exception {
        final JSONParserFactory factory = new JSONParserFactory();
        factory.setAddRootObject(false);

        final LoggingErrorReceiver errorReceiver = new LoggingErrorReceiver();
        final LocationFactory locationFactory = new DefaultLocationFactory();

        final XMLReader reader = factory.getParser();
        reader.setErrorHandler(new ErrorHandlerAdaptor("JSONParser", locationFactory, errorReceiver));
        return reader;
    }

    public void loadXMLSchema(final String schemaGroup, final String schemaName, final String namespaceURI,
            final String systemId, final String fileName) throws IOException {
        final File dir = FileSystemTestUtil.getConfigXSDDir();

        final File file = new File(dir, fileName);

        final XMLSchema xmlSchema = new XMLSchema();
        xmlSchema.setSchemaGroup(schemaGroup);
        xmlSchema.setName(schemaName);
        xmlSchema.setNamespaceURI(namespaceURI);
        xmlSchema.setSystemId(systemId);
        xmlSchema.setData(StreamUtil.fileToString(file));

        final FindXMLSchemaCriteria criteria = new FindXMLSchemaCriteria();
        criteria.getName().setString(schemaName);

        if (xmlSchemaService.find(criteria).size() == 0) {
            xmlSchemaService.save(xmlSchema);
        }
    }

    private void compareFiles(final File actualFile, final File expectedFile) throws FileNotFoundException {
        ComparisonHelper.compareFiles(expectedFile, actualFile, true, false);
        // If the files matched then delete the temporary file.
        FileUtil.deleteFile(actualFile);
    }
}
