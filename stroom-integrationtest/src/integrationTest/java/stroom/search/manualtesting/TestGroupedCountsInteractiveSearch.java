/*
 *
 *  * Copyright 2017 Crown Copyright
 *  *
 *  * Licensed under the Apache License, Version 2.0 (the "License");
 *  * you may not use this file except in compliance with the License.
 *  * You may obtain a copy of the License at
 *  *
 *  *     http://www.apache.org/licenses/LICENSE-2.0
 *  *
 *  * Unless required by applicable law or agreed to in writing, software
 *  * distributed under the License is distributed on an "AS IS" BASIS,
 *  * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  * See the License for the specific language governing permissions and
 *  * limitations under the License.
 *
 */

package stroom.search.manualtesting;

import org.junit.Assert;
import org.junit.Before;
import org.junit.Ignore;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.dictionary.DictionaryStore;
import stroom.entity.shared.DocRefUtil;
import stroom.index.IndexService;
import stroom.pipeline.shared.PipelineEntity;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.Row;
import stroom.query.api.v2.TableSettings;
import stroom.query.shared.v2.ParamUtil;
import stroom.search.AbstractSearchTest;
import stroom.search.CommonIndexingTest;
import stroom.search.LuceneSearchResponseCreatorManager;
import stroom.task.TaskManager;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestControl;
import stroom.util.config.StroomProperties;
import stroom.util.io.FileUtil;

import javax.inject.Inject;
import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.OptionalInt;
import java.util.function.Consumer;
import java.util.stream.IntStream;

// This spring/junit configuration is copied from AbstractCoreIntegrationTest and StroomIntegrationTest
// and it is so we can manually run tests using state from a previous run.
public class TestGroupedCountsInteractiveSearch extends AbstractCoreIntegrationTest {
    private static final Logger LOGGER = LoggerFactory.getLogger(TestGroupedCountsInteractiveSearch.class);

    private static final String DATA_FILE_NAME_PREFIX = "NetworkMonitoringBulkData_";
    private static final String DATA_FILE_NAME_SUFFIX = ".in";
    private static final String DATA_FILE_NAME_FORMAT = DATA_FILE_NAME_PREFIX + "%03d" + DATA_FILE_NAME_SUFFIX;

    private static final int STREAM_ROW_COUNT = 100_000;
    private static final int STREAM_COUNT = 100;
    private static final int MAX_DOCS_PER_SHARD = 10_000;

    @Inject
    private CommonIndexingTest commonIndexingTest;
    @Inject
    private IndexService indexService;
    @Inject
    private DictionaryStore dictionaryStore;
    @Inject
    private TaskManager taskManager;
    @Inject
    private LuceneSearchResponseCreatorManager searchResponseCreatorManager;
    @Inject
    private CommonTestControl commonTestControl;

    Path testDir = FileUtil.getTempDir();

    @Before
    public void beforeTest() throws IOException {

        LOGGER.info("Setting temp dir to {}", testDir.toAbsolutePath().toString());

        StroomProperties.setOverrideProperty(
                StroomProperties.STROOM_TEMP,
                testDir.toFile().getCanonicalPath(),
                StroomProperties.Source.TEST);
    }

    //    @Override
    protected boolean doSingleSetup() {

        List<Path> dataFiles = new ArrayList<>();
//        if (!Files.exists(dataFile)) {
        LOGGER.info("Generating test data");
        IntStream.rangeClosed(1, STREAM_COUNT).forEach(i -> {
            Path dataFile = testDir.resolve(String.format(DATA_FILE_NAME_FORMAT, i));
            LOGGER.info("Generating test data in {}", dataFile.toAbsolutePath().toString());
            NetworkMonitoringDataGenerator.generate(STREAM_ROW_COUNT, dataFile);
            dataFiles.add(dataFile);
        });
        LOGGER.info("Completed test data generation");
//        } else {
//            LOGGER.info("Using existing file");
//        }

//        Assert.assertTrue(Files.isRegularFile(dataFile));
//        try {
//            Assert.assertTrue(Files.size(dataFile) > 0);
//        } catch (IOException e) {
//            throw new RuntimeException(e);
//        }
        commonIndexingTest.setup(dataFiles, OptionalInt.of(MAX_DOCS_PER_SHARD));

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
    @Ignore
    public void testGroupedCounts() {
        //we want all data here
        final ExpressionOperator.Builder expressionBuilder = new ExpressionOperator.Builder();
        expressionBuilder.addTerm("UserId", ExpressionTerm.Condition.CONTAINS, "*");

        final List<String> componentIds = Collections.singletonList("table-1");

        // we have 20 different user IDs so should get 20 rows
        int expectedResultCount = 20;
        boolean extractValues = true;

        Consumer<Map<String, List<Row>>> resultMapConsumer = resultMap -> {
            Assert.assertEquals(1, resultMap.size());
            List<Row> rows = resultMap.values().iterator().next();
            Assert.assertEquals(expectedResultCount, rows.size());

            long totalCount = rows.stream()
                    .map(row -> row.getValues().get(1)) // get the 'count' field
                    .mapToLong(Long::parseLong)
                    .sum();

            Assert.assertEquals(STREAM_ROW_COUNT * STREAM_COUNT, totalCount);
        };

        setProperties();

        AbstractSearchTest.testInteractive(
                expressionBuilder,
                expectedResultCount,
                componentIds,
                this::createTableSettings,
                extractValues,
                resultMapConsumer,
                5,
                5,
                indexService,
                searchResponseCreatorManager);

        LOGGER.info("Completed search");
    }

    private void setProperties() {
        StroomProperties.setOverrideProperty(
                "stroom.search.shard.maxThreads",
                "5",
                StroomProperties.Source.TEST);

        StroomProperties.setOverrideProperty(
                "stroom.search.extraction.maxThreads",
                "5",
                StroomProperties.Source.TEST);

        StroomProperties.setOverrideProperty(
                "stroom.search.shard.maxOpen",
                "10",
                StroomProperties.Source.TEST);
    }


    private TableSettings createTableSettings(Boolean extractValues) {

        final Field groupedUserId = new Field.Builder()
                .name("User")
                .expression(ParamUtil.makeParam("User"))
                .group(0)
                .build();

        final Field countField = new Field.Builder()
                .name("Count")
                .expression("count()")
                .format(new Format(Format.Type.NUMBER))
                .build();

        List<Field> fields = Arrays.asList(groupedUserId, countField);
        final PipelineEntity resultPipeline = commonIndexingTest.getSearchResultPipeline();

        final TableSettings tableSettings = new TableSettings.Builder()
                .addFields(fields)
                .extractValues(extractValues)
                .extractionPipeline(DocRefUtil.create(resultPipeline))
                .build();

        return tableSettings;
    }


    @Test
    @Ignore
    public void tearDownAndSetupOnly() {
        LOGGER.info("before() - commonTestControl.setup()");
        commonTestControl.teardown();
        commonTestControl.setup();

        doSingleSetup();
    }


}
