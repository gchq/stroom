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
import stroom.dashboard.expression.v1.ValString;
import stroom.lmdb.LmdbEnvFactory;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.query.api.v2.Field;
import stroom.query.api.v2.Format;
import stroom.query.api.v2.OffsetRange;
import stroom.query.api.v2.ParamUtil;
import stroom.query.api.v2.QueryKey;
import stroom.query.api.v2.ResultRequest;
import stroom.query.api.v2.TableResult;
import stroom.query.api.v2.TableSettings;
import stroom.query.common.v2.format.FieldFormatter;
import stroom.query.common.v2.format.FormatterFactory;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

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

    private final Sizes defaultMaxResultsSizes = Sizes.create(50);
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
    DataStore create(final TableSettings tableSettings, final Sizes maxResults, final Sizes storeSize) {
        final FieldIndex fieldIndex = new FieldIndex();

        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final ResultStoreConfig resultStoreConfig = new ResultStoreConfig();
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvFactory lmdbEnvFactory = new LmdbEnvFactory(
                pathCreator,
                tempDirProvider,
                () -> lmdbLibraryConfig);

        return new LmdbDataStore(
                lmdbEnvFactory,
                resultStoreConfig,
                new QueryKey(UUID.randomUUID().toString()),
                "0",
                tableSettings,
                fieldIndex,
                Collections.emptyMap(),
                maxResults,
                false,
                () -> executorService,
                new ErrorConsumerImpl());
    }

    @Test
    void testBigValues() {
        final FormatterFactory formatterFactory = new FormatterFactory(null);
        final FieldFormatter fieldFormatter = new FieldFormatter(formatterFactory);

        final TableSettings tableSettings = TableSettings.builder()
                .addFields(Field.builder()
                        .id("Text")
                        .name("Text")
                        .expression(ParamUtil.makeParam("Text"))
                        .format(Format.TEXT)
                        .group(0)
                        .build())
                .addFields(Field.builder()
                        .id("Text2")
                        .name("Text2")
                        .expression(ParamUtil.makeParam("Text2"))
                        .format(Format.TEXT)
                        .build())
                .build();

        final DataStore dataStore = create(tableSettings);

        for (int i = 0; i < 3000; i++) {
            final Val val = ValString.create("Text " + i + "test".repeat(1000));
            final Val[] values = new Val[2];
            values[0] = val;
            values[1] = val;
            dataStore.add(values);
        }

        // Wait for all items to be added.
        try {
            dataStore.getCompletionState().signalComplete();
            dataStore.getCompletionState().awaitCompletion();
        } catch (final InterruptedException e) {
            throw new RuntimeException(e.getMessage(), e);
        }

        // Make sure we only get 50 results.
        final ResultRequest tableResultRequest = ResultRequest.builder()
                .componentId("componentX")
                .addMappings(tableSettings)
                .requestedRange(new OffsetRange(0, 3000))
                .build();
        final TableResultCreator tableComponentResultCreator = new TableResultCreator(
                fieldFormatter,
                defaultMaxResultsSizes);
        final TableResult searchResult = (TableResult) tableComponentResultCreator.create(
                dataStore,
                tableResultRequest);
        assertThat(searchResult.getTotalResults().intValue()).isEqualTo(50);
    }
}
