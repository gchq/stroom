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

package stroom.index;

import stroom.test.AbstractCoreIntegrationTest;

// FIXME : BROKEN BY LUCENE553 SEGREGATION
public class TestIndexShardSearcher extends AbstractCoreIntegrationTest {
//
//    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestIndexShardSearcher.class);
//
//    @Inject
//    private CommonTestScenarioCreator commonTestScenarioCreator;
//    @Inject
//    private IndexShardService indexShardService;
//    @Inject
//    private IndexShardWriterCache indexShardWriterCache;
//    @Inject
//    private Indexer indexer;
//    @Inject
//    private IndexStore indexStore;
//    @Inject
//    private PathCreator pathCreator;
//
//    @BeforeEach
//    void onBefore() {
//        indexShardWriterCache.shutdown();
//    }
//
//    @Disabled
//    @Test
//    void test() {
//        assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isZero();
//
//        // Create an index
//        final DocRef indexRef = commonTestScenarioCreator.createIndex("TEST_2010a");
//        final IndexDoc index = indexStore.readDocument(indexRef);
//        final IndexShardKey indexShardKey = IndexShardKey.createTestKey(index);
//
//        // Create and close writer.
//        final IndexShardWriter writer = indexShardWriterCache.getWriterByShardKey(indexShardKey);
//        indexShardWriterCache.close(writer);
//
//        // Get shard.
//        final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
//        criteria.getIndexUuidSet().add(indexShardKey.getIndexUuid());
//        criteria.getPartition().setString(indexShardKey.getPartition().getLabel());
//        final ResultPage<IndexShard> indexShardResultPage = indexShardService.find(criteria);
//        assertThat(indexShardResultPage.size()).isOne();
//        final IndexShard indexShard = indexShardResultPage.getFirst();
//
//        final int indexThreads = 10;
//        final int searchThreads = 10;
//        final CompletableFuture<?>[] futures = new CompletableFuture[indexThreads + searchThreads];
//
//        for (int i = 0; i < indexThreads; i++) {
//            final AtomicInteger count = new AtomicInteger();
//            futures[i] = CompletableFuture.runAsync(() -> {
//                Thread.currentThread().setName("Indexer " + count.incrementAndGet());
//                while (true) {
//                    index(indexShardKey);
//                }
//            });
//        }
//
//        for (int i = indexThreads - 1; i < futures.length; i++) {
//            final AtomicInteger count = new AtomicInteger();
//            futures[i] = CompletableFuture.runAsync(() -> {
//                Thread.currentThread().setName("Searcher " + count.incrementAndGet());
//                while (true) {
//                    search(indexShardKey, indexShard);
//                    if (Thread.interrupted()) {
//                        LOGGER.trace("Cleared interrupt");
//                    }
//                }
//            });
//        }
//
//        CompletableFuture.allOf(futures).join();
//    }
//
//    private void index(final IndexShardKey indexShardKey) {
//        try {
//            // Create a writer in the pool
//            final IndexShardWriter writer = indexShardWriterCache.getWriterByShardKey(indexShardKey);
//
//            // Assert that there is 1 writer in the pool.
//            assertThat(indexShardService.find(FindIndexShardCriteria.matchAll()).size()).isEqualTo(1);
//
////            final FindIndexShardCriteria criteria = FindIndexShardCriteria.matchAll();
//
//            // Do some work.
//            final IndexDocument document = new MockIndexDocument();
//            document.add(new FieldValue(IndexField.createField("test"), ValString.create("test")));
//
////            LOGGER.info("Adding document");
//            writer.addDocument(document);
//
////            indexShardManager.performAction(criteria, IndexShardAction.FLUSH);
//
//            // Close writer by removing the writer from the cache.
//            indexShardWriterCache.close(writer);
////            indexShardWriterCache.shutdown();
//
////            // Make sure that writer was closed.
////            assertThat(compareStatus(IndexShardStatus.OPEN, writer.getIndexShardId())).isFalse();
//
//            // Make sure that adding to writer reopens the index.
//            indexer.addDocument(indexShardKey, document);
////            assertThat(compareStatus(IndexShardStatus.OPEN, writer.getIndexShardId())).isTrue();
//
//            // Close indexes again.
//            indexShardWriterCache.close(writer);
////            indexShardWriterCache.shutdown();
//
//            // Make sure that writer was closed.
////            assertThat(compareStatus(IndexShardStatus.OPEN, writer.getIndexShardId())).isFalse();
//        } catch (final RuntimeException e) {
//            LOGGER.error(e::getMessage, e);
//        }
//    }
//
//    private void search(final IndexShardKey indexShardKey, final IndexShard indexShard) {
//        final IndexShardWriter indexShardWriter = indexShardWriterCache.getWriterByShardId(indexShard.getId());
//
//        // Get writer.
//        IndexWriter writer = null;
//        if (indexShardWriter != null) {
////            writer = indexShardWriter.getWriter();
//        } else {
//            LOGGER.trace("Null writer");
//        }
//
//        final IndexShardSearcher indexShardSearcher = new IndexShardSearcher(indexShard, writer, pathCreator);
//        searchShard(
//                new String[]{"test"},
//                new LongAdder(),
//                indexShardSearcher,
//                values -> LOGGER.debug(values.toString()));
//        indexShardSearcher.destroy();
//    }
//
//    private void searchShard(final String[] storedFieldNames,
//                             final LongAdder hitCount,
//                             final IndexShardSearcher indexShardSearcher,
//                             final ValuesConsumer valuesConsumer) {
//        try {
//            final IndexField indexField = IndexField.createField("test");
//            final List<IndexField> fields = new ArrayList<>();
//            fields.add(indexField);
//            final IndexFieldsMap indexFieldsMap = new IndexFieldsMap(fields);
//
//            // Get a query for this lucene version.
//            final SearchExpressionQueryBuilder searchExpressionQueryBuilder = new SearchExpressionQueryBuilder(
//                    null,
//                    indexFieldsMap,
//                    1024,
//                    DateTimeSettings.builder().build());
//            final QueryField textField = QueryField.createText("test");
//            final ExpressionOperator expression = ExpressionOperator
//                    .builder()
//                    .addTerm(textField, Condition.EQUALS, "test")
//                    .build();
//            final SearchExpressionQuery query = searchExpressionQueryBuilder
//                    .buildQuery(LuceneVersionUtil.CURRENT_LUCENE_VERSION, expression);
//
//            final DocIdQueue docIdQueue = new DocIdQueue(1000);
//            final QueryKey queryKey = new QueryKey("test");
//
//            final SearcherManager searcherManager = indexShardSearcher.getSearcherManager();
//            final IndexSearcher searcher = searcherManager.acquire();
//            final SimpleTaskContext taskContext = new SimpleTaskContext();
//            try {
//                final Runnable runnable = () -> {
//                    try {
//                        // Create a collector.
//                        final IndexShardHitCollector collector = new IndexShardHitCollector(
//                                taskContext,
//                                queryKey,
//                                new IndexShard(),
//                                query.getQuery(),
//                                docIdQueue,
//                                hitCount);
//
//                        searcher.search(query.getQuery(), collector);
//
//                    } catch (final TaskTerminatedException e) {
//                        // Expected error on early completion.
//                        LOGGER.trace(e::getMessage, e);
//                    } catch (final IOException e) {
//                        LOGGER.error(e::getMessage, e);
//                        throw new UncheckedIOException(e);
//                    } finally {
//                        docIdQueue.complete();
//                        LOGGER.info("Found " + hitCount.longValue() + " hits");
//                    }
//                };
//                final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(runnable);
//
//                try {
//                    // Start converting found docIds into stored data values
//                    int count = 0;
//                    boolean done = false;
//                    while (!done) {
//                        // Uncomment this to slow searches down in dev
////                            ThreadUtil.sleepAtLeastIgnoreInterrupts(1_000);
//                        // Take the next item
//                        final Integer docId = docIdQueue.take();
//                        if (docId != null) {
//                            // If we have a doc id then retrieve the stored data for it.
//                            SearchProgressLog.increment(queryKey,
//                                    SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_DOC_ID_STORE_TAKE);
//                            getStoredData(queryKey, storedFieldNames, valuesConsumer, searcher, docId);
//
//                            if (count == 1000) {
//                                taskContext.terminate();
//                            }
//
//                            count++;
//                        } else {
//                            done = true;
//                        }
//                    }
//                } finally {
//                    // Ensure the searcher completes before we exit.
//                    completableFuture.join();
//                }
//            } finally {
//                searcherManager.release(searcher);
//            }
//        } catch (final IOException | RuntimeException e) {
//            LOGGER.error(e::getMessage, e);
//        }
//    }
//
//    private void getStoredData(final QueryKey queryKey,
//                               final String[] storedFieldNames,
//                               final ValuesConsumer valuesConsumer,
//                               final IndexSearcher searcher,
//                               final int docId) {
//        try {
//            SearchProgressLog.increment(queryKey, SearchPhase.INDEX_SHARD_SEARCH_TASK_HANDLER_GET_STORED_DATA);
//            final Val[] values = new Val[storedFieldNames.length];
//            final Document document = searcher.doc(docId);
//
//            for (int i = 0; i < storedFieldNames.length; i++) {
//                final String storedField = storedFieldNames[i];
//
//                // If the field is null then it isn't stored.
//                if (storedField != null) {
//                    final IndexableField indexableField = document.getField(storedField);
//
//                    // If the field is not in fact stored then it will be null here.
//                    if (indexableField != null) {
//                        final String value = indexableField.stringValue();
//                        if (value != null) {
//                            final String trimmed = value.trim();
//                            if (trimmed.length() > 0) {
//                                values[i] = ValString.create(trimmed);
//                            }
//                        }
//                    }
//                }
//            }
//
//            valuesConsumer.accept(Val.of(values));
//        } catch (final IOException e) {
//            LOGGER.error(e::getMessage, e);
//            throw new UncheckedIOException(e);
//        }
//    }
}
