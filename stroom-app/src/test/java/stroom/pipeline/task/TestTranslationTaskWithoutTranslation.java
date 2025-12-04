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


import stroom.data.shared.StreamTypeNames;
import stroom.data.store.mock.MockStore;
import stroom.meta.mock.MockMetaService;
import stroom.meta.shared.Meta;
import stroom.processor.api.ProcessorResult;
import stroom.test.AbstractProcessIntegrationTest;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.ComparisonHelper;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Path;
import java.util.List;
import java.util.Map.Entry;

import static org.assertj.core.api.Assertions.assertThat;

class TestTranslationTaskWithoutTranslation extends AbstractProcessIntegrationTest {

    private static final String DIR = "TestTranslationTaskWithoutTranslation/";
    private static final String FEED_NAME = "TEST_FEED";
    private static final Path RESOURCE_NAME = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "TestTask.out");

    @Inject
    private CommonTranslationTestHelper commonTranslationTestHelper;
    @Inject
    private MockStore streamStore;
    @Inject
    private MockMetaService metaService;
    @Inject
    private StoreCreationTool storeCreationTool;

    /**
     * Tests Task with a valid resource and feed.
     *
     * @throws IOException Could be thrown.
     */
    @Test
    void test() throws IOException {
        setup(FEED_NAME, RESOURCE_NAME);
        assertThat(metaService.getLockCount()).isEqualTo(0);

        final List<ProcessorResult> results = commonTranslationTestHelper.processAll();
        assertThat(results.size()).isEqualTo(1);

        for (final ProcessorResult result : results) {
            final String message = "Count = " + result.getRead() + "," + result.getWritten() + ","
                    + result.getMarkerCount(Severity.SEVERITIES);

            assertThat(result.getWritten() > 0).as(message).isTrue();
            assertThat(result.getRead() <= result.getWritten()).as(message).isTrue();
            assertThat(result.getMarkerCount(Severity.SEVERITIES)).as(message).isEqualTo(0);
        }

        final Path inputDir = StroomPipelineTestFileUtil.getTestResourcesDir().resolve(DIR);
        final Path outputDir = StroomPipelineTestFileUtil.getTestOutputDir().resolve(DIR);

        for (final Entry<Long, Meta> entry : metaService.getMetaMap().entrySet()) {
            final long streamId = entry.getKey();
            final Meta meta = entry.getValue();
            if (StreamTypeNames.EVENTS.equals(meta.getTypeName())) {
                final byte[] data = streamStore.getFileData().get(streamId).get(meta.getTypeName());

                // Write the actual XML out.
                final OutputStream os = StroomPipelineTestFileUtil.getOutputStream(outputDir, "TestTask.out");
                os.write(data);
                os.flush();
                os.close();

                ComparisonHelper.compareDirs(inputDir, outputDir);
            }
        }

        // Make sure 10 records were written.
        assertThat(results.get(0).getWritten()).isEqualTo(10);
    }

    private void setup(final String feedName, final Path dataLocation) throws IOException {
        storeCreationTool.addEventData(feedName, null, null, null, dataLocation, null);
    }
}
