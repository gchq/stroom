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

package stroom.search;


import stroom.core.dataprocess.PipelineDataProcessorTaskExecutor;
import stroom.docref.DocRef;
import stroom.index.impl.IndexShardManager;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.processor.api.DataProcessorTaskExecutor;
import stroom.test.CommonTranslationTestHelper;
import stroom.test.StoreCreationTool;
import stroom.test.common.StroomPipelineTestFileUtil;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.OptionalInt;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Class to create test data for use in all search tests.
 */

public class CommonIndexingTestHelper {
    private static final int N1 = 1;

    private static final String DIR = "CommonIndexingTest/";

    private static final Path INDEX_XSLT = StroomPipelineTestFileUtil.getTestResourcesFile(DIR + "index.xsl");
    private static final Path SEARCH_RESULT_XSLT = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "search_result.xsl");
    private static final Path SEARCH_RESULT_TEXT_XSLT = StroomPipelineTestFileUtil
            .getTestResourcesFile(DIR + "search_result_text.xsl");

    private final IndexShardManager indexShardManager;
    private final CommonTranslationTestHelper commonTranslationTestHelper;
    private final StoreCreationTool storeCreationTool;

    @Inject
    CommonIndexingTestHelper(final IndexShardManager indexShardManager,
                             final CommonTranslationTestHelper commonTranslationTestHelper,
                             final StoreCreationTool storeCreationTool) {
        this.indexShardManager = indexShardManager;
        this.commonTranslationTestHelper = commonTranslationTestHelper;
        this.storeCreationTool = storeCreationTool;
    }

    public void setup() {
        setup(OptionalInt.empty());
    }

    public void setup(OptionalInt maxDocsPerShard) {
        // Add data.
        commonTranslationTestHelper.setup();
        runProcessing(1, maxDocsPerShard);
    }

    public void setup(List<Path> dataFiles, OptionalInt maxDocsPerShard) {
        // Add data.
        commonTranslationTestHelper.setup(new ArrayList<>(dataFiles));
        runProcessing(dataFiles.size(), maxDocsPerShard);
    }

    private void runProcessing(final int dataFileCount, final OptionalInt maxDocsPerShard) {
        // Translate data.
        List<DataProcessorTaskExecutor> results = commonTranslationTestHelper.processAll();

        // 3 ref data streams pluss our data streams
        int expectedTaskCount = 3 + dataFileCount;

        assertThat(results.size()).isEqualTo(expectedTaskCount);
        for (final DataProcessorTaskExecutor result : results) {
            final PipelineDataProcessorTaskExecutor processor = (PipelineDataProcessorTaskExecutor) result;
            assertThat(processor.getWritten() > 0).as(result.toString()).isTrue();
            assertThat(processor.getRead() <= processor.getWritten()).as(result.toString()).isTrue();
            assertThat(processor.getMarkerCount(Severity.SEVERITIES)).as(result.toString()).isEqualTo(0);
        }

        // Add index.
        storeCreationTool.addIndex("Test index", INDEX_XSLT, maxDocsPerShard);
        // Translate data.
        results = commonTranslationTestHelper.processAll();
        assertThat(results.size()).isEqualTo(N1);
        for (final DataProcessorTaskExecutor result : results) {
            final PipelineDataProcessorTaskExecutor processor = (PipelineDataProcessorTaskExecutor) result;
            assertThat(processor.getWritten() > 0).as(result.toString()).isTrue();
            assertThat(processor.getRead() <= processor.getWritten()).as(result.toString()).isTrue();
            assertThat(processor.getMarkerCount(Severity.SEVERITIES)).as(result.toString()).isEqualTo(0);
        }

        // Flush all newly created index shards.
        indexShardManager.findFlush(new FindIndexShardCriteria());
    }

    public int flushIndex() {
        final FindIndexShardCriteria criteria = new FindIndexShardCriteria();
        criteria.getIndexUuidSet().setMatchAll(true);
        indexShardManager.findFlush(criteria);

        return 1;
    }

    public DocRef getSearchResultPipeline() {
        return storeCreationTool.getSearchResultPipeline("Search result", SEARCH_RESULT_XSLT);
    }

    public DocRef getSearchResultTextPipeline() {
        return storeCreationTool.getSearchResultPipeline("Search result text", SEARCH_RESULT_TEXT_XSLT);
    }
}
