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
import stroom.analytics.shared.DuplicateCheckRows;
import stroom.analytics.shared.DuplicateNotificationConfig;
import stroom.analytics.shared.FindDuplicateCheckCriteria;
import stroom.analytics.shared.NotificationConfig;
import stroom.analytics.shared.NotificationDestinationType;
import stroom.analytics.shared.NotificationStreamDestination;
import stroom.analytics.shared.QueryLanguageVersion;
import stroom.bytebuffer.impl6.ByteBufferFactory;
import stroom.bytebuffer.impl6.ByteBufferFactoryImpl;
import stroom.bytebuffer.impl6.ByteBuffers;
import stroom.docref.DocRef;
import stroom.lmdb.LmdbLibrary;
import stroom.lmdb.LmdbLibraryConfig;
import stroom.lmdb2.LmdbEnvDirFactory;
import stroom.query.api.Column;
import stroom.query.common.v2.CompiledColumns;
import stroom.query.common.v2.DuplicateCheckStoreConfig;
import stroom.query.language.functions.ExpressionContext;
import stroom.query.language.functions.FieldIndex;
import stroom.query.language.functions.Values;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.shared.PageRequest;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.Collections;
import java.util.List;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;

class TestDuplicateCheckFactoryImpl {

    private Path tempDir;

    @BeforeEach
    void setup(@TempDir final Path tempDir) {
        this.tempDir = tempDir;
    }

    @Test
    void test() {
        final Values values = Values.of("test");
        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        try (final DuplicateCheck duplicateCheck = createDuplicateCheck(duplicateCheckFactory, "test")) {
            assertThat(duplicateCheck.check(values)).isTrue();
            for (int i = 0; i < 10; i++) {
                assertThat(duplicateCheck.check(values)).isFalse();
            }
        }
    }

    @Test
    void testReload() {
        final Values values = Values.of("test");
        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        try (final DuplicateCheck duplicateCheck = createDuplicateCheck(duplicateCheckFactory, "test")) {
            assertThat(duplicateCheck.check(values)).isTrue();
            for (int i = 0; i < 10; i++) {
                assertThat(duplicateCheck.check(values)).isFalse();
            }
        }

        try (final DuplicateCheck duplicateCheck2 = createDuplicateCheck(duplicateCheckFactory, "test")) {
            for (int i = 0; i < 10; i++) {
                assertThat(duplicateCheck2.check(values)).isFalse();
            }
        }
    }

    @Test
    void testDifferentAnalytic() {
        final Values values = Values.of("test");
        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        try (final DuplicateCheck duplicateCheck1 = createDuplicateCheck(duplicateCheckFactory, "test1")) {
            assertThat(duplicateCheck1.check(values)).isTrue();
            for (int i = 0; i < 10; i++) {
                assertThat(duplicateCheck1.check(values)).isFalse();
            }
        }

        try (final DuplicateCheck duplicateCheck2 = createDuplicateCheck(duplicateCheckFactory, "test2")) {
            assertThat(duplicateCheck2.check(values)).isTrue();
            for (int i = 0; i < 10; i++) {
                assertThat(duplicateCheck2.check(values)).isFalse();
            }
        }
    }

    @Test
    void testFetchData() {
        final String analyticRuleUuid = "test";

        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        try (final DuplicateCheck duplicateCheck = createDuplicateCheck(duplicateCheckFactory, analyticRuleUuid)) {
            for (int i = 0; i < 223; i++) {
                final Values values = Values.of("test" + i);
                assertThat(duplicateCheck.check(values)).isTrue();
                assertThat(duplicateCheck.check(values)).isFalse();
            }
        }

        final FindDuplicateCheckCriteria criteria = new FindDuplicateCheckCriteria(
                PageRequest.createDefault(),
                null,
                analyticRuleUuid,
                null);
        final DuplicateCheckRows rows = duplicateCheckFactory.fetchData(criteria);
        assertThat(rows.getColumnNames().size()).isOne();
        assertThat(rows.getResultPage().size()).isEqualTo(100);
        assertThat(rows.getResultPage().getPageStart()).isEqualTo(0);
        assertThat(rows.getResultPage().getPageResponse().getTotal()).isEqualTo(223);
    }

    @Test
    void testFetchColumnNames() {
        final DuplicateCheckFactoryImpl duplicateCheckFactory = createDuplicateCheckFactory();
        final String analyticRuleUuid = "test";
        final AnalyticRuleDoc analytic = createAnalytic(analyticRuleUuid);
        assertThat(duplicateCheckFactory.fetchColumnNames(analytic.getUuid()))
                .isEmpty();

        try (final DuplicateCheck ignored = createDuplicateCheck(duplicateCheckFactory, "test")) {
            assertThat(duplicateCheckFactory.fetchColumnNames(analytic.getUuid()).get())
                    .containsExactly("test");
        }

        // Now removed from the pool
        assertThat(duplicateCheckFactory.fetchColumnNames(analytic.getUuid()).get())
                .containsExactly("test");
    }

    private DuplicateCheckFactoryImpl createDuplicateCheckFactory() {
        final TempDirProvider tempDirProvider = () -> tempDir;
        final PathCreator pathCreator = new SimplePathCreator(() -> tempDir, () -> tempDir);
        final LmdbLibraryConfig lmdbLibraryConfig = new LmdbLibraryConfig();
        final LmdbEnvDirFactory lmdbEnvDirFactory = new LmdbEnvDirFactory(
                new LmdbLibrary(pathCreator, tempDirProvider, () -> lmdbLibraryConfig), pathCreator);
        final ByteBufferFactory byteBufferFactory = new ByteBufferFactoryImpl();
        final ByteBuffers byteBuffers = new ByteBuffers(byteBufferFactory);
        final DuplicateCheckDirs duplicateCheckDirs = new DuplicateCheckDirs(
                lmdbEnvDirFactory,
                new DuplicateCheckStoreConfig());
        return new DuplicateCheckFactoryImpl(
                duplicateCheckDirs,
                byteBufferFactory,
                byteBuffers,
                new DuplicateCheckStoreConfig(),
                new DuplicateCheckRowSerde(byteBufferFactory),
                Executors::newCachedThreadPool);
    }

    private DuplicateCheck createDuplicateCheck(final DuplicateCheckFactoryImpl duplicateCheckFactory,
                                                final String ruleUUID) {
        final AnalyticRuleDoc analyticRuleDoc = createAnalytic(ruleUUID);

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

    private AnalyticRuleDoc createAnalytic(final String ruleUuid) {
        final DuplicateNotificationConfig duplicateNotificationConfig = new DuplicateNotificationConfig(
                true,
                true,
                false,
                Collections.emptyList());

        return AnalyticRuleDoc.builder()
                .uuid(ruleUuid)
                .languageVersion(QueryLanguageVersion.STROOM_QL_VERSION_0_1)
                .query("test")
                .analyticProcessType(AnalyticProcessType.SCHEDULED_QUERY)
                .notifications(createNotificationConfig())
                .errorFeed(new DocRef("Feed", "error"))
                .duplicateNotificationConfig(duplicateNotificationConfig)
                .build();
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
