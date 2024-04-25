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

package stroom.analytics.impl;

import stroom.analytics.shared.AnalyticProcessType;
import stroom.analytics.shared.AnalyticRuleDoc;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.docref.DocRef;
import stroom.expression.api.ExpressionContext;
import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnvFactory2;
import stroom.query.api.v2.Column;
import stroom.query.api.v2.Row;
import stroom.query.common.v2.AnalyticResultStoreConfig;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.language.functions.FieldIndex;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class TestDuplicateCheckFactory {

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

    @Test
    void test() {
        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        final DuplicateCheck duplicateCheck = createDuplicateCheck(duplicateCheckFactory);
        final Row row = new Row("test", List.of("test"), 0, "", "");
        assertThat(duplicateCheck.check(row)).isTrue();
        for (int i = 0; i < 10; i++) {
            assertThat(duplicateCheck.check(row)).isFalse();
        }
    }

    @Test
    void testReload() {
        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        final DuplicateCheck duplicateCheck = createDuplicateCheck(duplicateCheckFactory);
        final Row row = new Row("test", List.of("test"), 0, "", "");
        assertThat(duplicateCheck.check(row)).isTrue();
        for (int i = 0; i < 10; i++) {
            assertThat(duplicateCheck.check(row)).isFalse();
        }
        duplicateCheckFactory.close();

        final DuplicateCheckFactoryImpl duplicateCheckFactory2 = createDuplicateCheckFactory();
        final DuplicateCheck duplicateCheck2 = createDuplicateCheck(duplicateCheckFactory2);
        for (int i = 0; i < 10; i++) {
            assertThat(duplicateCheck2.check(row)).isFalse();
        }
    }

    private DuplicateCheckFactoryImpl createDuplicateCheckFactory() {
        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvFactory2 lmdbEnvFactory = new LmdbEnvFactory2(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig),
                pathCreator);
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        return new DuplicateCheckFactoryImpl(
                lmdbEnvFactory,
                () -> executorService,
                byteBufferFactory,
                new AnalyticResultStoreConfig());
    }

    private DuplicateCheck createDuplicateCheck(final DuplicateCheckFactoryImpl duplicateCheckFactory) {
        final AnalyticRuleDoc analyticRuleDoc = AnalyticRuleDoc.builder()
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query("test")
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .notifications(createNotificationConfig())
                .errorFeed(new DocRef("Feed", "error"))
                .build();

        final Column column = Column
                .builder()
                .id("test")
                .name("test")
                .expression("${Number}")
                .build();
        final List<Column> columns = List.of(column);
        final FieldIndex fieldIndex = new FieldIndex();
        final CompiledColumns compiledColumns = CompiledColumns
                .create(new ExpressionContext(), columns, fieldIndex, Collections.emptyMap());
        return duplicateCheckFactory.create(analyticRuleDoc, compiledColumns);
    }

    protected List<NotificationConfig> createNotificationConfig() {
        final NotificationConfig notificationConfig = NotificationConfig
                .builder()
                .destinationType(NotificationDestinationType.STREAM)
                .destination(NotificationStreamDestination.builder()
                        .destinationFeed(new DocRef("Feed", "test"))
                        .useSourceFeedIfPossible(false)
                        .build())
                .build();
        return List.of(notificationConfig);
    }
}
