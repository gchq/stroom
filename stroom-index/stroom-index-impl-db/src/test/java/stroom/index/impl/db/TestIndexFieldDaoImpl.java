package stroom.index.impl.db;

import stroom.datasource.api.v2.AnalyzerType;
import stroom.datasource.api.v2.FieldType;
import stroom.datasource.api.v2.FindFieldCriteria;
import stroom.datasource.api.v2.IndexField;
import stroom.db.util.JooqUtil;
import stroom.docref.DocRef;
import stroom.docref.StringMatch;
import stroom.docref.StringMatch.MatchType;
import stroom.index.impl.IndexFieldDao;
import stroom.index.shared.IndexFieldImpl;
import stroom.index.shared.LuceneIndexDoc;
import stroom.test.common.TestUtil;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.TypeLiteral;
import jakarta.inject.Inject;
import org.jooq.Record1;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.DynamicTest;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestFactory;
import org.junit.jupiter.api.parallel.Execution;
import org.junit.jupiter.api.parallel.ExecutionMode;

import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static stroom.db.util.JooqUtil.count;
import static stroom.index.impl.db.jooq.tables.IndexField.INDEX_FIELD;
import static stroom.index.impl.db.jooq.tables.IndexFieldSource.INDEX_FIELD_SOURCE;

class TestIndexFieldDaoImpl {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestIndexFieldDaoImpl.class);

    private static final DocRef DOC_REF_1 = DocRef.builder()
            .uuid("a8c078d8-b2e7-41e6-b254-d4870ab75ab8")
            .type(LuceneIndexDoc.DOCUMENT_TYPE)
            .name("foo")
            .build();

    private static final DocRef DOC_REF_2 = DocRef.builder()
            .uuid("05da4d75-28d4-48ad-9195-9302cfacbc1c")
            .type(LuceneIndexDoc.DOCUMENT_TYPE)
            .name("bar")
            .build();

    private static final IndexField FIELD_1 = IndexFieldImpl.builder()
            .fldName("ID")
            .fldType(FieldType.ID)
            .analyzerType(AnalyzerType.NUMERIC)
            .build();
    private static final IndexField FIELD_2 = IndexFieldImpl.builder()
            .fldName("Name")
            .fldType(FieldType.TEXT)
            .analyzerType(AnalyzerType.ALPHA_NUMERIC)
            .build();
    private static final IndexField FIELD_3 = IndexFieldImpl.builder()
            .fldName("State")
            .fldType(FieldType.BOOLEAN)
            .analyzerType(AnalyzerType.KEYWORD)
            .build();

    @Inject
    IndexFieldDao indexFieldDao;
    @Inject
    IndexDbConnProvider indexDbConnProvider;

    @BeforeEach
    void setUp() throws SQLException {
        final Injector injector = Guice.createInjector(
                new IndexDbModule(),
                new IndexDaoModule(),
                new TestModule());
        injector.injectMembers(this);

        JooqUtil.transaction(indexDbConnProvider, context -> {
            LOGGER.info("Tear down");
            context.deleteFrom(INDEX_FIELD).execute();
            context.deleteFrom(INDEX_FIELD_SOURCE).execute();

            assertThat(count(context, INDEX_FIELD))
                    .isEqualTo(0);
            assertThat(count(context, INDEX_FIELD_SOURCE))
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

        CompletableFuture.runAsync(() -> {
            JooqUtil.context(indexDbConnProvider, context -> {
                try {
                    startLatch.await();
                } catch (InterruptedException e) {
                    throw new RuntimeException(e);
                }

                final Record1<Integer> rec = context.select(INDEX_FIELD_SOURCE.ID)
                        .from(INDEX_FIELD_SOURCE)
                        .where(INDEX_FIELD_SOURCE.UUID.eq(DOC_REF_1.getUuid()))
                        .and(INDEX_FIELD_SOURCE.TYPE.eq(DOC_REF_1.getType()))
                        .fetchOne();

                final Integer id = rec.get(INDEX_FIELD_SOURCE.ID);

                LOGGER.info("id {}", id);
            });
        });

        CompletableFuture.runAsync(() -> {
            JooqUtil.context(indexDbConnProvider, context -> {
                context.select(INDEX_FIELD_SOURCE.ID)
                        .from(INDEX_FIELD_SOURCE)
                        .where(INDEX_FIELD_SOURCE.UUID.eq(DOC_REF_1.getUuid()))
                        .and(INDEX_FIELD_SOURCE.TYPE.eq(DOC_REF_1.getType()))
                        .forUpdate()
                        .fetch();
                LOGGER.info("Done lock");
                startLatch.countDown();

                ThreadUtil.sleepIgnoringInterrupts(5_000);
            });
        }).get();
    }

    /**
     * Make sure multiple threads can concurrently add different lists of fields
     */
    @Test
    void testMultiThread() throws ExecutionException, InterruptedException {
        final int threads = 10;
        final ExecutorService executorService = Executors.newFixedThreadPool(threads);
        final CountDownLatch startLatch = new CountDownLatch(1);

        JooqUtil.context(indexDbConnProvider, context -> {
            assertThat(count(context, INDEX_FIELD))
                    .isEqualTo(0);
        });

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

    @TestFactory
    @Execution(ExecutionMode.SAME_THREAD)
    Stream<DynamicTest> testCaseSensitivity() {
        final AtomicBoolean hasSetupRun = new AtomicBoolean(false);

        return TestUtil.buildDynamicTestStream()
                .withInputType(StringMatch.class)
                .withWrappedOutputType(new TypeLiteral<Set<String>>() {
                })
                .withTestFunction(testCase -> {
                    final StringMatch stringMatch = testCase.getInput();
                    return getFields(DOC_REF_1, stringMatch)
                            .stream()
                            .map(IndexField::getFldName)
                            .collect(Collectors.toSet());
                })
                .withSimpleEqualityAssertion()
                .withBeforeTestCaseAction(() -> {
                    if (!hasSetupRun.get()) {

                        assertThat(getFields(DOC_REF_1))
                                .isEmpty();
                        final IndexField foo = idField("foo");
                        final IndexField bar = idField("bar");

                        // Fields are not changed between tests
                        indexFieldDao.addFields(DOC_REF_1, List.of(
                                foo,
                                bar));

                        assertThat(getFields(DOC_REF_1))
                                .hasSize(2);

                        hasSetupRun.set(true);
                    }
                })
                .addCase(StringMatch.any(), Set.of("foo", "bar"))
                .addCase(StringMatch.nonNull(), Set.of("foo", "bar"))

                .addCase(StringMatch.equals("foo"), Set.of("foo"))
                .addCase(StringMatch.equals("FOO"), Collections.emptySet())
                .addCase(StringMatch.equalsIgnoreCase("foo"), Set.of("foo"))
                .addCase(StringMatch.equalsIgnoreCase("FOO"), Set.of("foo"))

                .addCase(StringMatch.notEquals("foo"), Set.of("bar"))
                .addCase(StringMatch.notEquals("FOO"), Set.of("foo", "bar"))
                .addCase(StringMatch.notEqualsIgnoreCase("foo"), Set.of("bar"))
                .addCase(StringMatch.notEqualsIgnoreCase("FOO"), Set.of("bar"))

                .addCase(StringMatch.contains("oo"), Set.of("foo"))
                .addCase(StringMatch.contains("OO"), Collections.emptySet())
                .addCase(StringMatch.containsIgnoreCase("oo"), Set.of("foo"))
                .addCase(StringMatch.containsIgnoreCase("OO"), Set.of("foo"))

                .addCase(StringMatch.regex("^fo"), Set.of("foo"))
                .addCase(StringMatch.regex("^FO"), Collections.emptySet())
                .addCase(StringMatch.regexIgnoreCase("^fo"), Set.of("foo"))
                .addCase(StringMatch.regexIgnoreCase("^FO"), Set.of("foo"))

                .addCase(new StringMatch(MatchType.STARTS_WITH, true, "fo"), Set.of("foo"))
                .addCase(new StringMatch(MatchType.STARTS_WITH, true, "FO"), Collections.emptySet())
                .addCase(new StringMatch(MatchType.STARTS_WITH, false, "fo"), Set.of("foo"))
                .addCase(new StringMatch(MatchType.STARTS_WITH, false, "FO"), Set.of("foo"))

                .addCase(new StringMatch(MatchType.ENDS_WITH, true, "oo"), Set.of("foo"))
                .addCase(new StringMatch(MatchType.ENDS_WITH, true, "OO"), Collections.emptySet())
                .addCase(new StringMatch(MatchType.ENDS_WITH, false, "oo"), Set.of("foo"))
                .addCase(new StringMatch(MatchType.ENDS_WITH, false, "OO"), Set.of("foo"))

                .build();
    }


    private List<IndexField> getFields(final DocRef docRef) {
        final ResultPage<IndexField> resultPage = indexFieldDao.findFields(
                new FindFieldCriteria(
                        new PageRequest(),
                        Collections.emptyList(),
                        docRef));
        return resultPage.getValues();
    }

    private List<IndexField> getFields(final DocRef docRef, final StringMatch stringMatch) {
        final ResultPage<IndexField> resultPage = indexFieldDao.findFields(
                new FindFieldCriteria(
                        new PageRequest(),
                        Collections.emptyList(),
                        docRef,
                        stringMatch,
                        null));
        return resultPage.getValues();
    }

    private static IndexField idField(final String fieldName) {
        return IndexFieldImpl.builder()
                .fldName(fieldName)
                .fldType(FieldType.ID)
                .analyzerType(AnalyzerType.NUMERIC)
                .build();
    }
}
