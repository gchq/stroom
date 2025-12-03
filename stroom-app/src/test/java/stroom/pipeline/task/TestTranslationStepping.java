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

import stroom.data.store.api.Store;
import stroom.pipeline.shared.TextConverterDoc.TextConverterType;
import stroom.task.api.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.StoreCreationTool;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

class TestTranslationStepping extends AbstractCoreIntegrationTest {

    private static final String DIR = "TestTranslationStepping/";

    private static final String DATASPLITTER_FORMAT_DEFINITION = DIR + "SimpleCSVSplitter.ds";
    private static final String DATASPLITTER_XSLT = DIR + "TestCSV.xsl";
    private static final String INPUT_DIR = DIR;

    private static final String XMLFRAGMENT_FORMAT_DEFINITION = DIR + "TranslationTestService_fragment_wrapper.xml";

    @Inject
    private StoreCreationTool storeCreationTool;
    @Inject
    private Store streamStore;
    @Inject
    private TaskManager taskManager;

    @Test
    void testDataSplitterTransformation() {
        test(DATASPLITTER_FORMAT_DEFINITION, DATASPLITTER_XSLT, INPUT_DIR, ".csv", TextConverterType.DATA_SPLITTER,
                "DataSplitter");
    }

    @Test
    void testXMLFragmentTransformation() {
        test(XMLFRAGMENT_FORMAT_DEFINITION, DATASPLITTER_XSLT, INPUT_DIR, ".nxml", TextConverterType.XML_FRAGMENT,
                "XMLFragment");
    }

    @Test
    void testXMLTransformation() {
        test(null, DATASPLITTER_XSLT, INPUT_DIR, ".xml", TextConverterType.NONE, "XML");
    }

    private void test(final String converterDefinitionLocation, final String xsltLocation, final String inputDir,
                      final String extension, final TextConverterType textConverterType, final String outputName) {
        // FIXME : Fix this.
    }
}
