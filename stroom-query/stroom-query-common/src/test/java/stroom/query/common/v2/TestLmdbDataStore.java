/*
 * Copyright 2017 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *    http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.query.common.v2;

import stroom.dashboard.expression.v1.FieldIndex;
import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValLong;
import stroom.dashboard.expression.v1.ValString;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbEnvFactory.SimpleEnvBuilder;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamSubstituteUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.SearchRequestSource;
import stroom.query.api.v2.SearchRequestSource.SourceType;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.Metrics;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.UUID;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class TestLmdbDataStore extends AbstractDataStoreTest {

    private Path tempDir;
    private ExecutorService executorService;

    @BeforeEach
    void setup(@TempDir final Path tempDir) {
        this.tempDir = tempDir;
        executorService = Executors.newCachedThreadPool();
    }

    @AfterEach
    void after() {
        executorService.shutdown();
    }

    @Override
    DataStore create(final SearchRequestSource searchRequestSource,
                     final QueryKey queryKey,
                     final String componentId,
                     final TableSettings tableSettings,
                     final AbstractResultStoreConfig resultStoreConfig,
                     final DataStoreSettings dataStoreSettings) {
        final FieldIndex fieldIndex = new FieldIndex();

        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvFactory lmdbEnvFactory = new LmdbEnvFactory(
                pathCreator,
                tempDirProvider,
                () -> lmdbLibraryConfig);
        final SimpleEnvBuilder lmdbEnvBuilder = lmdbEnvFactory.builder(resultStoreConfig.getLmdbConfig());

        final ErrorConsumerImpl errorConsumer = new ErrorConsumerImpl();
        final Serialisers serialisers = new Serialisers(resultStoreConfig);
        return new LmdbDataStore(
                searchRequestSource,
                serialisers,
                lmdbEnvBuilder,
                resultStoreConfig,
                queryKey,
                componentId,
                tableSettings,
                fieldIndex,
                Collections.emptyMap(),
                dataStoreSettings,
                () -> executorService,
                errorConsumer);
    }

    @Test
    void testBigValues() {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        final TableSettings tableSettings = TableSettings.builder()
                .addFields(Field.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamSubstituteUtil.makeParam("Text"))
                        .format(Format.TEXT)
                        .group(0)
                        .build())
                .addFields(Field.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression(ParamSubstituteUtil.makeParam("Text2"))
                        .format(Format.TEXT)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        Metrics.setEnabled(true);
        Metrics.measure("Added data", () -> {
            for (int i = 0; i < 300_000; i++) {
                final Val val = ValString.create("Text " + i + "test".repeat(1000));
                dataStore.add(Val.of(val, val));
            }

            // Wait for all items to be added.
            try {
                dataStore.getCompletionState().signalComplete();
                dataStore.getCompletionState().awaitCompletion();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e.getMessage(), e);
            }
        });
        Metrics.report();

        Metrics.measure("Retrieved data", () -> {
            // Make sure we only get 3000 results.
            final ResultRequest tableResultRequest = ResultRequest.builder()
                    .componentId("componentX")
                    .addMappings(tableSettings)
                    .requestedRange(new OffsetRange(0, 3000))
                    .build();
            final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                    fieldFormatter);
            final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                    dataStore,
                    tableResultRequest);
            assertThat(searchResult.getResultRange().getLength()).isEqualTo(3000);
            assertThat(searchResult.getTotalResults().intValue()).isEqualTo(300_000);
        });
        Metrics.report();
    }

    @Test
    void testReload() throws Exception {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        final TableSettings tableSettings = TableSettings.builder()
                .addFields(Field.builder()
                        .id("StreamId")
                        .name("StreamId")
                        .expression(ParamSubstituteUtil.makeParam("StreamId"))
                        .format(Format.NUMBER)
                        .build())
                .addFields(Field.builder()
                        .id("EventId")
                        .name("EventId")
                        .expression(ParamSubstituteUtil.makeParam("EventId"))
                        .format(Format.NUMBER)
                        .build())
                .addFields(Field.builder()
                        .id("EventTime")
                        .name("EventTime")
                        .expression(ParamSubstituteUtil.makeParam("EventTime"))
                        .format(Format.DATE_TIME)
                        .build())
                .build();

        final QueryKey queryKey = new QueryKey(UUID.randomUUID().toString());
        final AbstractResultStoreConfig resultStoreConfig = new AnalyticResultStoreConfig();
        final DataStoreSettings dataStoreSettings = DataStoreSettings.createAnalyticStoreSettings();
        final SearchRequestSource searchRequestSource = SearchRequestSource
                .builder()
                .sourceType(SourceType.ANALYTIC_RULE)
                .build();
        LmdbDataStore dataStore = (LmdbDataStore)
                create(
                        searchRequestSource,
                        queryKey,
                        "0",
                        tableSettings,
                        resultStoreConfig,
                        dataStoreSettings);

        for (int i = 1; i <= 100; i++) {
            for (int j = 1; j <= 100; j++) {
                final Val streamId = ValLong.create(i);
                final Val eventId = ValLong.create(j);
                final Val eventTime = ValLong.create(System.currentTimeMillis());
                dataStore.add(Val.of(streamId, eventId, eventTime));
            }
        }

        // Wait for all items to be added.
        CurrentDbState currentDbState = dataStore.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(100);
        assertThat(currentDbState.getEventId()).isEqualTo(100);

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("0")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 50))
                .build();
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                fieldFormatter);
        TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(10000);

        // Close the db.
        dataStore.getCompletionState().signalComplete();
        dataStore.getCompletionState().awaitCompletion();
        dataStore.close();

        // Try and open the datastore again.
        LmdbDataStore dataStore2 = (LmdbDataStore)
                create(
                        searchRequestSource,
                        queryKey,
                        "0",
                        tableSettings,
                        resultStoreConfig,
                        dataStoreSettings);

        currentDbState = dataStore2.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(100);
        assertThat(currentDbState.getEventId()).isEqualTo(100);

        // Make sure we only get 50 results.
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore2,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(10000);

        // Load some more data.
        for (int i = 101; i <= 200; i++) {
            for (int j = 101; j <= 200; j++) {
                final Val streamId = ValLong.create(i);
                final Val eventId = ValLong.create(j);
                final Val eventTime = ValLong.create(System.currentTimeMillis());
                dataStore2.add(Val.of(streamId, eventId, eventTime));
            }
        }

        // Wait for all items to be added.
        currentDbState = dataStore2.sync();
        assertThat(currentDbState.getStreamId()).isEqualTo(200);
        assertThat(currentDbState.getEventId()).isEqualTo(200);

        // Make sure we still only get 50 results.
        searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore2,
                tableResultRequest);
        assertThat(searchResult.getResultRange().getLength()).isEqualTo(50);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(20000);
    }

    @Test
    void basicTest() {
        super.basicTest();
    }

    @Test
    void noValuesTest() {
        super.noValuesTest();
    }

    @Test
    void sortedTextTest() {
        super.sortedTextTest();
    }

    @Test
    void sortedNumberTest() {
        super.sortedNumberTest();
    }

    @Test
    void sortedCountedTextTest1() {
        super.sortedCountedTextTest1();
    }

    @Test
    void sortedCountedTextTest2() {
        super.sortedCountedTextTest2();
    }

    @Test
    void sortedCountedTextTest3() {
        super.sortedCountedTextTest3();
    }

    @Test
    void firstLastSelectorTest() {
        super.firstLastSelectorTest();
    }
}
