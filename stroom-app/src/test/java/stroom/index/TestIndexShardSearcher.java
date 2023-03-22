package stroom.index;

import stroom.dashboard.expression.v1.Val;
import stroom.dashboard.expression.v1.ValString;
import stroom.dashboard.expression.v1.Values;
import stroom.dashboard.expression.v1.ValuesConsumer;
import stroom.datasource.api.v2.TextField;
import stroom.docref.DocRef;
import stroom.index.impl.FieldTypeFactory;
import stroom.index.impl.IndexShardKeyUtil;
import stroom.index.impl.IndexShardService;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.IndexShardWriterCache;
import stroom.index.impl.IndexStore;
import stroom.index.impl.Indexer;
import stroom.index.impl.LuceneVersionUtil;
import stroom.index.shared.FindIndexShardCriteria;
import stroom.index.shared.IndexDoc;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexFieldsMap;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.query.api.v2.ExpressionOperator;
import stroom.query.api.v2.ExpressionTerm.Condition;
import stroom.query.api.v2.QueryKey;
import stroom.query.common.v2.SearchProgressLog;
import stroom.query.common.v2.SearchProgressLog.SearchPhase;
import stroom.search.impl.SearchExpressionQueryBuilder;
import stroom.search.impl.SearchExpressionQueryBuilder.SearchExpressionQuery;
import stroom.search.impl.shard.DocIdQueue;
import stroom.search.impl.shard.IndexShardHitCollector;
import stroom.search.impl.shard.IndexShardSearcher;
import stroom.task.api.SimpleTaskContext;
import stroom.task.api.TaskTerminatedException;
import stroom.test.AbstractCoreIntegrationTest;
import stroom.test.CommonTestScenarioCreator;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.ResultPage;

import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexableField;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.SearcherManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.LongAdder;
import javax.inject.Inject;

import static org.assertj.core.api.Assertions.assertThat;

public class TestIndexShardSearcher extends AbstractCoreIntegrationTest {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestIndexShardSearcher.class);

    @Inject
    private CommonTestScenarioCreator commonTestScenarioCreator;
    @Inject
    private IndexShardService indexShardService;
    @Inject
    private IndexShardWriterCache indexShardWriterCache;
    @Inject
    private Indexer indexer;
    @Inject
    private IndexStore indexStore;
    @Inject
    private PathCreator pathCreator;

    @BeforeEach
    void onBefore() {
        indexShardWriterCache.shutdown();
    }

    @Disabled
    @Test
    void test() {
        assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isZero();

        // Create an index
        final DocRef indexRef = commonTestScenarioCreator.createIndex("TEST_2010a");
        final IndexDoc index = indexStore.readDocument(indexRef);
        final IndexShardKey indexShardKey = IndexShardKeyUtil.createTestKey(index);

        // Create and close writer.
        final IndexShardWriter writer = indexShardWriterCache.getWriterByShardKey(indexShardKey);
        indexShardWriterCache.close(writer);

        // Get shard.
        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
        criteria.getIndexUuidSet().add(indexShardKey.getIndexUuid());
        criteria.getPartition().setString(indexShardKey.getPartition().getLabel());
        final ResultPage<IndexShard> indexShardResultPage = indexShardService.find(criteria);
        assertThat(indexShardResultPage.size()).isOne();
        final IndexShard indexShard = indexShardResultPage.getFirst();

        final int indexThreads = 10;
        final int searchThreads = 10;
        final CompletableFuture<?>[] futures = new CompletableFuture[indexThreads + searchThreads];

        for (int i = 0; i < indexThreads; i++) {
            final AtomicInteger count = new AtomicInteger();
            futures[i] = CompletableFuture.runAsync(() -> {
                Thread.currentThread().setName("Indexer " + count.incrementAndGet());
                while (true) {
                    index(indexShardKey);
                }
            });
        }

        for (int i = indexThreads - 1; i < futures.length; i++) {
            final AtomicInteger count = new AtomicInteger();
            futures[i] = CompletableFuture.runAsync(() -> {
                Thread.currentThread().setName("Searcher " + count.incrementAndGet());
                while (true) {
                    search(indexShardKey, indexShard);
                    if (Thread.interrupted()) {
                        LOGGER.trace("Cleared interrupt");
                    }
                }
            });
        }

        CompletableFuture.allOf(futures).join();
    }

    private void index(final IndexShardKey indexShardKey) {
        try {
            // Create a writer in the pool
            final IndexShardWriter writer = indexShardWriterCache.getWriterByShardKey(indexShardKey);

            // Assert that there is 1 writer in the pool.
            assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(1);

//            final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();

            // Do some work.
            final FieldType fieldType = FieldTypeFactory.createBasic();
            final Field field = new Field("test", "test", fieldType);
            final Document document = new Document();
//            LOGGER.info("Adding document");
            document.add(field);
            writer.addDocument(document);

//            indexShardManager.performAction(criteria, IndexShardAction.FLUSH);

            // Close writer by removing the writer from the cache.
            indexShardWriterCache.close(writer);
//            indexShardWriterCache.shutdown();

//            // Make sure that writer was closed.
//            assertThat(compareStatus(IndexShardStatus.OPEN, writer.getIndexShardId())).isFalse();

            // Make sure that adding to writer reopens the index.
            indexer.addDocument(indexShardKey, document);
//            assertThat(compareStatus(IndexShardStatus.OPEN, writer.getIndexShardId())).isTrue();

            // Close indexes again.
            indexShardWriterCache.close(writer);
//            indexShardWriterCache.shutdown();

            // Make sure that writer was closed.
//            assertThat(compareStatus(IndexShardStatus.OPEN, writer.getIndexShardId())).isFalse();
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void search(final IndexShardKey indexShardKey, final IndexShard indexShard) {
        final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(indexShard.getId());

        // Get writer.
        IndexWriter writer = null;
        if (indexShardWriter != null) {
//            writer = indexShardWriter.getWriter();
        } else {
            LOGGER.trace("Null writer");
        }

        final IndexShardSearcher indexShardSearcher = new IndexShardSearcher(indexShard, writer, pathCreator);
        searchShard(
                new String[]{"test"},
                new LongAdder(),
                indexShardSearcher,
                values -> LOGGER.debug(values.toString()));
        indexShardSearcher.destroy();
    }

    private void searchShard(final String[] storedFieldNames,
                             final LongAdder hitCount,
                             final IndexShardSearcher indexShardSearcher,
                             final ValuesConsumer valuesConsumer) {
        try {
            final IndexField indexField = IndexField.createField("test");
            final List<IndexField> fields = new ArrayList<>();
            fields.add(indexField);
            final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(fields);

            // Get a query for this lucene version.
            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
                    null,
                    indexFieldsMap,
                    1024,
                    null,
                    System.currentTimeMillis());
            final TextField textField = new TextField("test");
            final ExpressionOperator expression = ExpressionOperator
                    .builder()
                    .addTerm(textField, Condition.EQUALS, "test")
                    .build();
            final SearchExpressionQuery query = searchExpressionQueryBuilder
                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expression);

            final DocIdQueue docIdQueue = new DocIdQueue(1000);
            final QueryKey queryKey = new QueryKey("test");

            final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
            final IndexSearcher searcher = searcherManager.acquire();
            final SimpleTaskContext taskContext = new SimpleTaskContext();
            try {
                final Runnable runnable = () -> {
                    try {
                        // Create a collector.
                        final IndexShardHitCollector collector = new IndexShardHitCollector(
                                taskContext,
                                queryKey,
                                new IndexShard(),
                                query.getQuery(),
                                docIdQueue,
                                hitCount);

                        searcher.search(query.getQuery(), collector);

                    } catch (final TaskTerminatedException e) {
                        // Expected error on early completion.
                        LOGGER.trace(e::getMessage, e);
                    } catch (final IOException e) {
                        LOGGER.error(e::getMessage, e);
                        throw new UncheckedIOException(e);
                    } finally {
                        docIdQueue.complete();
                        LOGGER.info("Found " + hitCount.longValue() + " hits");
                    }
                };
                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable);

                try {
                    // Start converting found docIds into stored data values
                    int count = 0;
                    boolean done = false;
                    while (!done) {
                        // Uncomment this to slow searches down in dev
//                            ThreadUtil.sleepAtLeastIgnoreInterrupts(1_000);
                        // Take the next item
                        final Integer docId = docIdQueue.take();
                        if (docId != null) {
                            // If we have a doc id then retrieve the stored data for it.
                            SearchProgressLog.increment(queryKey,
                                    SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_DOC_ID_STORE_TAKE);
                            getStoredData(queryKey, storedFieldNames, valuesConsumer, searcher, docId);

                            if (count == 1000) {
                                taskContext.terminate();
                            }

                            count++;
                        } else {
                            done = true;
                        }
                    }
                } finally {
                    // Ensure the searcher completes before we exit.
                    completableFuture.join();
                }
            } finally {
                searcherManager.release(searcher);
            }
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void getStoredData(final QueryKey queryKey,
                               final String[] storedFieldNames,
                               final ValuesConsumer valuesConsumer,
                               final IndexSearcher searcher,
                               final int docId) {
        try {
            SearchProgressLog.increment(queryKey, SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_GET_STORED_DATA);
            final Val[] values = new Val[storedFieldNames.length];
            final Document document = searcher.doc(docId);

            for (int i = 0; i < storedFieldNames.length; i++) {
                final String storedField = storedFieldNames[i];

                // If the field is null then it isn't stored.
                if (storedField != null) {
                    final IndexableField indexableField = document.getField(storedField);

                    // If the field is not in fact stored then it will be null here.
                    if (indexableField != null) {
                        final String value = indexableField.stringValue();
                        if (value != null) {
                            final String trimmed = value.trim();
                            if (trimmed.length() > 0) {
                                values[i] = ValString.create(trimmed);
                            }
                        }
                    }
                }
            }

            valuesConsumer.add(Values.of(values));
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }
    }
}
