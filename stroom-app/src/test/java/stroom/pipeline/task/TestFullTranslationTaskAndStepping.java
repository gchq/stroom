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

package stroom.pipeline.task;

import stroom.test.common.StroomPipelineTestFileUtil;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;

class TestFullTranslationTaskAndStepping extends TranslationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestFullTranslationTaskAndStepping.class);

    private static final ThreadLocal<Boolean> DONE_SETUP = ThreadLocal.withInitial(() -> false);

    @BeforeEach
    void setup() {
        if (!DONE_SETUP.get()) {
            // Import all the schemas/pipes/xslts/etc.
            importConfig();
            // Some of the tests rely on ref data lookups so ensure all ref data is loaded first
            loadAllRefData();
            DONE_SETUP.set(true);
        }
    }

    @Override
    protected boolean cleanupBetweenTests() {
        return false;
    }

    @Test
    void testBOMXMLEvents() throws IOException {
        testStepping("BOM_XML-EVENTS");
    }

    @Test
    void testBOMAndReplaceXMLEvents() throws IOException {
        testStepping("BOM_AND_REPLACE_XML-EVENTS");
    }

    @Test
    void testDataSplitterEvents() throws IOException {
        testStepping("DATA_SPLITTER-EVENTS");
    }

    @Test
    void testFileToLocationReference() throws IOException {
        testStepping("FILENO_TO_LOCATION-REFERENCE", true);
    }

    @Test
    void testJSONEvents() throws IOException {
        testStepping("JSON-EVENTS");
    }

    @Test
    void testRawStreamingEvents() throws IOException {
        testStepping("RAW_STREAMING-EVENTS");
    }

    @Test
    void testRawStreamingForkEvents() throws IOException {
        testStepping("RAW_STREAMING_FORK-EVENTS");
    }

    @Test
    void testXMLFragmentEvents() throws IOException {
        testStepping("XML_FRAGMENT-EVENTS");
    }

    @Test
    void testXMLEvents() throws IOException {
        testStepping("XML-EVENTS");
    }

    @Test
    void testXMLReaderEvents() throws IOException {
        testStepping("XML_READER-EVENTS");
    }

    @Test
    void testZipTestDataSplitterEvents() throws IOException {
        testStepping("ZIP_TEST-DATA_SPLITTER-EVENTS");
    }

    private void testStepping(final String feedName) {
        testStepping(feedName, false);
    }

    private void testStepping(final String feedName, final boolean isReference) {
        final Path outDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(
                "TestFullTranslationTaskAndStepping");

        // Load the data, but not ref data as we have already loaded that
        if (!isReference) {
            testTranslationTask(feedName, false, false);
        }

        final Instant startTime = Instant.now();
        testSteppingTask(feedName, outDir);

        LOGGER.info(feedName + " translation stepping took: " + Duration.between(startTime, Instant.now()));
    }
}
