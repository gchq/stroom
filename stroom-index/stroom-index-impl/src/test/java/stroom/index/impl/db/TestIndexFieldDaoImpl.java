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

package stroom.index.impl.db;

import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.index.impl.IndexFieldDao;
import stroom.index.shared.AddField;
import stroom.index.shared.DeleteField;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.index.shared.UpdateField;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.FindFieldCriteria;
import stroom.query.api.datasource.IndexField;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import jakarta.inject.Inject;
import org.jooq.Record1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static stroom.index.impl.db.jooq.tables.IndexField.INDEX_FIELD;
import static stroom.index.impl.db.jooq.tables.IndexFieldSource.INDEX_FIELD_SOURCE;

class TestIndexFieldDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestIndexFieldDaoImpl.class);

    private static final DocRef DOC_REF_1 = DocRef.builder()
            .uuid("a8c078d8-b2e7-41e6-b254-d4870ab75ab8")
            .type(LuceneIndexDoc.TYPE)
            .name("foo")
            .build();

    private static final DocRef DOC_REF_2 = DocRef.builder()
            .uuid("05da4d75-28d4-48ad-9195-9302cfacbc1c")
            .type(LuceneIndexDoc.TYPE)
            .name("bar")
            .build();

    private static final IndexFieldImpl FIELD_1 = IndexFieldImpl.builder()
            .fldName("ID")
            .fldType(FieldType.ID)
            .analyzerType(AnalyzerType.NUMERIC)
            .build();
    private static final IndexFieldImpl FIELD_2 = IndexFieldImpl.builder()
            .fldName("Name")
            .fldType(FieldType.TEXT)
            .analyzerType(AnalyzerType.ALPHA_NUMERIC)
            .build();
    private static final IndexFieldImpl FIELD_3 = IndexFieldImpl.builder()
            .fldName("State")
            .fldType(FieldType.BOOLEAN)
            .analyzerType(AnalyzerType.KEYWORD)
            .build();

    @Inject
    IndexFieldDao indexFieldDao;
    @Inject
    IndexDbConnProvider indexDbConnProvider;

    @BeforeEach
    void setUp() {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        injector.injectMembers(this);

        JooqUtil.transaction(indexDbConnProvider, context -> {
            LOGGER.info("Tear down");
            context.deleteFrom(INDEX_FIELD).execute();
            context.deleteFrom(INDEX_FIELD_SOURCE).execute();

            assertThat(JooqUtil.count(context, INDEX_FIELD))
                    .isEqualTo(0);
            assertThat(JooqUtil.count(context, INDEX_FIELD_SOURCE))
                    .isEqualTo(0);
        });
    }

    @Test
    void addFields() {
        List<IndexField> fields = getFields(DOC_REF_1);

        assertThat(fields.size())
                .isEqualTo(0);

        indexFieldDao.addFields(DOC_REF_1, List.of(FIELD_1));

        fields = getFields(DOC_REF_1);

        assertThat(fields.size())
                .isEqualTo(1);

        // Now add all three fields, so field 1 is ignored
        indexFieldDao.addFields(DOC_REF_1, List.of(FIELD_1, FIELD_2, FIELD_3));

        fields = getFields(DOC_REF_1);

        assertThat(fields.size())
                .isEqualTo(3);
    }

    @Disabled // Verifying lock behaviour
    @Test
    void test() throws ExecutionException, InterruptedException {
        indexFieldDao.addFields(DOC_REF_1, List.of(FIELD_1));

        final CountDownLatch startLatch = new CountDownLatch(1);

        CompletableFuture.runAsync(() -> JooqUtil.context(indexDbConnProvider, context -> {
            try {
                startLatch.await();
            } catch (final InterruptedException e) {
                throw new RuntimeException(e);
            }

            final Record1<Integer> rec = context
                    .select(INDEX_FIELD_SOURCE.ID)
                    .from(INDEX_FIELD_SOURCE)
                    .where(INDEX_FIELD_SOURCE.UUID.eq(DOC_REF_1.getUuid()))
                    .and(INDEX_FIELD_SOURCE.TYPE.eq(DOC_REF_1.getType()))
                    .fetchOne();

            final Integer id = rec.get(INDEX_FIELD_SOURCE.ID);
            LOGGER.info("id {}", id);
        }));

        CompletableFuture.runAsync(() -> JooqUtil.context(indexDbConnProvider, context -> {
            context.select(INDEX_FIELD_SOURCE.ID)
                    .from(INDEX_FIELD_SOURCE)
                    .where(INDEX_FIELD_SOURCE.UUID.eq(DOC_REF_1.getUuid()))
                    .and(INDEX_FIELD_SOURCE.TYPE.eq(DOC_REF_1.getType()))
                    .forUpdate()
                    .fetch();
            LOGGER.info("Done lock");
            startLatch.countDown();

            ThreadUtil.sleepIgnoringInterrupts(5_000);
        })).get();
    }

    /**
     * Make sure multiple threads can concurrently add different lists of fields
     */
    @Test
    void testMultiThread() throws ExecutionException, InterruptedException {
        final int threads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final CountDownLatch startLatch = new CountDownLatch(1);

        JooqUtil.context(indexDbConnProvider, context -> assertThat(JooqUtil.count(context, INDEX_FIELD))
                .isEqualTo(0));

        final List<CompletableFuture<?>> futures = new ArrayList<>();
        for (int i = 0; i < threads; i++) {
            final int finalI = i;
            final CompletableFuture<Void> future = CompletableFuture.runAsync(
                    ThrowingRunnable.unchecked(() -> {
                        final List<IndexField> fields = finalI % 3 == 0
                                ? List.of(FIELD_1, FIELD_3)
                                : List.of(FIELD_2, FIELD_3);
                        startLatch.await();
                        final DocRef docRef = finalI % 2 == 0
                                ? DOC_REF_1
                                : DOC_REF_2;
                        indexFieldDao.addFields(docRef, fields);
                        LOGGER.debug("Thread {} complete", finalI);
                    }), executorService);
            futures.add(future);
        }
        startLatch.countDown();

        CompletableFuture.allOf(futures.toArray(new CompletableFuture[0]))
                .get();

        List<IndexField> fields = getFields(DOC_REF_1);
        assertThat(fields.size())
                .isEqualTo(3);

        fields = getFields(DOC_REF_2);
        assertThat(fields.size())
                .isEqualTo(3);
    }

    @Test
    void findFields() {
        List<IndexField> fields = getFields(DOC_REF_1);
        assertThat(fields.size())
                .isEqualTo(0);

        indexFieldDao.addFields(DOC_REF_1, List.of(FIELD_1, FIELD_2, FIELD_3));
        indexFieldDao.addFields(DOC_REF_2, List.of(FIELD_3));

        fields = getFields(DOC_REF_1);
        assertThat(fields.size())
                .isEqualTo(3);

        fields = getFields(DOC_REF_2);
        assertThat(fields.size())
                .isEqualTo(1);
    }

    @Test
    void addUpdateDeleteFields() {
        final List<IndexField> fields = getFields(DOC_REF_1);
        assertThat(fields.size())
                .isEqualTo(0);

        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_1));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(1);
        assertThatThrownBy(() -> indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_1)))
                .isInstanceOf(RuntimeException.class);
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(1);
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_2));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(2);
        indexFieldDao.updateField(new UpdateField(DOC_REF_1, FIELD_1.getFldName(), FIELD_3));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(2);
        assertThatThrownBy(() -> indexFieldDao.updateField(new UpdateField(DOC_REF_1, FIELD_3.getFldName(), FIELD_2)))
                .isInstanceOf(RuntimeException.class);
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(2);
        indexFieldDao.deleteField(new DeleteField(DOC_REF_1, FIELD_3.getFldName()));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(1);
        indexFieldDao.deleteField(new DeleteField(DOC_REF_1, FIELD_1.getFldName()));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(1);
    }

    @Test
    void textDeleteAll() {
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(0);
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_1));
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_2));
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_3));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(3);
        indexFieldDao.deleteAll(DOC_REF_1);
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(0);
    }

    @Test
    void textCopyAll() {
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(0);
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_1));
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_2));
        indexFieldDao.addField(new AddField(DOC_REF_1, FIELD_3));
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(3);

        assertThat(indexFieldDao.getFieldCount(DOC_REF_2)).isEqualTo(0);
        indexFieldDao.copyAll(DOC_REF_1, DOC_REF_2);
        assertThat(indexFieldDao.getFieldCount(DOC_REF_1)).isEqualTo(3);
        assertThat(indexFieldDao.getFieldCount(DOC_REF_2)).isEqualTo(3);
    }

    private List<IndexField> getFields(final DocRef docRef) {
        final ResultPage<IndexField> resultPage = indexFieldDao.findFields(
                new FindFieldCriteria(
                        new PageRequest(),
                        FindFieldCriteria.DEFAULT_SORT_LIST,
                        docRef));
        return resultPage.getValues();
    }
}
