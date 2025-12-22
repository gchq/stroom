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

package stroom.contentindex;

import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndex;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.shared.DocRefUtil;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.DocContentHighlights;
import stroom.explorer.shared.DocContentMatch;
import stroom.explorer.shared.FetchHighlightsRequest;
import stroom.explorer.shared.FindInContentRequest;
import stroom.explorer.shared.StringMatch;
import stroom.explorer.shared.StringMatch.MatchType;
import stroom.explorer.shared.StringMatchLocation;
import stroom.explorer.shared.TagsPatternParser;
import stroom.index.lucene.LuceneLockFactory;
import stroom.index.lucene.analyser.AnalyzerFactory;
import stroom.query.api.datasource.AnalyzerType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.io.FileUtil;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;
import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.LowerCaseFilter;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.Tokenizer;
import org.apache.lucene.analysis.core.KeywordAnalyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.analysis.ngram.NGramTokenizer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.Field.Store;
import org.apache.lucene.document.FieldType;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexOptions;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.StoredFields;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.ParseException;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.queryparser.simple.SimpleQueryParser;
import org.apache.lucene.search.BooleanClause.Occur;
import org.apache.lucene.search.BooleanQuery;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.automaton.RegExp;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Function;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * Creation of the content index (or re-indexing it) is triggered by a user doing a content
 * search. This ensures that only UI nodes create the index.
 * The index is also updated on a doc basis by this class handling {@link EntityEvent}s however
 * they will be ignored if the index is not initialised.
 * There is also a managed job that will trigger a full re-index
 */
@Singleton
@EntityEventHandler(
        action = {EntityAction.CREATE, EntityAction.UPDATE, EntityAction.DELETE})
public class LuceneContentIndex implements ContentIndex, EntityEvent.Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneContentIndex.class);
    private static final long JOB_ENABLED_STATE_CHECK_INTERVAL = 10_000;
    private static final int LATCH_TIMEOUT_MS = 1_500;
    private static final int MIN_GRAM = 1;
    private static final int MAX_GRAM = 2;
    private static final int MAX_HIGHLIGHTS = 100;

    static final String RE_INDEX_JOB_NAME = "Reindex Content";

    private static final String TYPE = "type";
    private static final String UUID = "uuid";
    private static final String NAME = "name";
    private static final String EXTENSION = "extension";
    //    private static final String DATA = "data";
//    private static final String DATA_CS = "data_cs";
    private static final String DATA_NGRAM = "data_ngram";
    private static final String DATA_CS_NGRAM = "data_cs_ngram";
    private static final String TEXT = "text";
    private static final String TAG = "tag";

    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerNodeService explorerNodeService;
    private final Set<ContentIndexable> indexables;
    private final Path docIndexDir;
    private final CountDownLatch indexInitialisedLatch = new CountDownLatch(1);
    private final AtomicBoolean isInitialising = new AtomicBoolean(false);
    private volatile Throwable initialisationError = null;
    private final AtomicInteger updateProgress = new AtomicInteger();
    private final AtomicInteger updateTotal = new AtomicInteger();

    private ArrayBlockingQueue<EntityEvent> changes = null;
    private Map<String, ContentIndexable> indexableMap = null;
    private Analyzer analyzer = null;
    private Directory directory = null;

    @Inject
    public LuceneContentIndex(final TempDirProvider tempDirProvider,
                              final Set<ContentIndexable> indexables,
                              final SecurityContext securityContext,
                              final TaskContextFactory taskContextFactory,
                              final ExplorerNodeService explorerNodeService) {
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.indexables = indexables;
        this.docIndexDir = tempDirProvider.get().resolve("doc-index");
        this.explorerNodeService = explorerNodeService;
    }

    private boolean isIndexInitialised() {
        return indexInitialisedLatch.getCount() == 0;
    }

    private boolean ensureIndexAsync() {
        if (!isIndexInitialised() && !isInitialising.get()) {
            synchronized (this) {
                if (!isIndexInitialised() && !isInitialising.get()) {
                    LOGGER.info("{} - Executing async initialisation of the content index", RE_INDEX_JOB_NAME);
                    CompletableFuture.runAsync(this::initIndex);
                }
            }
        }
        // If async this will almost certainly be true
        final boolean isInitialised = isIndexInitialised();
        if (!isInitialised && initialisationError != null) {
            throw new RuntimeException("Content index failed initialisation due to: "
                                       + initialisationError.getMessage());
        }
        return isInitialised;
    }

    private synchronized void initIndex() {
        if (!isInitialising.get()) {
            isInitialising.set(true);
            try {
                LOGGER.info("{} - Initialising content index in {}",
                        RE_INDEX_JOB_NAME,
                        docIndexDir.toAbsolutePath().normalize());
                this.indexableMap = indexables.stream()
                        .collect(Collectors.toMap(ContentIndexable::getType, Function.identity()));

                final Map<String, Analyzer> analyzerMap = Map.of(
                        TYPE, AnalyzerFactory.create(AnalyzerType.KEYWORD, false),
                        UUID, AnalyzerFactory.create(AnalyzerType.KEYWORD, false),
                        NAME, AnalyzerFactory.create(AnalyzerType.KEYWORD, false),
                        EXTENSION, AnalyzerFactory.create(AnalyzerType.KEYWORD, false),
//            DATA, AnalyzerFactory.create(AnalyzerType.KEYWORD, false),
//            DATA_CS, AnalyzerFactory.create(AnalyzerType.KEYWORD, true),
                        DATA_NGRAM, new NGramAnalyzer(),
                        DATA_CS_NGRAM, new NGramCSAnalyzer(),
                        TEXT, AnalyzerFactory.create(AnalyzerType.KEYWORD, true),
                        TAG, AnalyzerFactory.create(AnalyzerType.KEYWORD, false));

                analyzer = new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);

                Files.createDirectories(docIndexDir);

                final boolean validIndex = isValidIndex(docIndexDir, analyzer);
                if (!validIndex) {
                    FileUtil.deleteContents(docIndexDir);
                }

                changes = new ArrayBlockingQueue<>(10_000);

                // Create lucene directory object.
                directory = new NIOFSDirectory(docIndexDir, LuceneLockFactory.get());

            } catch (final IOException e) {
                initialisationError = e;
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }

            // Make sure all docs are up-to-date
            final Runnable runnable = securityContext.asProcessingUserResult(() ->
                    taskContextFactory.context(
                            RE_INDEX_JOB_NAME + " (Initialisation)",
                            TerminateHandlerFactory.NOOP_FACTORY,
                            taskContext -> {
                                doReindex();
                            }));
            runnable.run();

            // Let other threads know it is all good to use now
            isInitialising.set(false);
            indexInitialisedLatch.countDown();

            // Not the index is good to use start up the thread that will handle EntityEvents
            initQueueProcessorThread();
            LOGGER.info("{} - Initialisation of content index complete", RE_INDEX_JOB_NAME);
        } else {
            LOGGER.debug("Another thread is initialising the index");
        }
    }

    private void initQueueProcessorThread() {
        CompletableFuture
                .runAsync(() -> {
                    Thread.currentThread().setName("Content index queue monitor");
                    LOGGER.info("{} - Starting background thread to monitor change queue", RE_INDEX_JOB_NAME);
                    // This runnable is executed at the end of initIndex, so we don't
                    // have to worry about checking if the index is ready
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            final List<EntityEvent> list = new ArrayList<>();
                            // This one will block if empty
                            list.add(changes.take());
                            // Now get everything else on the queue without blocking
                            changes.drainTo(list);
                            if (!list.isEmpty()) {
                                securityContext.asProcessingUser(() -> taskContextFactory.context(
                                        "Indexing Content Changes",
                                        TerminateHandlerFactory.NOOP_FACTORY,
                                        taskContext -> {
                                            updateIndex(list);
                                        }).run());
                            }
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.error(e::getMessage, e);
                        Thread.currentThread().interrupt();
                        throw new UncheckedInterruptedException(e);
                    }
                });
    }

    /**
     * for testing
     */
    void flush() {
        if (isIndexInitialised()) {
            final List<EntityEvent> list = new ArrayList<>();
            changes.drainTo(list);
            if (!list.isEmpty()) {
                updateIndex(list);
            }
        } else {
            LOGGER.debug("Index is not initialised, doing nothing");
        }
    }

    private boolean isValidIndex(final Path dir, final Analyzer analyzer) {
        try (final NIOFSDirectory directory = new NIOFSDirectory(dir, LuceneLockFactory.get())) {
            try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
                final IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
                final QueryParser queryParser = new QueryParser("", analyzer);
                final Query query = queryParser.parse("test");
                indexSearcher.search(query, 1);
            }
        } catch (final Exception e) {
            return false;
        }
        return true;
    }

    @Override
    public void onChange(final EntityEvent event) {
        // This will be called on nodes that are not UI nodes and thus do no content indexing.
        // If the index is not initialised there is no point doing anything with the events.
        // If the index is subsequently initialised then the init will index all docs anyway.
        if (isIndexInitialised()) {
            NullSafe.consume(event, EntityEvent::getDocRef, DocRef::getType, type -> {
                if (indexableMap.containsKey(type)) {
                    try {
                        changes.put(event);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new UncheckedInterruptedException(e);
                    }
                }
            });
        }
    }

    /**
     * Re-index all indexable items on the system
     */
    public void reindex() {
        // Called by the stroom managed job scheduled execution
        // No point re-indexing if the index is not set up, e.g. admin as enabled Re-Index job
        // on a non-UI node.
        if (isIndexInitialised()) {
            doReindex();
        } else {
            LOGGER.info("{} - Index not yet initialised, nothing to re-index", RE_INDEX_JOB_NAME);
        }
    }

    private synchronized void doReindex() {
        taskContextFactory.current().info(() -> "Gathering indexable items");
        LOGGER.info("{} - Re-indexing all content", RE_INDEX_JOB_NAME);
        securityContext.asProcessingUser(() -> {
            final List<EntityEvent> events = indexableMap.values()
                    .stream()
                    .takeWhile(docRef ->
                            !Thread.currentThread().isInterrupted())
                    .flatMap(contentIndexable ->
                            contentIndexable.listDocuments().stream())
                    .map(docRef ->
                            new EntityEvent(docRef, EntityAction.UPDATE))
                    .toList();

            LOGGER.logDurationIfInfoEnabled(
                    () ->
                            updateIndex(events),
                    LogUtil.message("Re-index {} items", events.size()));
        });
    }

    @Override
    public ResultPage<DocContentMatch> findInContent(final FindInContentRequest request) {
        // Trigger async initialisation, wait for a bit, then throw if it is not ready
        ensureIndexAndWait();

        return LOGGER.logDurationIfDebugEnabled(() ->
                findInContentUsingIndex(request), "findInContentUsingIndex");
    }

    private ResultPage<DocContentMatch> findInContentUsingIndex(final FindInContentRequest request) {
        try {
            final StringMatch filter = request.getFilter();
            final Query query = getQuery(filter);
            final List<DocContentMatch> matches = new ArrayList<>();
            final PageRequest pageRequest = request.getPageRequest();
            final ContentHighlighter highlighter = getHighlighter(filter);

            long total = 0;
            try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
                final IndexSearcher searcher = new IndexSearcher(directoryReader);
                final ScoreDoc[] hits = searcher.search(query, 1_000_000).scoreDocs;
                LOGGER.debug(() -> LogUtil.message("{} ({}) query '{}' matched {} documents",
                        filter.getMatchType(),
                        (filter.isCaseSensitive()
                                ? "case-sense"
                                : "case-insense"),
                        filter.getPattern(),
                        hits.length));
                final StoredFields storedFields = searcher.storedFields();
                for (final ScoreDoc hit : hits) {
                    final int docId = hit.doc;
                    final Document doc = storedFields.document(docId);
                    final DocRef docRef = new DocRef(doc.get(TYPE), doc.get(UUID), doc.get(NAME));
                    final String text = doc.get(TEXT);
                    final String[] tagsArray = doc.getValues(TAG);
                    final List<StringMatchLocation> highlights = highlighter.getHighlights(text, 1);
                    if (!highlights.isEmpty()) {
                        if (securityContext.hasDocumentPermission(docRef, DocumentPermission.VIEW)) {
                            if (total >= pageRequest.getOffset() &&
                                total < pageRequest.getOffset() + pageRequest.getLength()) {
                                try {
                                    final String extension = doc.get(EXTENSION);
                                    matches.add(DocContentMatch.create(docRef,
                                            extension,
                                            text,
                                            highlights.getFirst(),
                                            List.of(tagsArray)));
                                } catch (final Exception e) {
                                    LOGGER.debug(e::getMessage, e);
                                }
                            }
                            total++;
                        }
                    }
                }
            }

            return new ResultPage<>(matches, PageResponse
                    .builder()
                    .offset(pageRequest.getOffset())
                    .length(matches.size())
                    .total(total)
                    .exact(total == pageRequest.getOffset() + matches.size())
                    .build());
        } catch (final ParseException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Parse error on '{}': {} (TRACE for stack trace)",
                    request.getFilter().getPattern(),
                    e.getMessage()));
            LOGGER.trace(e::getMessage, e);
            return ResultPage.empty();
        } catch (final RuntimeException | IOException e) {
            LOGGER.debug(e::getMessage, e);
            return ResultPage.empty();
        }
    }

    private ContentHighlighter getHighlighter(final StringMatch stringMatch) throws ParseException {
        return new BasicContentHighlighter(stringMatch);
//
//        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
//            return new BasicContentHighlighter(stringMatch);
//        }
//
//        final Query basicQuery = getBasicQuery(stringMatch);
//        return new LuceneContentHighlighter(getNGramField(stringMatch), basicQuery);
    }

    private String getNGramField(final StringMatch stringMatch) {
        return stringMatch.isCaseSensitive()
                ? DATA_CS_NGRAM
                : DATA_NGRAM;
    }

//    private String[] getFields(final StringMatch stringMatch) {
//        return stringMatch.isCaseSensitive()
//                ? new String[]{DATA_CS_NGRAM, DATA_CS}
//                : new String[]{DATA_NGRAM, DATA};
//    }

    private String getQueryText(final StringMatch stringMatch) {
        String value = stringMatch.isCaseSensitive()
                ? stringMatch.getPattern()
                : stringMatch.getPattern().toLowerCase(Locale.ROOT);
        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
            if (!value.startsWith("/")) {
                value = "/" + value;
            }
            if (!value.endsWith("/") || value.length() == 1) {
                value = value + "/";
            }
        }
        return value;
    }

    private Query getQuery(final StringMatch stringMatch) throws ParseException {
        final TagsPatternParser tagsPatternParser = new TagsPatternParser(stringMatch.getPattern());
        final List<String> tags = tagsPatternParser.getTags();
        final String text = tagsPatternParser.getText();

        // If we are going to do a regex then we'll just scan all content and do regex.
        if (MatchType.REGEX.equals(stringMatch.getMatchType())) {
            // Lucene matches on the whole line (^ & $ are not supported), so we need
            // to append .* to each end
            final String pattern = ".*" + text + ".*";
            final Term term = new Term(TEXT, pattern);
            try {
                if (stringMatch.isCaseSensitive()) {
                    return addTagsQuery(new RegexpQuery(term), tags);
                } else {
                    // The 10_000 came from looking at the other overloaded ctors in RegexpQuery
                    return addTagsQuery(new RegexpQuery(
                            term,
                            RegExp.ALL,
                            RegExp.ASCII_CASE_INSENSITIVE,
                            10_000), tags);
                }
            } catch (final Exception e) {
                LOGGER.debug(() -> LogUtil.message("Error constructing regex query with pattern '{}': {}",
                        pattern, LogUtil.exceptionMessage(e)));
                throw e;
            }
        } else {
            if (!text.isBlank()) {
                final String nGramField = getNGramField(stringMatch);
                final SimpleQueryParser simpleQueryParser = new SimpleQueryParser(analyzer, nGramField);
                final Query query = simpleQueryParser.createPhraseQuery(nGramField, text);
                return addTagsQuery(query, tags);
            }
        }

        return new BooleanQuery.Builder().build();
    }

    private Query addTagsQuery(final Query query, final List<String> tags) {
        final BooleanQuery.Builder builder = new BooleanQuery.Builder();
        builder.add(query, Occur.MUST);

        if (!tags.isEmpty()) {
            tags.forEach(tag -> {
                final Query tagQuery = new TermQuery(new Term(TAG, tag));
                builder.add(tagQuery, Occur.MUST);
            });
        }

        return builder.build();
    }

    private boolean isNGram(final String field) {
        return DATA_NGRAM.equals(field) || DATA_CS_NGRAM.equals(field);
    }

    @Override
    public DocContentHighlights fetchHighlights(final FetchHighlightsRequest request) {
        if (!securityContext.hasDocumentPermission(request.getDocRef(), DocumentPermission.VIEW)) {
            throw new PermissionException(securityContext.getUserRef(),
                    "You do not have read permission on " + request.getDocRef());
        }

        // Trigger async initialisation, wait for a bit, then throw if it is not ready
        ensureIndexAndWait();

        try (final DirectoryReader directoryReader = DirectoryReader.open(directory)) {
            final Query docQuery = createDocQuery(request.getDocRef(), request.getExtension());
            final IndexSearcher indexSearcher = new IndexSearcher(directoryReader);
            final ScoreDoc[] hits = indexSearcher.search(docQuery, 1).scoreDocs;
            if (hits.length > 0) {
                final int docId = hits[0].doc;
                final StoredFields storedFields = indexSearcher.storedFields();
                final Document doc = storedFields.document(docId);
                final String text = doc.get(TEXT);

                try {
                    final ContentHighlighter highlighter = getHighlighter(request.getFilter());
                    final List<StringMatchLocation> highlights = highlighter.getHighlights(text, MAX_HIGHLIGHTS);
                    return new DocContentHighlights(request.getDocRef(), text, highlights);

                } catch (final RuntimeException e) {
                    LOGGER.debug(e::getMessage, e);
                }
            }
        } catch (final ParseException e) {
            LOGGER.debug(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        } catch (final IOException e) {
            LOGGER.error(e::getMessage, e);
            throw new UncheckedIOException(e);
        }

        return null;
    }

    private synchronized void updateIndex(final List<EntityEvent> events) {
        if (NullSafe.hasItems(events)) {
            final Map<EntityAction, Long> countsByAction = events.stream()
                    .collect(Collectors.groupingBy(EntityEvent::getAction, Collectors.counting()));

            LOGGER.logDurationIfDebugEnabled(
                    () -> {
                        securityContext.asProcessingUser(() -> {
                            updateIndex(events, taskContextFactory.current());
                        });
                    },
                    () -> LogUtil.message("Process {} content index events, counts by event type: ({})",
                            events.size(),
                            countsByAction.entrySet()
                                    .stream()
                                    .map(entry -> entry.getKey() + "=" + entry.getValue())
                                    .collect(Collectors.joining(", "))));
        }
    }

    private synchronized void updateIndex(final List<EntityEvent> list, final TaskContext taskContext) {
        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        try (final IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
            updateProgress.set(0);
            updateTotal.set(list.size());
            list.stream()
                    .takeWhile(createTaskTerminatedCheck(taskContext))
                    .forEach(event -> {
                        updateDocInIndex(taskContext, event, writer);
                    });

            taskContext.info(() -> LogUtil.message("Committing {} changes to the content index", list.size()));
            LOGGER.logDurationIfDebugEnabled(
                    ThrowingRunnable.unchecked(writer::commit),
                    () -> LogUtil.message("Commit {} event(s)", updateProgress));

            taskContext.info(() -> LogUtil.message("Flushing {} changes to the content index", list.size()));
            LOGGER.logDurationIfDebugEnabled(
                    ThrowingRunnable.unchecked(writer::flush),
                    () -> LogUtil.message("Flush {} event(s)", updateProgress));
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void updateDocInIndex(final TaskContext taskContext,
                                  final EntityEvent event,
                                  final IndexWriter writer) {
        updateProgress.incrementAndGet();
        try {
            switch (event.getAction()) {
                case CREATE -> {
                    setTaskContextInfo(
                            taskContext, event, "Adding", updateProgress, updateTotal);
                    addDoc(taskContext, writer, event.getDocRef());
                }
                case UPDATE -> {
                    setTaskContextInfo(
                            taskContext, event, "Updating", updateProgress, updateTotal);
                    deleteDoc(writer, event.getDocRef());
                    addDoc(taskContext, writer, event.getDocRef());
                }
                case DELETE -> {
                    setTaskContextInfo(
                            taskContext, event, "Deleting", updateProgress, updateTotal);
                    deleteDoc(writer, event.getDocRef());
                }
            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private static Predicate<Object> createTaskTerminatedCheck(final TaskContext taskContext) {
        return obj -> {
            if (taskContext.isTerminated()) {
                LOGGER.info("{} - Task is terminated: '{}'", RE_INDEX_JOB_NAME, taskContext);
                return false;
            } else if (Thread.currentThread().isInterrupted()) {
                LOGGER.info("{} - Task thread is interrupted: '{}'", RE_INDEX_JOB_NAME, taskContext);
                return false;
            } else {
                return true;
            }
        };
    }

    private static void setTaskContextInfo(final TaskContext taskContext,
                                           final EntityEvent event,
                                           final String action,
                                           final AtomicInteger count,
                                           final AtomicInteger totalCount) {
        taskContext.info(
                () -> LogUtil.message("{}: {} ({} of {})",
                        action,
                        DocRefUtil.createSimpleDocRefString(event.getDocRef()),
                        count,
                        totalCount),
                LOGGER);
    }

    private void addDoc(final TaskContext taskContext, final IndexWriter writer, final DocRef docRef) {
        final ContentIndexable contentIndexable = indexableMap.get(docRef.getType());
        if (contentIndexable != null) {
            final Map<String, String> dataMap = contentIndexable.getIndexableData(docRef);
            if (NullSafe.hasEntries(dataMap)) {
                dataMap.entrySet()
                        .stream()
                        .takeWhile(createTaskTerminatedCheck(taskContext))
                        .forEach(entry -> {
                            final String extension = entry.getKey();
                            final String data = entry.getValue();
                            try {
                                index(writer, docRef, extension, data);
                            } catch (final Exception e) {
                                LOGGER.error(e::getMessage, e);
                            }
                        });
            }
        }
    }

    private void deleteDoc(final IndexWriter writer, final DocRef docRef) {
        try {
            final Query query = createDocQuery(docRef, null);
            writer.deleteDocuments(query);
        } catch (final ParseException | IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private Query createDocQuery(final DocRef docRef, final String extension) throws ParseException {
        final StringBuilder sb = new StringBuilder();
        sb.append("+");
        sb.append(TYPE);
        sb.append(":\"");
        sb.append(docRef.getType());
        sb.append("\" ");
        sb.append("+");
        sb.append(UUID);
        sb.append(":\"");
        sb.append(docRef.getUuid());
        sb.append("\"");

        if (extension != null) {
            sb.append(" ");
            sb.append("+");
            sb.append(EXTENSION);
            sb.append(":\"");
            sb.append(extension);
            sb.append("\"");
        }

        final QueryParser queryParser = new QueryParser("", analyzer);
        return queryParser.parse(sb.toString());
    }

    private void index(final IndexWriter writer,
                       final DocRef docRef,
                       final String extension,
                       final String data) throws IOException {
        final Document document = new Document();
        document.add(new TextField(TYPE, docRef.getType(), Store.YES));
        document.add(new TextField(UUID, docRef.getUuid(), Store.YES));
        document.add(new TextField(NAME, docRef.getName(), Store.YES));
        document.add(new TextField(EXTENSION, extension, Store.YES));
        document.add(createField(DATA_NGRAM, data));
        document.add(createField(DATA_CS_NGRAM, data));
//        document.add(createField(DATA, data));
//        document.add(createField(DATA_CS, data));
        document.add(new TextField(TEXT, data, Store.YES));
        explorerNodeService.getNode(docRef).ifPresent(node -> {
            if (node.getTags() != null) {
                node.getTags().forEach(tag ->
                    document.add(new TextField(TAG, tag, Store.YES))
                );
            }
        });
        writer.addDocument(document);
    }

    private Field createField(final String name, final String data) {
        final FieldType fieldType = new FieldType();
        // Indexes documents, frequencies, positions and offsets:
        fieldType.setIndexOptions(IndexOptions.DOCS_AND_FREQS_AND_POSITIONS_AND_OFFSETS);
        // Field's value is NOT stored in the index:
        fieldType.setStored(false);
        // Store index data in term vectors:
        fieldType.setStoreTermVectors(true);
        // Store token character offsets in term vectors:
        fieldType.setStoreTermVectorOffsets(true);
        // Store token positions in term vectors:
        fieldType.setStoreTermVectorPositions(true);
        // Do NOT store token payloads into the term vector:
        fieldType.setStoreTermVectorPayloads(true);
        return new Field(name, data, fieldType);
    }

    private void ensureIndexAndWait() {
        boolean isInitialised = ensureIndexAsync();
        if (!isInitialised) {
            try {
                // Make the user wait a wee bit if it is not initialised as this gives it a chance
                // to return a non-zero % so the user gets the impression that it is underway.
                isInitialised = indexInitialisedLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!isInitialised) {
            final int updateCount = this.updateProgress.get();
            final int updateTotal = this.updateTotal.get();
            final String completionText;
            if (updateCount != 0 && updateTotal != 0) {
                final int pctComplete = (int) (updateCount / (double) updateTotal * 100);
                completionText = " (" + pctComplete + "% complete)";
            } else {
                completionText = "";
            }
            throw new RuntimeException("The content is currently being indexed"
                                       + completionText + ".\n" +
                                       "Please try again in a minute by clicking the Refresh button.");
        }
    }

// --------------------------------------------------------------------------------


    static class NGramAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            final TokenStream tokenStream = new LowerCaseFilter(tokenizer);
            return new Analyzer.TokenStreamComponents(tokenizer, tokenStream);
        }
    }


// --------------------------------------------------------------------------------


    static class NGramCSAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            return new Analyzer.TokenStreamComponents(tokenizer, tokenizer);
        }
    }
}
