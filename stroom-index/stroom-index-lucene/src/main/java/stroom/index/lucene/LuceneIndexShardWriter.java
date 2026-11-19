/*
 * Copyright 2016 Crown Copyright
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

package stroom.index.lucene;

import stroom.index.impl.IndexConfig;
import stroom.index.impl.IndexDocument;
import stroom.index.impl.IndexShardCommitListener;
import stroom.index.impl.IndexShardDao;
import stroom.index.impl.IndexShardUtil;
import stroom.index.impl.IndexShardWriter;
import stroom.index.impl.ShardFullException;
import stroom.index.impl.UncheckedLockObtainException;
import stroom.index.lucene.analyser.AnalyzerFactory;
import stroom.index.shared.IndexConstants;
import stroom.index.shared.IndexException;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexVolume;
import stroom.query.api.datasource.AnalyzerType;
import stroom.query.api.datasource.IndexField;
import stroom.search.extraction.FieldValue;
import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.logging.LoggerPrintStream;
import stroom.util.shared.ModelStringUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.LongField;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexNotFoundException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LiveIndexWriterConfig;
import org.apache.lucene.index.Term;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.TermQuery;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.NIOFSDirectory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.BooleanSupplier;

class LuceneIndexShardWriter implements IndexShardWriter {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(LuceneIndexShardWriter.class);

    private static final int DEFAULT_RAM_BUFFER_MB_SIZE = 1024;

    /**
     * Used to manage the way fields are analysed.
     */
    private final Map<String, Analyzer> fieldAnalyzers = new ConcurrentHashMap<>();

    private final IndexShardDao indexShardDao;
    private final StroomDuration slowIndexWriteWarningThreshold;
    /**
     * When we are in debug mode we track some important info from the LUCENE
     * log so that we can report some debug info
     */
    private static final Map<String, String> LOG_WATCH_TERMS;
    private final long indexShardId;
    private final Path dir;

    static {
        LOG_WATCH_TERMS = new ConcurrentHashMap<>();
        LOG_WATCH_TERMS.put("Flush Count", "flush: now pause all indexing threads");
        LOG_WATCH_TERMS.put("Commit Count", "startCommit()");
    }

    /**
     * Lucene stuff
     */
    private final Directory directory;
    private final IndexWriter indexWriter;

    /**
     * A count of documents added to the index used to control the maximum number of documents that are added.
     * Note that due to the multi-threaded nature of document addition and how this count is used to control
     * addition this will not always be accurate.
     */
    private final AtomicInteger documentCount;

    private final long creationTime;
    private volatile int maxDocumentCount;

    private final AtomicBoolean open = new AtomicBoolean();
    // Null unless the shard is on a shared volume, where replaced commits are kept for readers on other nodes.
    private final RetainReplacedCommitsDeletionPolicy replacedCommitsPolicy;
    // Says whether this node still holds the lease on the shard. A shard on a local volume has no lease.
    private volatile BooleanSupplier leaseHeld = () -> true;
    // Set once this writer has found that the lease is no longer held. It then never writes again, even if
    // a late renewal makes the lease look held once more.
    private volatile boolean leaseLost;
    // Told about each commit, so the streams it makes safe can be recorded. Null if nothing is to be told.
    private volatile IndexShardCommitListener commitListener;
    // Set when a commit has failed and that could not be recorded, or the lease was lost.
    private volatile boolean needsPendingStreamRecovery;
    private final AtomicInteger adding = new AtomicInteger();
    private final FieldFactory fieldFactory;

    /**
     * Convenience constructor used in tests.
     */
    LuceneIndexShardWriter(final IndexShardDao indexShardDao,
                           final IndexConfig indexConfig,
                           final IndexShard indexShard,
                           final PathCreator pathCreator,
                           final int maxDocumentCount,
                           final FieldFactory fieldFactory) {
        this(indexShardDao,
                indexConfig,
                indexShard,
                DEFAULT_RAM_BUFFER_MB_SIZE,
                pathCreator,
                maxDocumentCount,
                fieldFactory);
    }

    LuceneIndexShardWriter(final IndexShardDao indexShardDao,
                           final IndexConfig indexConfig,
                           final IndexShard indexShard,
                           final int ramBufferSizeMB,
                           final PathCreator pathCreator,
                           final int maxDocumentCount,
                           final FieldFactory fieldFactory) {
        try {
            this.indexShardDao = indexShardDao;
            this.slowIndexWriteWarningThreshold = NullSafe.getOrElse(
                    indexConfig,
                    IndexConfig::getIndexWriterConfig,
                    stroom.index.impl.IndexWriterConfig::getSlowIndexWriteWarningThreshold,
                    StroomDuration.ZERO);
            this.indexShardId = indexShard.getId();
            this.creationTime = System.currentTimeMillis();
            this.maxDocumentCount = maxDocumentCount;
            this.fieldFactory = fieldFactory;

            // Find the index shard dir.
            dir = IndexShardUtil.getIndexPath(indexShard, pathCreator);
            LOGGER.debug(() -> LogUtil.message("Creating index shard writer for dir {} {}", dir, this));

            final Directory directory;

            // Open the index writer.
            // If we already have a directory then this is an existing index.
            final String path = FileUtil.getCanonicalPath(dir);
            if (Files.isDirectory(dir)) {
                final long count = FileUtil.count(dir);
                LOGGER.debug(() -> dir + " exists, file count: " + count + " " + this);
                if (count == 0) {
                    throw new IndexException("Unable to find any index shard data in directory: " + path);
                }

            } else {
                // Make sure the index hasn't been deleted.
                LOGGER.debug(() ->
                        dir + " doesn't exist, doc count: " + indexShard.getDocumentCount() + " " + this);
                if (indexShard.getDocumentCount() > 0) {
                    throw new IndexException("Unable to find any index shard data in directory: " + path);
                }

                // Try and make all required directories.
                try {
                    Files.createDirectories(dir);
                } catch (final IOException e) {
                    LOGGER.error(() ->
                                    buildErrorMessage("Error creating path " + dir, e),
                            e);
                    throw new IndexException("Unable to create directories for new index in \"" + path + "\"");
                }
            }

            // Create the index writer config.
            // Setup the field analyzers.
            final Analyzer defaultAnalyzer = AnalyzerFactory.create(AnalyzerType.ALPHA_NUMERIC, false);
            final PerFieldAnalyzerWrapper analyzerWrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer,
                    fieldAnalyzers);
            final IndexWriterConfig luceneIndexWriterConfig = new IndexWriterConfig(analyzerWrapper);

            // In trace mode we do extra trace in LUCENE and we also count
            // certain logging info like merge and flush
            // counts, so you can get this later using the trace method.
            if (LOGGER.isTraceEnabled()) {
                final LoggerPrintStream loggerPrintStream = new LoggerPrintStream(LOGGER);
                for (final String term : LOG_WATCH_TERMS.values()) {
                    loggerPrintStream.addWatchTerm(term);
                }
                luceneIndexWriterConfig.setInfoStream(loggerPrintStream);
            }

            // Other nodes may be reading a shard on a shared volume, so keep the commits this writer
            // replaces for long enough that they can finish. A shard on a local volume is only read by this
            // node, where a deleted file stays readable for as long as it is open.
            final long replacedCommitRetentionMs = NullSafe.test(indexShard.getVolume(), IndexVolume::isShared)
                    ? NullSafe.getOrElse(
                            indexConfig,
                            IndexConfig::getIndexWriterConfig,
                            stroom.index.impl.IndexWriterConfig::getReplacedCommitRetention,
                            StroomDuration.ZERO).toMillis()
                    : 0;
            if (replacedCommitRetentionMs > 0) {
                replacedCommitsPolicy = new RetainReplacedCommitsDeletionPolicy(
                        replacedCommitRetentionMs,
                        System::currentTimeMillis);
                luceneIndexWriterConfig.setIndexDeletionPolicy(replacedCommitsPolicy);
            } else {
                replacedCommitsPolicy = null;
            }

            // Create lucene directory object.
            // Lucene will refuse to change any file of the shard once the lease on it is no longer held.
            directory = new LeaseCheckedDirectory(
                    new NIOFSDirectory(dir, LuceneLockFactory.get()),
                    () -> !leaseLost && leaseHeld.getAsBoolean());

            // IndexWriter to use for adding data to the index.
            final IndexWriter indexWriter = new IndexWriter(directory, luceneIndexWriterConfig);
            LOGGER.debug("Marking shard writer as open. {}", this);
            open.set(true);

            final LiveIndexWriterConfig liveIndexWriterConfig = indexWriter.getConfig();
            liveIndexWriterConfig.setRAMBufferSizeMB(ramBufferSizeMB);

            // TODO : We might still want to write separate segments I'm not sure on pros/cons?
            liveIndexWriterConfig.setUseCompoundFile(false);
            liveIndexWriterConfig.setMaxBufferedDocs(Integer.MAX_VALUE);

            // Check the number of committed docs in this shard.
            final int numDocs = indexWriter.getDocStats().numDocs;
            final AtomicInteger documentCount = new AtomicInteger(numDocs);

            if (indexShard.getDocumentCount() != numDocs) {
                LOGGER.error(() ->
                        "Mismatch document count. Index says "
                        + numDocs
                        + " DB says "
                        + indexShard.getDocumentCount()
                        + " "
                        + this);
            }

            this.directory = directory;
            this.indexWriter = indexWriter;
            this.documentCount = documentCount;
        } catch (final LockObtainFailedException e) {
            throw new UncheckedLockObtainException(e);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void addDocument(final IndexDocument indexDocument) throws IndexException {
        final Document document = new Document();
        for (final FieldValue fieldValue : indexDocument.getValues()) {
            final IndexField indexField = fieldValue.field();

            // Ensure analyzer is present.
            fieldAnalyzers.computeIfAbsent(indexField.getFldName(), k -> {
                LOGGER.debug(() ->
                        "Adding field analyser for: " + indexField.getFldName() + " " + this);
                // Add the field analyser.
                return AnalyzerFactory.create(indexField.getAnalyzerType(), indexField.isCaseSensitive());
            });

            final Collection<Field> fields = fieldFactory.create(fieldValue);

            // Add the fields to the document.
            for (final Field field : fields) {
                document.add(field);
            }
        }
        if (!document.getFields().isEmpty()) {
            addDocument(document);
        }
    }

    private void addDocument(final Document document) throws IndexException {
        if (leaseLost || !leaseHeld.getAsBoolean()) {
            // Another node may be about to write to the shard, so this writer must add nothing more to it.
            throw new IndexException("Unable to add to index shard " + indexShardId
                                     + " as this node no longer holds the lease on it");
        }
        adding.incrementAndGet();
        try {
            // An Exception might be thrown here if the index
            // has been deleted. If this happens log the error
            // and return false so that the pool can return a
            // new index to add documents to.
            try {
                if (documentCount.getAndIncrement() >= maxDocumentCount) {
                    throw new ShardFullException("Shard is full");
                }

                final Instant startTime = Instant.now();
                indexWriter.addDocument(document);

                if (!slowIndexWriteWarningThreshold.isZero()) {
                    final Duration duration = Duration.between(startTime, Instant.now());

                    if (duration.compareTo(slowIndexWriteWarningThreshold.getDuration()) > 0) {
                        LOGGER.warn(() ->
                                "addDocument() - "
                                + this
                                + " took "
                                + duration
                                + " (Warning threshold: "
                                + slowIndexWriteWarningThreshold
                                + ". Configure this with property"
                                + " stroom.index.writer.slowIndexWriteWarningThreshold)");
                    }
                }
            } catch (final RuntimeException e) {
                documentCount.decrementAndGet();
                throw e;
            }
        } catch (final IOException e) {
            throw new UncheckedIOException(e);

        } finally {
            adding.decrementAndGet();
        }
    }

    @Override
    public void setMaxDocumentCount(final int maxDocumentCount) {
        this.maxDocumentCount = maxDocumentCount;
    }

    @Override
    public synchronized void flush() {
        if (open.get()) {
            // Record commit start time.
            final long startTime = System.currentTimeMillis();
            LOGGER.debug(() -> "Starting flush " + this);

            try {
                commit("commit");

            } catch (final IOException | RuntimeException e) {
                LOGGER.error(buildErrorMessage("Error while committing writer.", e), e);
                commitFailed();

            } finally {
                // Update the shard info
                updateShardInfo(startTime);
            }

            LOGGER.debug(() ->
                    "Finished flush in " +
                    ModelStringUtil.formatDurationString(System.currentTimeMillis() - startTime) +
                    ") " + this);
        }
    }

    // Commits in two steps. The first writes and syncs the files, which can be slow. The second makes the
    // commit visible and is quick. The lease is checked between the two, so that a commit never becomes
    // visible long after the lease was last known to be held. Returns false if the lease had gone, when
    // the writer has been rolled back and closed.
    private boolean commit(final String action) throws IOException {
        final List<Long> pendingStreamRowIds = beforeCommit();
        if (prepareCommit()) {
            indexWriter.commit();
            afterCommit(pendingStreamRowIds);
            return true;
        } else {
            abandon(action);
            return false;
        }
    }

    // The first step of a commit. Returns false if the lease was not held, or was lost while the files
    // were being written. Lucene is stopped from changing files as soon as the lease has gone, so losing it
    // part way through shows up as a failure to write.
    private boolean prepareCommit() throws IOException {
        if (!leaseHeld.getAsBoolean()) {
            return false;
        }
        try {
            indexWriter.prepareCommit();
        } catch (final IOException | RuntimeException e) {
            if (leaseHeld.getAsBoolean()) {
                throw e;
            }
            LOGGER.debug(() -> "Index shard " + indexShardId + " not committed: " + e.getMessage(), e);
        }
        return leaseHeld.getAsBoolean();
    }

    // Notes the streams whose tasks have finished. Their documents are all in the writer by now, so the
    // commit that follows makes them safe.
    private List<Long> beforeCommit() {
        final IndexShardCommitListener listener = commitListener;
        if (listener != null) {
            try {
                return listener.beforeCommit();
            } catch (final RuntimeException e) {
                // They stay as they are and are picked up by a later commit.
                LOGGER.error(buildErrorMessage("Error finding pending streams before commit.", e), e);
            }
        }
        return null;
    }

    private void afterCommit(final List<Long> pendingStreamRowIds) {
        final IndexShardCommitListener listener = commitListener;
        if (listener != null && pendingStreamRowIds != null) {
            try {
                listener.afterCommit(pendingStreamRowIds);
            } catch (final RuntimeException e) {
                // They stay as they are and are picked up by a later commit.
                LOGGER.error(buildErrorMessage("Error marking pending streams as committed.", e), e);
            }
        }
    }

    private void commitFailed() {
        final IndexShardCommitListener listener = commitListener;
        if (listener != null) {
            try {
                listener.commitFailed();
            } catch (final RuntimeException e) {
                // Could not be recorded now, so it is recorded on the shard when this writer closes.
                needsPendingStreamRecovery = true;
                LOGGER.error(buildErrorMessage("Error marking pending streams for recovery.", e), e);
            }
        }
    }

    @Override
    public synchronized void deleteStreams(final Collection<Long> streamIds) throws IndexException {
        if (!open.get() || leaseLost || !leaseHeld.getAsBoolean()) {
            throw new IndexException("Unable to remove streams from index shard " + indexShardId
                                     + " as its writer is closed or no longer holds the lease on it");
        }
        final long startTime = System.currentTimeMillis();
        try {
            // The stream id is indexed as a number in all but the oldest indexes, where it is a term.
            // Each query matches nothing in an index of the other kind.
            final List<Query> queries = new ArrayList<>();
            for (final Long streamId : streamIds) {
                queries.add(LongField.newExactQuery(IndexConstants.STREAM_ID, streamId));
                queries.add(new TermQuery(new Term(IndexConstants.STREAM_ID, streamId.toString())));
            }
            indexWriter.deleteDocuments(queries.toArray(new Query[0]));
            if (!commit("remove streams from")) {
                throw new IndexException("Unable to remove streams from index shard " + indexShardId
                                         + " as this node no longer holds the lease on it");
            }
            // Adding carries on meanwhile, so this is as near as the count can be got.
            documentCount.set(indexWriter.getDocStats().numDocs);
        } catch (final IndexException e) {
            throw e;
        } catch (final IOException | RuntimeException e) {
            // The commit may have been of other streams' documents too.
            commitFailed();
            throw new IndexException("Unable to remove streams from index shard " + indexShardId
                                     + ": " + e.getMessage(), e);
        } finally {
            updateShardInfo(startTime);
        }
    }

    @Override
    public void setCommitListener(final IndexShardCommitListener commitListener) {
        this.commitListener = commitListener;
    }

    @Override
    public boolean needsPendingStreamRecovery() {
        return needsPendingStreamRecovery;
    }

    @Override
    public synchronized void close() {
        if (open.get()) {
            // Record close start time.
            final long startTime = System.currentTimeMillis();
            LOGGER.debug(() -> "Starting close " + this);

            try {
                // Perform close.
                // Wait for us to stop adding docs.
                try {
                    while (adding.get() > 0) {
                        LOGGER.debug(() -> "Waiting for " + adding.get() +
                                           " docs to finish being added before we can close this shard");
                        Thread.sleep(1000);
                    }
                } catch (final InterruptedException e) {
                    LOGGER.debug(() -> "Interrupted waiting for docs to be added. " + this);
                    // Interrupt this thread again.
                    Thread.currentThread().interrupt();
                }

                try {
                    // Commit in two steps as when flushing, with the lease checked again after the slow
                    // one, so that the commit never becomes visible long after the lease was last known to
                    // be held.
                    boolean held = leaseHeld.getAsBoolean();
                    List<Long> pendingStreamRowIds = null;
                    if (held) {
                        pendingStreamRowIds = beforeCommit();
                        held = prepareCommit();
                    }
                    if (held) {
                        // Lucene refuses to close with a commit prepared but not finished.
                        indexWriter.commit();
                        afterCommit(pendingStreamRowIds);
                        indexWriter.close();
                    } else {
                        discardUncommitted("close");
                    }
                } catch (final IOException | RuntimeException e) {
                    LOGGER.error(e::getMessage, e);
                    // Recorded on the shard as it is closed, in the same update that closes it.
                    needsPendingStreamRecovery = true;
                } finally {
                    try {
                        if (directory != null) {
                            directory.close();
                        }
                    } catch (final IOException | RuntimeException e) {
                        LOGGER.error(buildErrorMessage("Error closing directory.", e), e);
                    }

                    open.set(false);
                }

            } catch (final RuntimeException e) {
                LOGGER.error(buildErrorMessage("Error closing shard writer.", e), e);
            } finally {
                // Update the shard info
                updateShardInfo(startTime);
            }

            LOGGER.debug(() -> "Finished close in " +
                               ModelStringUtil.formatDurationString((System.currentTimeMillis() - startTime))
                               + ") "
                               + this);
        }
    }

    // Stops this writer being used once the lease on its shard is no longer held. Whatever it has not
    // committed is discarded, as another node may by now be writing to the shard.
    private void abandon(final String action) throws IOException {
        try {
            discardUncommitted(action);
        } finally {
            try {
                if (directory != null) {
                    directory.close();
                }
            } finally {
                open.set(false);
            }
        }
    }

    private void discardUncommitted(final String action) throws IOException {
        leaseLost = true;
        // The streams whose documents are discarded here are recovered by whichever node next opens the
        // shard. The database may well be out of reach now, which is the usual reason for losing a lease.
        needsPendingStreamRecovery = true;
        final int documentCountBefore = documentCount.get();
        // Rolling back also closes the Lucene writer. It would delete every file in the shard that this
        // writer does not know about, which may now include files written by another node. The directory
        // stops it, so if there is anything it wants to delete it fails, having still closed the writer.
        // The files this writer leaves behind are removed by the next writer to open the shard.
        try {
            indexWriter.rollback();
        } catch (final IOException | RuntimeException e) {
            LOGGER.debug(() -> "Index shard " + indexShardId + " left as it is: " + e.getMessage(), e);
        }
        final int committedDocumentCount = countCommittedDocuments();
        documentCount.set(committedDocumentCount);
        LOGGER.error(() -> "Unable to " + action + " index shard " + indexShardId
                           + " as this node no longer holds the lease on it. Discarded about "
                           + Math.max(0, documentCountBefore - committedDocumentCount)
                           + " documents that had not been committed. " + this);
    }

    // What is really in the shard, read from its latest commit.
    private int countCommittedDocuments() throws IOException {
        try (final DirectoryReader reader = DirectoryReader.open(directory)) {
            return reader.numDocs();
        } catch (final IndexNotFoundException e) {
            // Nothing was ever committed.
            return 0;
        }
    }

    @Override
    public void setLeaseCheck(final BooleanSupplier leaseHeld) {
        this.leaseHeld = Objects.requireNonNull(leaseHeld);
    }

    @Override
    public boolean hasReplacedCommits() {
        return replacedCommitsPolicy != null && replacedCommitsPolicy.getRetainedCommitCount() > 0;
    }

    @Override
    public synchronized void pruneReplacedCommits() {
        if (open.get() && replacedCommitsPolicy != null) {
            try {
                replacedCommitsPolicy.expireAll();
                // Makes Lucene ask the policy again which commits to keep.
                indexWriter.deleteUnusedFiles();
            } catch (final IOException e) {
                throw new UncheckedIOException(e);
            }
        }
    }

    private synchronized void updateShardInfo(final long startTime) {
        if (leaseLost) {
            // The shard's row is no longer this node's to change.
            return;
        }
        try {
            // If the index is closed we can be sure no additional documents were added successfully.
            final Integer lastDocumentCount = documentCount.get();

            // Record when commit completed so we know how fresh the index
            // is for searching purposes.
            final Long lastCommitMs = System.currentTimeMillis();

            // Find out how many docs were committed and how long it took.
            final long timeNow = System.currentTimeMillis();
            final Long lastCommitDurationMs = (timeNow - startTime);

            // Update the size of the index.
            final Long fileSize = calcFileSize();

            update(indexShardId, lastDocumentCount, lastCommitDurationMs, lastCommitMs, fileSize);
        } catch (final RuntimeException e) {
            LOGGER.error(buildErrorMessage("Error updating shard info.", e), e);
        }
    }

    private Long calcFileSize() {
        Long fileSize = null;
        try {
            if (dir != null) {
                final AtomicLong totalSize = new AtomicLong();
                try (final DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
                    stream.forEach(file -> {
                        try {
                            totalSize.getAndAdd(Files.size(file));
                        } catch (final IOException e) {
                            LOGGER.trace(buildErrorMessage("IO error getting file size.", e), e);
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.trace(buildErrorMessage("IO error getting file sizes.", e), e);
                }
                fileSize = totalSize.get();
            }
        } catch (final RuntimeException e) {
            LOGGER.debug(buildErrorMessage("Error calculating file sizes.", e), e);
        }
        return fileSize;
    }

    //    @Override
    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public int getDocumentCount() {
        return documentCount.get();
    }

    private void update(final long indexShardId,
                        final Integer documentCount,
                        final Long commitDurationMs,
                        final Long commitMs,
                        final Long fileSize) {
        if (indexShardDao != null) {
            // Output some debug so we know how long commits are taking.
            LOGGER.debug(() -> String.format("Documents written %s (%s)",
                    documentCount,
                    ModelStringUtil.formatDurationString(commitDurationMs)));
            indexShardDao.update(indexShardId, documentCount, commitDurationMs, commitMs, fileSize);
        }
    }

    @Override
    public long getIndexShardId() {
        return indexShardId;
    }

    @Override
    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public String toString() {
        return "(id=" + indexShardId + ")";
    }

    private String buildErrorMessage(final String message,
                                     final Throwable throwable) {
        return message
               + " "
               + NullSafe.getOrElse(throwable, Throwable::getMessage, "null")
               + " "
               + this;
    }
}
