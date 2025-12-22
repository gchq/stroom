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

package stroom.search.manualtesting;


import stroom.docref.DocRef;
import stroom.index.impl.IndexShardSearchConfig;
import stroom.index.impl.IndexStore;
import stroom.query.api.Column;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.Format;
import stroom.query.api.ParamUtil;
import stroom.query.api.Row;
import stroom.query.api.TableSettings;
import stroom.query.common.v2.ResultStoreManager;
import stroom.search.AbstractSearchTest;
import stroom.search.CommonIndexingTestHelper;
import stroom.search.extraction.ExtractionConfig;
import stroom.task.api.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;

import jakarta.inject.Inject;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.IntStream;

import static org.assertj.core.api.Assertions.assertThat;

// This junit configuration is copied from AbstractCoreIntegrationTest and StroomIntegrationTest
// and it is so we can manually run tests using state from a previous run.
class TestGroupedCountsInteractiveSearch extends AbstractCoreIntegrationTest {

    private static final Logger LOGGER = LoggerFactory.getLogger(TestGroupedCountsInteractiveSearch.class);

    private static final String DATA_FILE_NAME_PREFIX = "NetworkMonitoringBulkData_";
    private static final String DATA_FILE_NAME_SUFFIX = ".in";
    private static final String DATA_FILE_NAME_FORMAT = DATA_FILE_NAME_PREFIX + "%03d" + DATA_FILE_NAME_SUFFIX;

    private static final int STREAM_ROW_COUNT = 100_000;
    private static final int STREAM_COUNT = 100;
    private static final int MAX_DOCS_PER_SHARD = 10_000;

    @TempDir
    Path testDir;
    @Inject
    private CommonIndexingTestHelper commonIndexingTestHelper;
    @Inject
    private IndexStore indexStore;
    @Inject
    private TaskManager taskManager;
    @Inject
    private ResultStoreManager searchResponseCreatorManager;
    @Inject
    private CommonTestControl commonTestControl;
    @Inject
    private IndexShardSearchConfig indexShardSearchConfig;
    @Inject
    private ExtractionConfig extractionConfig;

    @BeforeEach
    void beforeTest() {
        LOGGER.info("Setting temp dir to {}", testDir.toAbsolutePath().toString());
    }

    //    @Override
    protected boolean onAfterSetup() {

        final List<Path> dataFiles = new ArrayList<>();
//        if (!Files.exists(dataFile)) {
        LOGGER.info("Generating test data");
        IntStream.rangeClosed(1, STREAM_COUNT).forEach(i -> {
            final Path dataFile = testDir.resolve(String.format(DATA_FILE_NAME_FORMAT, i));
            LOGGER.info("Generating test data in {}", dataFile.toAbsolutePath().toString());
            NetworkMonitoringDataGenerator.generate(STREAM_ROW_COUNT, dataFile);
            dataFiles.add(dataFile);
        });
        LOGGER.info("Completed test data generation");
//        } else {
//            LOGGER.info("Using existing file");
//        }

//        assertThat(Files.isRegularFile(dataFile)).isTrue();
//        try {
//            assertThat(Files.size(dataFile) > 0).isTrue();
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        commonIndexingTestHelper.setup(dataFiles, OptionalInt.of(MAX_DOCS_PER_SHARD));

//        try {
//            Files.deleteIfExists(dataFile);
//        } catch (IOException e) {
//            throw new RuntimeException(String.format("Error deleting %s", dataFile.toAbsolutePath().toString()), e);
//        }
        LOGGER.info("-----------------doSingleSetup complete --------------");
        return true;
    }


    /**
     * Run a query that gets counts grouped by userid and assert the total count
     */
    @Test
    @Disabled
    void testGroupedCounts() {
        //we want all data here
        final ExpressionOperator.Builder expressionBuilder = ExpressionOperator.builder();
        expressionBuilder.addTerm("UserId", ExpressionTerm.Condition.EQUALS, "*");

        final List<String> componentIds = Collections.singletonList("table-1");

        // we have 20 different user IDs so should get 20 rows
        final int expectedResultCount = 20;
        final boolean extractValues = true;

        final Consumer<Map<String, List<Row>>> resultMapConsumer = resultMap -> {
            assertThat(resultMap.size()).isEqualTo(1);
            final List<Row> rows = resultMap.values().iterator().next();
            assertThat(rows.size()).isEqualTo(expectedResultCount);

            final long totalCount = rows.stream()
                    .map(row -> row.getValues().get(1)) // get the 'count' field
                    .mapToLong(Long::parseLong)
                    .sum();

            assertThat(totalCount).isEqualTo(STREAM_ROW_COUNT * STREAM_COUNT);
        };

        AbstractSearchTest.testInteractive(
                expressionBuilder,
                expectedResultCount,
                componentIds,
                this::createTableSettings,
                extractValues,
                resultMapConsumer,
                indexStore,
                searchResponseCreatorManager);

        LOGGER.info("Completed search");
    }

    private TableSettings createTableSettings(final Boolean extractValues) {

        final Column groupedUserId = Column.builder()
                .id("User")
                .name("User")
                .expression(ParamUtil.create("User"))
                .group(0)
                .build();

        final Column countColumn = Column.builder()
                .id("Count")
                .name("Count")
                .expression("count()")
                .format(Format.NUMBER)
                .build();

        final List<Column> columns = Arrays.asList(groupedUserId, countColumn);
        final DocRef resultPipeline = commonIndexingTestHelper.getSearchResultPipeline();

        return TableSettings.builder()
                .addColumns(columns)
                .extractValues(extractValues)
                .extractionPipeline(resultPipeline)
                .build();
    }
}
