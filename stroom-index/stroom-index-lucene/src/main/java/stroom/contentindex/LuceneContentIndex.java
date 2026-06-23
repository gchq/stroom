/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.cluster.api.ClusterNodeManager;
import stroom.cluster.lock.api.ClusterLockService;
import stroom.contentindex.ContentIndexConfig.StorageType;
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
import stroom.node.api.NodeInfo;
import stroom.query.api.datasource.AnalyzerType;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.task.api.ExecutorProvider;
import stroom.task.api.TaskContext;
import stroom.task.api.TaskContextFactory;
import stroom.task.api.TerminateHandlerFactory;
import stroom.task.api.ThreadPoolImpl;
import stroom.task.shared.ThreadPool;
import stroom.util.collections.CollectionUtil;
import stroom.util.collections.CollectionUtil.DuplicateMode;
import stroom.util.concurrent.ThreadUtil;
import stroom.util.concurrent.UncheckedInterruptedException;
import stroom.util.date.DateUtil;
import stroom.util.entityevent.EntityAction;
import stroom.util.entityevent.EntityEvent;
import stroom.util.entityevent.EntityEvent.Handler;
import stroom.util.entityevent.EntityEventHandler;
import stroom.util.exception.ThrowingRunnable;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.io.TempDirProvider;
import stroom.util.logging.DurationTimer;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.PageRequest;
import stroom.util.shared.PageResponse;
import stroom.util.shared.PermissionException;
import stroom.util.shared.ResultPage;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
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
import org.apache.lucene.search.BooleanQuery.Builder;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.RegexpQuery;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.SearcherFactory;
import org.apache.lucene.search.SearcherManager;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.util.automaton.RegExp;
import org.jspecify.annotations.NonNull;

import java.io.IOException;
import java.io.RandomAccessFile;
import java.io.UncheckedIOException;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardOpenOption;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.Executor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.BooleanSupplier;
import java.util.function.Predicate;
import java.util.stream.Collectors;

/**
 * An index of the content of the document stores, e.g. dicts, XSLTs, etc.
 * <p>
 * The Content Index can be stored on local or shared storage. The former is more performant
 * for reads, but means every node has to keep it up to date.
 * </p>
 * <p>
 * The Content Index is kept up to date by handling onChange entity events and a scheduled full-rebuild
 * job.
 * </p>
 * <p>
 * If running on local/temp storage, the index is not eagerly built. It is build on demand
 * when a user executes a search. This is to ensure it is only built on the UI nodes that need it.
 * </p>
 * <p>
 * If running on shared storage, the index is eagerly checked/built by any one node on boot.
 * All onChange events are handled by the current master node, as only one node needs to handle them.
 * Scheduled re-builds will run on every node the job is configured on, but the re-build will only
 * happen if the time since last rebuild is greater than a threshold (to stop all nodes doing the rebuild).
 * </p>
 */
@Singleton
@EntityEventHandler(action = {
        EntityAction.CREATE,
        EntityAction.UPDATE,
        EntityAction.DELETE})
public class LuceneContentIndex implements ContentIndex, Handler {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneContentIndex.class);
    private static final ThreadPool THREAD_POOL = new ThreadPoolImpl("Lucene Content Index");

    public static final String LOCK_NAME = "LuceneContentIndexFiles";
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
    private static final int EVENT_QUEUE_SIZE = 10_000;
    public static final int QUEUE_DRAIN_DELAY = 300;

    private final SecurityContext securityContext;
    private final TaskContextFactory taskContextFactory;
    private final ExplorerNodeService explorerNodeService;
    private final Executor executor;
    private final Set<ContentIndexable> indexables;
    private final IndexDir indexDir;
    private final CountDownLatch indexInitialisedLatch = new CountDownLatch(1);
    private final AtomicBoolean isInitialising = new AtomicBoolean(false);
    private final AtomicInteger updateProgress = new AtomicInteger();
    private final AtomicInteger updateTotal = new AtomicInteger();
    private final ClusterLockService clusterLockService;
    private final NodeInfo nodeInfo;
    private final ClusterNodeManager clusterNodeManager;
    private final Provider<ContentIndexConfig> contentIndexConfigProvider;

    private volatile Throwable initialisationError = null;
    /**
     * Document type to {@link ContentIndexable} map
     */
    private volatile Map<String, ContentIndexable> typeToIndexableMap = null;
    private volatile ArrayBlockingQueue<EntityEvent> eventQueue = null;
    private volatile Analyzer analyzer = null;
    private volatile Directory directory = null;
    private volatile SearcherManager searcherManager = null;

    @Inject
    public LuceneContentIndex(final TempDirProvider tempDirProvider,
                              final PathCreator pathCreator,
                              final Provider<ContentIndexConfig> contentIndexConfigProvider,
                              final Set<ContentIndexable> indexables,
                              final SecurityContext securityContext,
                              final TaskContextFactory taskContextFactory,
                              final ExplorerNodeService explorerNodeService,
                              final ExecutorProvider executorProvider,
                              final ClusterLockService clusterLockService,
                              final NodeInfo nodeInfo,
                              final ClusterNodeManager clusterNodeManager) {
        this.securityContext = securityContext;
        this.taskContextFactory = taskContextFactory;
        this.indexables = indexables;
        this.explorerNodeService = explorerNodeService;
        this.executor = executorProvider.get(THREAD_POOL);
        this.clusterLockService = clusterLockService;
        this.nodeInfo = nodeInfo;
        this.clusterNodeManager = clusterNodeManager;
        this.contentIndexConfigProvider = contentIndexConfigProvider;
        this.indexDir = getIndexDirectory(contentIndexConfigProvider, tempDirProvider, pathCreator);

        // With a shared index we can get any single node (doesn't matter which) to eagerly init it
        if (indexDir.isShared()) {
            // Do it async so we don't hold up guice binding
            CompletableFuture.runAsync(() -> {
                try {
                    // When a cluster is booted, initIndex will probably finish on one node before others boot
                    // up, so it may be called by >1 node, but initIndex will check the index age and drop
                    // out if not old, so is not an issue.
                    LOGGER.debug("Content index using shared storage in {}, (re-)building index if we acquire lock.",
                            indexDir.path);
                    runIfNotLocked(this::initIndex);
                } catch (final Exception e) {
                    LOGGER.error("Error initialising the shared index {} - {}",
                            indexDir, LogUtil.exceptionMessage(e), e);
                }
            }, executor);
        } else {
            if (isIndexPresentOnDisk()) {
                // Local storage and an index exists on disk so do an async rebuild to ensure the
                // index is up to date before the user hits it
                CompletableFuture.runAsync(() -> {
                    try {
                        LOGGER.debug("Found local/temp content index in {}, (re-)building index.", indexDir.path);
                        runUnderLock(this::initIndex);
                    } catch (final Exception e) {
                        LOGGER.error("Error initialising the local/temp index {} - {}",
                                indexDir, LogUtil.exceptionMessage(e), e);
                    }
                }, executor);
            }
        }
    }

    private boolean isIndexPresentOnDisk() {
        final Path dir = indexDir.path;
        try {
            return Files.exists(dir) && !FileUtil.isEmptyDirectory(dir);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private IndexDir getIndexDirectory(final Provider<ContentIndexConfig> contentIndexConfigProvider,
                                       final TempDirProvider tempDirProvider,
                                       final PathCreator pathCreator) {
        final ContentIndexConfig contentIndexConfig = contentIndexConfigProvider.get();
        final StorageType storageType = contentIndexConfig.getStorageType();
        final IndexDir indexDir;
        if (storageType == StorageType.TEMP) {
            final Path path = Paths.get(contentIndexConfig.getContentIndexDir());
            if (!path.isAbsolute()) {
                indexDir = new IndexDir(
                        tempDirProvider.get().resolve(path).toAbsolutePath().normalize(),
                        storageType);
            } else {
                throw new IllegalStateException("Property contentIndexDir cannot be absolute if storageType is TEMP");
            }
        } else {
            final Path path = pathCreator.toAppPath(contentIndexConfig.getContentIndexDir());
            indexDir = new IndexDir(path, storageType);
        }
        LOGGER.debug("getIndexDirectory() - Returning {}", indexDir);
        return indexDir;
    }

    private boolean isIndexInitialised() {
        return indexInitialisedLatch.getCount() == 0;
    }

    private boolean ensureIndexAsync() {
        final BooleanSupplier requiresInitialisationTest = () ->
                !isIndexInitialised() && !isInitialising.get();

        if (requiresInitialisationTest.getAsBoolean()) {
            LOGGER.debug("ensureIndexAsync() - Initialisation required, about to get local lock.");
            final DurationTimer timer = DurationTimer.start();

            synchronized (this) {
                // Re-test under local lock
                if (requiresInitialisationTest.getAsBoolean()) {
                    LOGGER.debug("ensureIndexAsync() - Acquired local lock in {}.", timer);

                    // We init the index async so the user does not have to wait for the complete
                    // index build.

                    if (indexDir.isShared()) {
                        // Use both synchronised and clusterLockService for shared storage to save every thread
                        // from spawning an async thread and piling into the db to get a lock
                        LOGGER.debug("ensureIndexAsync() - Acquired cluster lock in {}.", timer);

                        LOGGER.info("{} - Executing async initialisation of the content index under cluster lock",
                                RE_INDEX_JOB_NAME);
                        CompletableFuture.runAsync(() -> {
                            runUnderLock(() -> {
                                // Re-test under cluster lock
                                if (requiresInitialisationTest.getAsBoolean()) {
                                    initIndex();
                                }
                            });
                        }, executor);
                    } else {
                        LOGGER.info("{} - Executing async initialisation of the content index under local lock",
                                RE_INDEX_JOB_NAME);
                        CompletableFuture.runAsync(this::initIndex, executor);
                    }
                } else {
                    LOGGER.debug("ensureIndexAsync() - Another thread has initialised");
                }
            }
        }

        // Unless this is a boot of a brand new stroom, this will be true
        final boolean isInitialised = isIndexInitialised();
        if (!isInitialised && initialisationError != null) {
            throw new RuntimeException("Content index failed initialisation due to: "
                                       + initialisationError.getMessage());
        }
        return isInitialised;
    }

    private Map<String, ContentIndexable> buildTypeToIndexableMap() {
        return CollectionUtil.mapBy(
                ContentIndexable::getType,
                DuplicateMode.THROW,
                indexables);
    }

    private synchronized void initIndex() {
        if (!isInitialising.get()) {
            isInitialising.set(true);
            boolean isSyncRebuildRequired = false;
            final Map<String, ContentIndexable> typeToIndexableMap = Objects.requireNonNullElseGet(
                    this.typeToIndexableMap,
                    this::buildTypeToIndexableMap);
            final Analyzer analyzer = Objects.requireNonNullElseGet(this.analyzer, this::buildAnalyzer);
            try {
                final Path indexDirPath = indexDir.path;
                LOGGER.info("{} - Initialising content index in {}", RE_INDEX_JOB_NAME, indexDirPath);

                if (Files.exists(indexDirPath)) {
                    LOGGER.debug("initIndex() - {} exists", indexDirPath);
                    if (!Files.isDirectory(indexDirPath)) {
                        throw new IOException(LogUtil.message("{} exists but is not a directory", indexDirPath));
                    }
                    if (FileUtil.isEmptyDirectory(indexDirPath)) {
                        LOGGER.info("Content index directory {} is empty, all content will be indexed. " +
                                    "This may take some time.", indexDirPath);
                        isSyncRebuildRequired = true;
                    } else {
                        // Make sure what is on disk can be searched
                        final boolean validIndex = isValidLuceneIndex(indexDirPath, analyzer);
                        if (!validIndex) {
                            LOGGER.info("Found invalid content index in {}, deleting the contents of this directory " +
                                        "and indexing the from scratch. This may take some time.", indexDirPath);
                            FileUtil.deleteContents(indexDirPath);
                            isSyncRebuildRequired = true;
                        }
                    }
                } else {
                    try {
                        Files.createDirectories(indexDirPath);
                        LOGGER.info("Created directory {}. All content will be indexed. This may take some time",
                                indexDirPath);
                    } catch (final IOException e) {
                        throw new IOException("Unable to create index directory: " + indexDirPath, e);
                    }
                    isSyncRebuildRequired = true;
                }

                LOGGER.debug("initIndex() - indexDirPath: {}, isSyncRebuildRequired: {}",
                        indexDirPath, isSyncRebuildRequired);

                // Create the queue to receive events now we know we are a UI node
//                changes = new ArrayBlockingQueue<>(10_000);

                // Create lucene directory object.
                directory = new NIOFSDirectory(indexDirPath, LuceneLockFactory.get());
            } catch (final IOException e) {
                initialisationError = e;
                LOGGER.error(e::getMessage, e);
                throw new UncheckedIOException(e);
            }

            // This is the runnable to (re-)build the index
            final Runnable runnable = securityContext.asProcessingUserResult(() ->
                    taskContextFactory.context(
                            RE_INDEX_JOB_NAME + " (Initialisation)",
                            TerminateHandlerFactory.NOOP_FACTORY,
                            ignored -> {
                                doReindex(typeToIndexableMap);
                            }));

            // Make sure we have a state file so other code can check it
            ensureStateFile();

            // We have to assume that the index is up-to-date enough, because if we always do
            // a synchronous re-build, the first user to hit the index after a node reboot will always have
            // to wait a while. Doing it async means we can bounce a node, bring it up, then use the existing
            // index straight away while a re-index happens in the background to ensure the node is up to date.
            if (isSyncRebuildRequired) {
                LOGGER.info("Executing synchronous (re-)build of the content index");
                // We are already under lock, so no need to try to lock again (which it won't allow)
                runnable.run();
            } else {
                LOGGER.info("Executing asynchronous (re-)build of the content index");
                // This thread will have to wait for us to drop out of any locks
                CompletableFuture.runAsync(() -> {
                    // Another node my have beaten us so let it rebuild
                    runUnderLock(runnable);
                }, executor);
            }

            // Let other threads know it is all good to use now
            isInitialising.set(false);
            indexInitialisedLatch.countDown();

            if (isSyncRebuildRequired) {
                LOGGER.info("{} - Initialisation of content index complete", RE_INDEX_JOB_NAME);
            } else {
                LOGGER.info("{} - Initialisation of content index complete, " +
                            "(re-)build of the index will continue in the background", RE_INDEX_JOB_NAME);
            }
        } else {
            LOGGER.debug("Another thread is initialising the index");
        }
    }

    private void ensureAnalyzerAndIndexableMap() {
        // init does not set these fields because a node doing an init may not be a UI node.
        if (analyzer == null) {
            synchronized (this) {
                if (analyzer == null) {
                    analyzer = buildAnalyzer();
                }
            }
        }
        if (typeToIndexableMap == null) {
            synchronized (this) {
                if (typeToIndexableMap == null) {
                    typeToIndexableMap = buildTypeToIndexableMap();
                }
            }
        }
    }

    private Analyzer buildAnalyzer() {
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

        return new PerFieldAnalyzerWrapper(new KeywordAnalyzer(), analyzerMap);
    }

    private synchronized void initQueueProcessorThread() {
        eventQueue = new ArrayBlockingQueue<>(EVENT_QUEUE_SIZE);
        CompletableFuture
                .runAsync(() -> {
                    LOGGER.info("{} - Starting background thread to monitor change queue", RE_INDEX_JOB_NAME);
                    // This runnable is executed at the end of initIndex, so we don't
                    // have to worry about checking if the index is ready
                    try {
                        while (!Thread.currentThread().isInterrupted()) {
                            final List<EntityEvent> list = new ArrayList<>();
                            // This one will block if empty
                            list.add(eventQueue.take());
                            // Add a little pause to allow other events to come in.
                            // This saves us having to get a lock on each one
                            ThreadUtil.sleepIgnoringInterrupts(QUEUE_DRAIN_DELAY);
                            // Now get everything else on the queue without blocking
                            eventQueue.drainTo(list);
                            if (!list.isEmpty()) {
                                securityContext.asProcessingUser(() -> taskContextFactory.context(
                                        "Indexing Content Changes",
                                        TerminateHandlerFactory.NOOP_FACTORY,
                                        ignored -> {
                                            // This is an async thread so make it acquire the lock
                                            runUnderLock(() -> {
                                                LOGGER.debug(() -> LogUtil.message(
                                                        "initQueueProcessorThread() - event count: {}, " +
                                                        "events (first ten):\n{}",
                                                        list.size(),
                                                        list.stream()
                                                                .limit(10)
                                                                .filter(Objects::nonNull)
                                                                .map(Objects::toString)
                                                                .map(str -> "  " + str)
                                                                .collect(Collectors.joining("\n"))));
                                                updateIndex(list);
                                            });
                                        }).run());
                            }
                        }
                    } catch (final InterruptedException e) {
                        LOGGER.error(e::getMessage, e);
                        Thread.currentThread().interrupt();
                        throw new UncheckedInterruptedException(e);
                    }
                }, executor);
    }

    /**
     * for testing
     */
    void flush() {
        if (isIndexInitialised()) {
            final List<EntityEvent> list = new ArrayList<>();
            eventQueue.drainTo(list);
            if (!list.isEmpty()) {
                runUnderLock(() -> {
                    updateIndex(list);
                });
            }
        } else {
            LOGGER.debug("Index is not initialised, doing nothing");
        }
    }

    private boolean isValidLuceneIndex(final Path dir, final Analyzer analyzer) {
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
        if (!isIndexInitialised() && indexDir.isShared() && isThisMasterNode()) {
            LOGGER.info("Initializing content index for onChange events");
            initIndex();
        }

        // This will be called on nodes that are not UI nodes and thus do no content indexing.
        // If the index is not initialised there is no point doing anything with the events.
        // If the index is subsequently initialised then the init will index all docs anyway.
        if (shouldConsumeEvent(event)) {
            LOGGER.debug("onChange() - event: {}", event);
            // If we get in here we are a node that a user has indirectly initialised the
            // index on, so we need to update it.

            // TODO On shared storage, the event will be handled by all UI nodes with an initialised
            //  index, so each one will update the doc in the index (delete + add), for probably the same
            //  document version. We don't have an easy way to tell if the ver of the doc is already in
            //  the index or not. Would need 66's generic doc store to make it easier to get the doc version
            //  for storage in the index. Then we could query the index to see if the stored ver is the same
            //  as the one we got from ContentIndexable.
            //  For now it just means duplicated effort, which we would have if the index was on local storage
            //  anyway.
            ensureAnalyzerAndIndexableMap();

            NullSafe.consume(event, EntityEvent::getDocRef, DocRef::getType, type -> {
                if (typeToIndexableMap.containsKey(type)) {
                    try {
                        LOGGER.info("onChange - Putting event {}", event);
                        getEventQueue().put(event);
                    } catch (final InterruptedException e) {
                        Thread.currentThread().interrupt();
                        throw new UncheckedInterruptedException(e);
                    }
                }
            });
        } else {
            LOGGER.debug("Ignoring event {}", event);
        }
    }

    /**
     * Re-index all indexable items on the system
     */
    public void reindex() {
        // Called by the stroom managed job scheduled execution
        // No point re-indexing if the index is not set up, e.g. admin as enabled Re-Index job
        // on a non-UI node.

        // Make sure the index is initialised on this node.
        // If this is not shared then the user activity should trigger init, to ensure
        // we only have an index on a UI node
        if (!isIndexInitialised() && indexDir.isShared()) {
            // This will trigger a reindex (either sync or async)
            runUnderLock(this::initIndex);
        } else if (isIndexInitialised()) {
            // Unfortunately, each node has to get the exclusive lock, but only long enough to read the
            // index state file, so if lots of nodes call reindex in quick succession, only one
            // will re-index it.
            runUnderLock(() ->
                    doReindex(Objects.requireNonNullElseGet(typeToIndexableMap, this::buildTypeToIndexableMap)));
        } else {
            LOGGER.debug("{} - Index not yet initialised, nothing to re-index", RE_INDEX_JOB_NAME);
        }
    }

    /**
     * This should be called under cluster lock (if shared)
     */
    private synchronized void doReindex(final Map<String, ContentIndexable> typeToindexableMap) {
        try {
            final Path stateFile = ensureStateFile();
            final Duration minRebuildAge = contentIndexConfigProvider.get().getMinRebuildAge().getDuration();

            try (final RandomAccessFile reader = new RandomAccessFile(stateFile.toFile(), "rwd");
                    final FileChannel channel = reader.getChannel()) {

                final ByteBuffer byteBuffer = ByteBuffer.allocate(IndexState.TOTAL_BYTES);
                final IndexState indexState = readIndexState(channel, byteBuffer);
                // This is to stop multiple threads all trying to re-index one after the other as they get the lock
                if ((indexState.lastRebuildEpochMs + minRebuildAge.toMillis()) < System.currentTimeMillis()) {

                    taskContextFactory.current().info(() -> "Gathering indexable items");
                    LOGGER.info("{} - Re-indexing all content", RE_INDEX_JOB_NAME);
                    securityContext.asProcessingUser(() -> {
                        final List<EntityEvent> events = typeToindexableMap.values()
                                .stream()
                                .flatMap(contentIndexable ->
                                        contentIndexable.listDocuments().stream())
                                .takeWhile(ignored ->
                                        !Thread.currentThread().isInterrupted())
                                .map(docRef ->
                                        new EntityEvent(docRef, EntityAction.UPDATE))
                                .toList();

                        if (!Thread.currentThread().isInterrupted()) {
                            LOGGER.logDurationIfDebugEnabled(
                                    () ->
                                            updateIndex(events),
                                    LogUtil.message("Re-index {} items", events.size()));
                        } else {
                            LOGGER.error("Content index re-build interrupted");
                        }
                    });

                    // Update the index state so other nodes know how new it is
                    writeIndexState(channel, byteBuffer);
                } else {
                    taskContextFactory.current().info(() -> "Skipping re-indexing");
                    LOGGER.info("{} - Skipping Re-indexing, last re-index: {} ({}) is younger than {}",
                            RE_INDEX_JOB_NAME,
                            DateUtil.createNormalDateTimeString(indexState.lastRebuildEpochMs),
                            Duration.between(indexState.getLastRebuildTime(), Instant.now()),
                            minRebuildAge);
                }
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    private static IndexState readIndexState(final FileChannel channel, final ByteBuffer byteBuffer)
            throws IOException {
        try {
            channel.read(byteBuffer);
            byteBuffer.flip();
            return IndexState.deserialise(byteBuffer);
        } catch (final IOException e) {
            LOGGER.error("Error reading indexState file - {}", LogUtil.exceptionMessage(e), e);
            return IndexState.ZERO;
        }
    }

    private @NonNull Path ensureStateFile() {
        final Path stateFile = indexDir.getStateFile();
        try {
            if (!Files.exists(stateFile)) {
                Files.write(stateFile,
                        IndexState.ZERO.serialise(),
                        StandardOpenOption.CREATE_NEW,
                        StandardOpenOption.WRITE);
            }
            return stateFile;
        } catch (final IOException e) {
            final String msg = LogUtil.message("Error ensuring index state file {} -- {}",
                    stateFile, LogUtil.exceptionMessage(e));
            LOGGER.error(msg, e);
            throw new UncheckedIOException(msg, e);
        }
    }

    private static void writeIndexState(final FileChannel channel,
                                        final ByteBuffer byteBuffer) throws IOException {
        // Get ready for writing back to the file
        channel.position(0);
        byteBuffer.clear();
        final IndexState newIndexState = new IndexState(System.currentTimeMillis());
        newIndexState.serialise(byteBuffer);
        byteBuffer.flip();
        final int writeCount = channel.write(byteBuffer);
        if (writeCount != IndexState.TOTAL_BYTES) {
            throw new IllegalStateException(LogUtil.message("Unexpected writeCount {}, expecting {}",
                    writeCount, IndexState.TOTAL_BYTES));
        }
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
            final SearcherManager searcherManager = getSearcherManager();
            // Another node may have updated the index so see if we need to refresh the searcher it holds
            searcherManager.maybeRefresh();

            final IndexSearcher searcher = searcherManager.acquire();
            try {
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
            } finally {
                if (searcher != null) {
                    searcherManager.release(searcher);
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
            LOGGER.error("Error performing query {} - {}", request, LogUtil.exceptionMessage(e), e);
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

        return new Builder().build();
    }

    private Query addTagsQuery(final Query query, final List<String> tags) {
        final Builder builder = new Builder();
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

        try {
            final SearcherManager searcherManager = getSearcherManager();
            searcherManager.maybeRefresh();
            final IndexSearcher indexSearcher = searcherManager.acquire();
            try {
                final Query docQuery = createDocQuery(request.getDocRef(), request.getExtension(), analyzer);
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
            } finally {
                if (indexSearcher != null) {
                    searcherManager.release(indexSearcher);
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

    /**
     * Caller should ensure this is called under cluster lock (if shared storage)
     */
    private synchronized void updateIndex(final List<EntityEvent> events) {
        if (NullSafe.hasItems(events)) {
            final Map<EntityAction, Long> countsByAction = events.stream()
                    .collect(Collectors.groupingBy(EntityEvent::getAction, Collectors.counting()));

            LOGGER.logDurationIfInfoEnabled(
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

    /**
     * Caller should ensure this is called under cluster lock (if shared storage)
     */
    private synchronized void updateIndex(final List<EntityEvent> list,
                                          final TaskContext taskContext) {
        final Analyzer analyzer = Objects.requireNonNullElseGet(this.analyzer, this::buildAnalyzer);
        final Map<String, ContentIndexable> typeToIndexableMap = Objects.requireNonNullElseGet(this.typeToIndexableMap,
                this::buildTypeToIndexableMap);
        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzer);
        try (final IndexWriter writer = new IndexWriter(directory, indexWriterConfig)) {
            updateProgress.set(0);
            updateTotal.set(list.size());
            list.stream()
                    .takeWhile(createTaskTerminatedCheck(taskContext))
                    .forEach(event -> {
                        updateDocInIndex(taskContext, event, writer, analyzer, typeToIndexableMap);
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

    /**
     * Caller should ensure this is called under cluster lock (if shared storage)
     */
    private void updateDocInIndex(final TaskContext taskContext,
                                  final EntityEvent event,
                                  final IndexWriter writer,
                                  final Analyzer analyzer,
                                  final Map<String, ContentIndexable> typeToIndexableMap) {
        LOGGER.debug("updateDocInIndex() - event: {}", event);
        updateProgress.incrementAndGet();
        try {
            final DocRef docRef = event.getDocRef();
            switch (event.getAction()) {
                case CREATE -> {
                    setTaskContextInfo(
                            taskContext, event, "Adding", updateProgress, updateTotal);
                    addDoc(taskContext, writer, docRef, typeToIndexableMap);
                }
                case UPDATE -> {
                    setTaskContextInfo(
                            taskContext, event, "Updating", updateProgress, updateTotal);
                    final DocRef oldDocRef = event.getOldDocRef();
                    if (oldDocRef != null && !Objects.equals(oldDocRef, docRef)) {
                        // This is a rename, so delete the old one
                        deleteDoc(writer, oldDocRef, analyzer);
                    }
                    // Try to delete the current docRef to be sure.
                    deleteDoc(writer, docRef, analyzer);
                    addDoc(taskContext, writer, docRef, typeToIndexableMap);
                }
                case DELETE -> {
                    setTaskContextInfo(
                            taskContext, event, "Deleting", updateProgress, updateTotal);
                    deleteDoc(writer, docRef, analyzer);
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

    private void addDoc(final TaskContext taskContext, final IndexWriter writer, final DocRef docRef,
                        final Map<String, ContentIndexable> typeToIndexableMap) {
        final ContentIndexable contentIndexable = typeToIndexableMap.get(docRef.getType());
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

    private void deleteDoc(final IndexWriter writer, final DocRef docRef, final Analyzer analyzer) {
        try {
            final Query query = createDocQuery(docRef, null, analyzer);
            writer.deleteDocuments(query);
        } catch (final ParseException | IOException e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private Query createDocQuery(final DocRef docRef,
                                 final String extension,
                                 final Analyzer analyzer) throws ParseException {
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
        ensureAnalyzerAndIndexableMap();

        boolean isInitialised = ensureIndexAsync();
        if (!isInitialised) {
            try {
                // Make the user wait a wee bit if it is not initialised as this gives it a chance
                // to return a non-zero % so the user gets the impression that it is underway.
                isInitialised = indexInitialisedLatch.await(LATCH_TIMEOUT_MS, TimeUnit.MILLISECONDS);
                LOGGER.debug("ensureIndexAndWait() - After wait of {}ms, isInitialised: {}",
                        LATCH_TIMEOUT_MS, isInitialised);
            } catch (final InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        if (!isInitialised) {
            final int updateCount = updateProgress.get();
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

    /**
     * Run runnable under cluster lock (if on shared storage) only if not already locked.
     * If not on shared storage, runnable is run.
     */
    private synchronized void runIfNotLocked(final Runnable runnable) {
        if (indexDir.isShared()) {
            final DurationTimer timer = DurationTimer.start();
            clusterLockService.tryLock(LOCK_NAME, () -> {
                LOGGER.debug("runIfNotLocked() - Acquired lock {}, duration: {}", LOCK_NAME, timer);
                runnable.run();
                LOGGER.debug("runIfNotLocked() - Releasing lock {}, duration: {}", LOCK_NAME, timer);
            });
        } else {
            LOGGER.debug("runIfNotLocked() - Running with no cluster locking");
            runnable.run();
        }
    }

    /**
     * Run with an exclusive cluster lock if the index is on shared storage
     */
    private synchronized void runUnderLock(final Runnable runnable) {
        if (indexDir.isShared()) {
            final DurationTimer timer = DurationTimer.start();
            clusterLockService.lock(LOCK_NAME, () -> {
                LOGGER.debug("runUnderLock() - Acquired lock {}, duration: {}", LOCK_NAME, timer);
                runnable.run();
                LOGGER.debug("runUnderLock() - Releasing lock {}, duration: {}", LOCK_NAME, timer);
            });
        } else {
            LOGGER.debug("runUnderLock() - Running with no cluster locking");
            runnable.run();
        }
    }

//    /**
//     * Run with an exclusive cluster lock if the index is on shared storage
//     */
//    private synchronized void runUnderLock(final String lockName, final Runnable runnable) {
//        if (indexDir.isShared()) {
//            final DurationTimer timer = DurationTimer.start();
//            clusterLockService.lock(lockName, () -> {
//                LOGGER.debug("runUnderLock() - Acquired lock {}, duration: {}", lockName, timer);
//                runnable.run();
//                LOGGER.debug("runUnderLock() - Releasing lock {}, duration: {}", lockName, timer);
//            });
//        } else {
//            LOGGER.debug("runUnderLock() - Running with no cluster locking");
//            runnable.run();
//        }
//    }

    private boolean shouldConsumeEvent(final EntityEvent event) {
        boolean result;
        if (event != null) {
            if (isIndexInitialised()) {
                if (indexDir.isShared()) {
                    // When on shared storage, only one node needs to handle the events.
                    // In the absence of any other mechanism to ensure events are only consumed by one
                    // node, use the master node status. Note: the master node can change as nodes
                    // are stopped/started.
                    try {
                        final String thisNodeName = nodeInfo.getThisNodeName();
                        final String masterNode = clusterNodeManager.getClusterState().getMasterNodeName();
                        // Takes time to establish master node, so if not known let all nodes consume it
                        if (masterNode == null) {
                            LOGGER.warn("shouldConsumeEvent() - master node unknown, consuming on all nodes");
                            result = true;
                        } else {
                            result = Objects.equals(masterNode, thisNodeName);
                        }
                    } catch (final Exception e) {
                        LOGGER.error("Error establishing master node - {}", LogUtil.exceptionMessage(e), e);
                        // Can't establish master node so just run on all nodes, no harm, just duplicated effort
                        result = true;
                    }
                } else {
                    result = true;
                }
            } else {
                result = false;
            }
        } else {
            result = false;
        }

        LOGGER.debug("shouldConsumeEvent() - Returning {} for event: {}", result, event);
        return result;
    }

    private boolean isThisMasterNode() {
        final boolean result;
        try {
            final String thisNodeName = nodeInfo.getThisNodeName();
            final String masterNode = clusterNodeManager.getClusterState().getMasterNodeName();
            return Objects.equals(masterNode, thisNodeName);
        } catch (final Exception e) {
            // Can't establish master node so just run on all nodes, no harm, just duplicated effort
            return false;
        }
    }

    private SearcherManager getSearcherManager() {
        // We can't create one until the index has been initialised, so do it lazily
        if (searcherManager != null) {
            return searcherManager;
        } else {
            if (!isIndexInitialised()) {
                throw new IllegalStateException("Can't create SearcherManager, index not initialised yet");
            }
            synchronized (this) {
                if (searcherManager == null) {
                    try {
                        searcherManager = new SearcherManager(directory, new SearcherFactory());
                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }
            }
        }
        return searcherManager;
    }

    private BlockingQueue<EntityEvent> getEventQueue() {
        // If on shared storage we only want the node that is handling events, i.e. the current master,
        // to spin up the consumer thread
        if (eventQueue == null) {
            synchronized (this) {
                if (eventQueue == null) {
                    // Create the queue to receive events
                    initQueueProcessorThread();
                }
            }
        }
        return eventQueue;
    }


    // --------------------------------------------------------------------------------


    static class NGramAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            final TokenStream tokenStream = new LowerCaseFilter(tokenizer);
            return new TokenStreamComponents(tokenizer, tokenStream);
        }
    }


    // --------------------------------------------------------------------------------


    static class NGramCSAnalyzer extends Analyzer {

        @Override
        protected TokenStreamComponents createComponents(final String fieldName) {
            final Tokenizer tokenizer = new NGramTokenizer(MIN_GRAM, MAX_GRAM);
            return new TokenStreamComponents(tokenizer, tokenizer);
        }
    }


    // --------------------------------------------------------------------------------


    private static final class IndexDir {

        private final Path path;
        private final StorageType storageType;
        //        private final Path lockFile;
        private final Path stateFile;

        private IndexDir(final Path path, final StorageType storageType) {
            this.path = path;
            this.storageType = storageType;
//            this.lockFile = path.resolve("content_index.lock");
            this.stateFile = path.resolve("content_index.state");
        }

        private boolean isShared() {
            return storageType == StorageType.SHARED;
        }

        public Path path() {
            return path;
        }

        public StorageType storageType() {
            return storageType;
        }

//        public Path getLockFile() {
//            return lockFile;
//        }

        public Path getStateFile() {
            return stateFile;
        }

        @Override
        public String toString() {
            return "IndexDir[" +
                   "path=" + path + ", " +
                   "storageType=" + storageType + ']';
        }
    }


    // --------------------------------------------------------------------------------


    private record IndexState(long lastRebuildEpochMs) {

        public static final int TOTAL_BYTES = Long.BYTES;
        public static final IndexState ZERO = new IndexState(0);

        static IndexState deserialise(final byte[] bytes) {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(Objects.requireNonNull(bytes));
            return deserialise(byteBuffer);
        }

        private static IndexState deserialise(final ByteBuffer byteBuffer) {
            if (byteBuffer.remaining() == 0) {
                return ZERO;
            }
            final long lastRebuildEpochMs = byteBuffer.getLong();
            return new IndexState(lastRebuildEpochMs);
        }

        byte[] serialise() {
            final ByteBuffer byteBuffer = ByteBuffer.wrap(new byte[TOTAL_BYTES]);
            serialise(byteBuffer);
            return byteBuffer.array();
        }

        void serialise(final ByteBuffer byteBuffer) {
            Objects.requireNonNull(byteBuffer);
            byteBuffer.putLong(lastRebuildEpochMs);
        }

        @Override
        public String toString() {
            return Instant.ofEpochMilli(lastRebuildEpochMs).toString();
        }

        Instant getLastRebuildTime() {
            return Instant.ofEpochMilli(lastRebuildEpochMs);
        }
    }
}
