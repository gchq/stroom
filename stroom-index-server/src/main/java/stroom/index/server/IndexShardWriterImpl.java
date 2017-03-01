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

package stroom.index.server;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.CorruptIndexException;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LiveIndexWriterConfig;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.LockObtainFailedException;
import org.apache.lucene.store.NIOFSDirectory;
import org.apache.lucene.store.SimpleFSLockFactory;
import org.apache.lucene.util.Version;
import org.joda.time.DateTime;
import stroom.index.server.analyzer.AnalyzerFactory;
import stroom.index.shared.Index;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexFields;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShard.IndexShardStatus;
import stroom.index.shared.IndexShardService;
import stroom.streamstore.server.fs.FileSystemUtil;
import stroom.util.logging.LoggerPrintStream;
import stroom.util.logging.StroomLogger;
import stroom.util.shared.ModelStringUtil;

import javax.persistence.EntityNotFoundException;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.locks.ReentrantLock;

public class IndexShardWriterImpl implements IndexShardWriter {
    public static final int DEFAULT_RAM_BUFFER_MB_SIZE = 1024;
    private static final StroomLogger LOGGER = StroomLogger.getLogger(IndexShardWriterImpl.class);
    /**
     * When we are in debug mode we track some important info from the LUCENE
     * log so that we can report some debug info
     */
    private static final Map<String, String> LOG_WATCH_TERMS;

    static {
        LOG_WATCH_TERMS = new ConcurrentHashMap<>();
        LOG_WATCH_TERMS.put("Flush Count", "flush: now pause all indexing threads");
        LOG_WATCH_TERMS.put("Commit Count", "startCommit()");
    }

    /**
     * Used to manage the way fields are analysed.
     */
    private final Map<String, Analyzer> fieldAnalyzers;
    private final PerFieldAnalyzerWrapper analyzerWrapper;
    private final ReentrantLock fieldAnalyzerLock = new ReentrantLock();
    private final IndexShardService service;
    private final AtomicInteger documentCount = new AtomicInteger(0);
    private final Path dir;
    private final int maxDocumentCount;
    private volatile Index fieldIndex;
    private volatile IndexShard indexShard;
    private volatile int ramBufferSizeMB = DEFAULT_RAM_BUFFER_MB_SIZE;
    /**
     * Lucene stuff
     */
    private volatile Directory directory;
    private volatile IndexWriter indexWriter;
    private volatile int lastDocumentCount;
    private volatile long lastCommitMs;
    private volatile int lastCommitDocumentCount;
    private volatile long lastCommitDurationMs;
    private LoggerPrintStream loggerPrintStream;

    /**
     * Convenience constructor used in tests.
     */
    public IndexShardWriterImpl(final IndexShardService service, final IndexFields indexFields, final Index index,
                                final IndexShard indexShard) {
        this(service, indexFields, index, indexShard, DEFAULT_RAM_BUFFER_MB_SIZE);
    }

    public IndexShardWriterImpl(final IndexShardService service, final IndexFields indexFields, final Index index,
                                final IndexShard indexShard, final int ramBufferSizeMB) {
        this.service = service;
        this.indexShard = indexShard;
        this.ramBufferSizeMB = ramBufferSizeMB;
        this.maxDocumentCount = index.getMaxDocsPerShard();

        // Find the index shard path.
        dir = IndexShardUtil.getIndexPath(indexShard);

        // Make sure the index writer is primed with the necessary analysers.
        LOGGER.debug("Updating field analysers");

        // Setup the field analyzers.
        final Analyzer defaultAnalyzer = AnalyzerFactory.create(AnalyzerType.ALPHA_NUMERIC, false);
        fieldAnalyzers = new HashMap<>();
        updateFieldAnalyzers(indexFields);
        analyzerWrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzers);

    }

    /**
     * You can set this before the index has opened
     */
    public void setRamBufferSizeMB(final int ramBufferSizeMB) {
        this.ramBufferSizeMB = ramBufferSizeMB;
    }

    @Override
    public synchronized boolean open(final boolean create) {
        boolean success = false;

        try {
            if (!isOpen()) {
                if (IndexShardStatus.OPEN.equals(indexShard.getStatus())) {
                    LOGGER.warn("Attempt to open an index that is already marked as open");
                } else {
                    success = doOpen(create);
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    private synchronized boolean doOpen(final boolean create) {
        boolean success = false;

        try {
            // Never open deleted index shards.
            if (IndexShardStatus.DELETED.equals(indexShard.getStatus())) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Shard is deleted " + indexShard);
                }
                return false;
            }

            // Don't open old index shards for writing.
            final Version currentVersion = LuceneVersionUtil.getLuceneVersion(LuceneVersionUtil.getCurrentVersion());
            final Version shardVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
            if (!shardVersion.equals(currentVersion)) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Shard version is different to current version " + indexShard);
                }
                return false;
            }

            final long startMs = System.currentTimeMillis();
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Opening " + indexShard);
            }

            if (create) {
                // Make sure the index directory does not exist. If one does
                // then throw an exception
                // as we don't want to overwrite an index.
                if (Files.isDirectory(dir)) {
                    // This is a workaround for lingering .nfs files.
                    Files.list(dir).forEach(file -> {
                        if (Files.isDirectory(file) || !file.getFileName().startsWith(".")) {
                            throw new IndexException("Attempting to create a new index in \""
                                    + dir.toAbsolutePath().toString() + "\" but one already exists.");
                        }
                    });
                } else {
                    // Try and make all required directories.
                    try {
                        Files.createDirectories(dir);
                    } catch (final IOException e) {
                        throw new IndexException(
                                "Unable to create directories for new index in \"" + dir.toAbsolutePath().toString() + "\"");
                    }
                }
            }

            // Create lucene directory object.
            directory = new NIOFSDirectory(dir, SimpleFSLockFactory.INSTANCE);

            analyzerWrapper.setVersion(shardVersion);
            final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzerWrapper);

            // In debug mode we do extra trace in LUCENE and we also count
            // certain logging info like merge and flush
            // counts, so you can get this later using the trace method.
            if (LOGGER.isDebugEnabled()) {
                loggerPrintStream = new LoggerPrintStream(LOGGER);
                for (final String term : LOG_WATCH_TERMS.values()) {
                    loggerPrintStream.addWatchTerm(term);
                }
                indexWriterConfig.setInfoStream(loggerPrintStream);
            }

            // IndexWriter to use for adding data to the index.
            indexWriter = new IndexWriter(directory, indexWriterConfig);

            final LiveIndexWriterConfig liveIndexWriterConfig = indexWriter.getConfig();
            liveIndexWriterConfig.setRAMBufferSizeMB(ramBufferSizeMB);

            // TODO : We might still want to write separate segments I'm not
            // sure on pros/cons?
            liveIndexWriterConfig.setUseCompoundFile(false);
            liveIndexWriterConfig.setMaxBufferedDocs(Integer.MAX_VALUE);

            // Check the number of committed docs in this shard.
            documentCount.set(indexWriter.numDocs());
            lastDocumentCount = documentCount.get();
            if (create) {
                if (lastDocumentCount != 0) {
                    LOGGER.error("Index should be new but already contains docs: " + lastDocumentCount);
                }
            } else if (indexShard.getDocumentCount() != lastDocumentCount) {
                LOGGER.error("Mismatch document count.  Index says " + lastDocumentCount + " DB says "
                        + indexShard.getDocumentCount());
            }

            // We have opened the index so update the DB object.
            setStatus(IndexShardStatus.OPEN);

            // Output some debug.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("getIndexWriter() - Opened " + indexShard + " in "
                        + (System.currentTimeMillis() - startMs) + "ms");
            }

            success = true;
        } catch (final LockObtainFailedException t) {
            LOGGER.warn(t.getMessage());
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    @Override
    public boolean isOpen() {
        return indexWriter != null && IndexShardStatus.OPEN.equals(indexShard.getStatus());
    }

    @Override
    public synchronized boolean close() {
        boolean success = false;

        try {
            if (isOpen()) {
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Closing index: " + toString());
                }

                try {
                    success = flushOrClose(true);
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                } finally {
                    indexWriter = null;

                    try {
                        if (directory != null) {
                            directory.close();
                        }
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        directory = null;
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    @Override
    public synchronized boolean check() {
        boolean success = false;

        // Don't clean deleted indexes.
        if (!IndexShardStatus.DELETED.equals(indexShard.getStatus())) {
            try {
                // Output some debug.
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Checking index - " + indexShard);
                }
                // Mark the index as closed.
                setStatus(IndexShardStatus.CLOSED);

                if (Files.isDirectory(dir) && Files.list(dir).count() > 0) {
                    // The directory exists and contains files so make sure it
                    // is unlocked.
                    try {
                        final Path lockFile = dir.resolve(IndexWriter.WRITE_LOCK_NAME);
                        if (Files.isRegularFile(lockFile)) {
                            Files.delete(lockFile);
                        }
                    } catch (final IOException e) {
                        // There is no lock file so ignore.
                    }

                    // Sync the DB.
                    sync();
                    success = true;
                } else {
                    if (!Files.isDirectory(dir)) {
                        throw new IndexException("Unable to find index shard directory: " + dir.toString());
                    } else {
                        throw new IndexException(
                                "Unable to find any index shard data in directory: " + dir.toString());
                    }
                }
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }
        }

        return success;
    }

    @Override
    public synchronized boolean delete() {
        boolean success = false;

        try {
            // Output some debug.
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("Deleting index - " + indexShard);
            }

            // Just mark the index as deleted. We can clean it up later.
            setStatus(IndexShardStatus.DELETED);

            success = true;
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    @Override
    public synchronized boolean deleteFromDisk() {
        boolean success = false;

        try {
            if (!IndexShardStatus.DELETED.equals(indexShard.getStatus())) {
                LOGGER.warn("deleteFromDisk() - Can only be called on delete records %s", indexShard);
            } else {
                // Make sure the shard is closed before it is deleted. If it
                // isn't then delete will fail as there
                // are open file handles.
                try {
                    if (indexWriter != null) {
                        indexWriter.close();
                    }
                } catch (final Throwable t) {
                    LOGGER.error(t.getMessage(), t);
                } finally {
                    indexWriter = null;
                    try {
                        if (directory != null) {
                            directory.close();
                        }
                    } catch (final Throwable t) {
                        LOGGER.error(t.getMessage(), t);
                    } finally {
                        directory = null;
                    }
                }

                // See if there are any files in the directory.
                if (!Files.isDirectory(dir) || FileSystemUtil.deleteDirectory(dir)) {
                    // The directory either doesn't exist or we have
                    // successfully deleted it so delete this index
                    // shard from the database.
                    if (service != null) {
                        success = service.delete(indexShard);
                    }
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }

        return success;
    }

    @Override
    public boolean addDocument(final Document document) {
        return doAddDocument(document, 1);
    }

    private boolean doAddDocument(final Document document, final int tryCount) {
        boolean added = false;
        boolean retry = false;

        if (document != null) {
            // We can't write to deleted index shard's
            if (!IndexShardStatus.DELETED.equals(indexShard.getStatus())) {
                // Make sure the index is open (it won't open if we have hit the
                // limit.
                if (!isOpen() && documentCount.get() < maxDocumentCount) {
                    open(false);
                }

                // Make sure the index is now open before we try and add a
                // document to it.
                final IndexWriter indexWriter = this.indexWriter;
                if (indexWriter != null && isOpen()) {
                    // Avoid any sync blocks so try and inc the number and if it
                    // went over then drop it back and return false
                    if (documentCount.incrementAndGet() <= maxDocumentCount) {
                        try {
                            final long startTime = System.currentTimeMillis();

                            // An Exception might be thrown here if the index
                            // has been deleted. If this happens log the error
                            // and return false so that the pool can return a
                            // new index to add documents to.
                            indexWriter.addDocument(document);
                            added = true;

                            final long duration = System.currentTimeMillis() - startTime;
                            if (duration > 1000) {
                                LOGGER.warn("addDocument() - took " + ModelStringUtil.formatDurationString(duration)
                                        + " " + toString());
                            }
                        } catch (final AlreadyClosedException e) {
                            LOGGER.warn(e.getMessage(), e);
                            doOpen(false);
                            retry = true;
                        } catch (final CorruptIndexException e) {
                            LOGGER.error(e.getMessage(), e);
                            // Mark the shard as corrupt as this should be the
                            // only reason we can't add a document.
                            setStatus(IndexShardStatus.CORRUPT);
                        } catch (final Throwable t) {
                            LOGGER.error(t.getMessage(), t);
                            retry = true;
                        }

                        // If we were unable to add the document then decrement
                        // the document count.
                        if (!added) {
                            documentCount.decrementAndGet();
                        }
                    } else {
                        documentCount.decrementAndGet();
                    }
                }
            }
        }

        if (retry) {
            if (tryCount > 10) {
                LOGGER.warn("Giving up adding document to this index shard after " + tryCount + " tries");
                close();
            } else {
                LOGGER.debug("Retrying addDocument()");
                added = doAddDocument(document, tryCount + 1);
            }
        }

        return added;
    }

    @Override
    public void updateIndex(final Index index) {
        // There's no point updating the analysers on a deleted index.
        if (!isDeleted()) {
            // Check if this index shard has been deleted on the DB.
            try {
                final IndexShard is = service.load(indexShard);
                if (is != null && IndexShardStatus.DELETED.equals(is.getStatus())) {
                    indexShard.setStatus(IndexShardStatus.DELETED);
                }
            } catch (final EntityNotFoundException e) {
                LOGGER.debug("Index shard has been deleted %s", indexShard);
                indexShard.setStatus(IndexShardStatus.DELETED);
            } catch (final Throwable t) {
                LOGGER.error(t.getMessage(), t);
            }

            if (!isDeleted()) {
                sync();
                checkRetention(index);
            }

            if (!isDeleted()) {
                if (fieldIndex == null || fieldIndex.getVersion() != index.getVersion()) {
                    fieldAnalyzerLock.lock();
                    try {
                        if (fieldIndex == null || fieldIndex.getVersion() != index.getVersion()) {
                            fieldIndex = index;
                            final IndexFields indexFields = fieldIndex.getIndexFieldsObject();
                            updateFieldAnalyzers(indexFields);
                        }
                    } finally {
                        fieldAnalyzerLock.unlock();
                    }
                }
            }
        }

    }

    private void checkRetention(final Index index) {
        try {
            // Delete this shard if it is older than the retention age.
            if (index.getRetentionDayAge() != null && indexShard.getPartitionToTime() != null) {
                // See if this index shard is older than the index retention
                // period.
                final long retentionTime = new DateTime().minusDays(index.getRetentionDayAge()).getMillis();
                final long shardAge = indexShard.getPartitionToTime();

                if (shardAge < retentionTime) {
                    delete();
                }
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    private void updateFieldAnalyzers(final IndexFields indexFields) {
        if (indexFields != null) {
            final Version luceneVersion = LuceneVersionUtil.getLuceneVersion(indexShard.getIndexVersion());
            for (final IndexField indexField : indexFields.getIndexFields()) {
                // Add the field analyser.
                final Analyzer analyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                analyzer.setVersion(luceneVersion);
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("Adding field analyser for: " + indexField.getFieldName());
                }
                fieldAnalyzers.put(indexField.getFieldName(), analyzer);
            }
        }
    }

    @Override
    public synchronized boolean flush() {
        return flushOrClose(false);
    }

    /**
     * Flush and close operations will cause docs in memory to be committed to
     * the shard so we use the same method to do both so we can consistently
     * record commit times and doc counts.
     */
    private synchronized boolean flushOrClose(final boolean close) {
        boolean success = false;

        if (isOpen()) {
            // Record commit start time.
            final long startTime = System.currentTimeMillis();

            try {
                if (LOGGER.isDebugEnabled()) {
                    if (close) {
                        LOGGER.debug("Closing index: " + toString());
                    } else {
                        LOGGER.debug("Flushing index: " + toString());
                    }
                }

                // Find out how many docs the DB thinks the shard currently
                // contains.
                final int docCountBeforeCommit = indexShard.getDocumentCount();

                // Find out how many docs should be in the shard after commit.
                lastDocumentCount = documentCount.get();
                // Perform commit or close.
                if (close) {
                    indexWriter.close();
                } else {
                    indexWriter.commit();
                }
                // Record when commit completed so we know how fresh the index
                // is for searching purposes.
                lastCommitMs = System.currentTimeMillis();

                // Find out how many docs were committed and how long it took.
                lastCommitDocumentCount = lastDocumentCount - docCountBeforeCommit;
                final long timeNow = System.currentTimeMillis();
                lastCommitDurationMs = (timeNow - startTime);

                // Output some debug so we know how long commits are taking.
                if (LOGGER.isDebugEnabled()) {
                    final String durationString = ModelStringUtil.formatDurationString(lastCommitDurationMs);
                    LOGGER.debug("flushOrClose() - documents written since last flush " + lastCommitDocumentCount + " ("
                            + durationString + ")");
                }

                success = true;

            } catch (final Exception e) {
                LOGGER.error("flushOrClose()", e);

            } finally {
                // Synchronise the DB entry.
                if (close) {
                    setStatus(IndexShardStatus.CLOSED);
                } else {
                    sync();
                }

                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("flushOrClose() - lastCommitDocumentCount=" + lastCommitDocumentCount
                            + ", lastCommitDuration=" + lastCommitDurationMs);

                    final long duration = System.currentTimeMillis() - startTime;
                    final String durationString = ModelStringUtil.formatDurationString(duration);
                    LOGGER.debug("flushOrClose() - finish " + toString() + " " + durationString);
                }

            }
        }

        return success;
    }

    @Override
    protected void finalize() throws Throwable {
        if (indexWriter != null) {
            LOGGER.error("finalize() - Failed to close index");
        }

        super.finalize();
    }

    /**
     * Utility to update the stat's on the DB entity
     */
    public synchronized void sync() {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (service != null) {
            boolean success = false;
            for (int i = 0; i < 10 && success == false; i++) {
                refreshEntity();
                success = save();
            }
        } else {
            refreshEntity();
        }
    }

    private synchronized void setStatus(final IndexShardStatus status) {
        // Allow the thing to run without a service (e.g. benchmark mode)
        if (service != null) {
            boolean success = false;
            for (int i = 0; i < 10 && success == false; i++) {
                refreshEntity();
                indexShard.setStatus(status);
                success = save();
            }
        } else {
            refreshEntity();
        }
    }

    private synchronized void reload() {
        try {
            final IndexShard is = service.load(indexShard);
            if (is != null) {
                indexShard = is;
            }
        } catch (final EntityNotFoundException e) {
            LOGGER.debug("Index shard has been deleted %s", indexShard);
            indexShard.setStatus(IndexShardStatus.DELETED);
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    private synchronized boolean save() {
        boolean success = false;

        try {
            indexShard = service.save(indexShard);
            success = true;
        } catch (final EntityNotFoundException e) {
            LOGGER.debug("Index shard has been deleted %s", indexShard);
            indexShard.setStatus(IndexShardStatus.DELETED);
            success = true;
        } catch (final Throwable t) {
            LOGGER.debug(t.getMessage(), t);
            LOGGER.debug("Reloading index shard due to save error %s", indexShard);
            reload();
        }

        return success;
    }

    private synchronized void refreshEntity() {
        try {
            // Update the size of the index.
            if (dir != null && Files.isDirectory(dir)) {
                final AtomicLong totalSize = new AtomicLong();
                try {
                    Files.list(dir).forEach(file -> {
                        totalSize.addAndGet(file.toFile().length());
                    });
                } catch (final IOException e) {
                    LOGGER.error(e.getMessage());
                }
                indexShard.setFileSize(totalSize.get());
            }

            // Only update the document count details if we have read them.
            if (lastDocumentCount > 0) {
                indexShard.setDocumentCount(lastDocumentCount);
                indexShard.setCommitDocumentCount(lastCommitDocumentCount);
                indexShard.setCommitDurationMs(lastCommitDurationMs);
                indexShard.setCommitMs(lastCommitMs);
            }
        } catch (final Throwable t) {
            LOGGER.error(t.getMessage(), t);
        }
    }

    @Override
    public String toString() {
        final StringBuilder builder = new StringBuilder();
        builder.append("indexShard=");
        builder.append(indexShard);
        builder.append(", indexWriter=");
        builder.append(indexWriter == null ? "closed" : "open");
        builder.append(", actualDocs=");
        builder.append(documentCount);
        return builder.toString();
    }

    public synchronized void trace(final PrintStream ps) {
        if (loggerPrintStream != null) {
            refreshEntity();

            ps.println("Document Count = " + ModelStringUtil.formatCsv(documentCount.intValue()));
            if (dir != null) {
                ps.println("Index File(s) Size = " + ModelStringUtil.formatByteSizeString(indexShard.getFileSize()));
            }
            ps.println("RAM Buffer Size = " + ModelStringUtil.formatCsv(ramBufferSizeMB) + " MB");

            for (final Entry<String, String> term : LOG_WATCH_TERMS.entrySet()) {
                ps.println(term.getKey() + " = " + loggerPrintStream.getWatchTermCount(term.getValue()));
            }
        }
    }

    @Override
    public int getDocumentCount() {
        return documentCount.intValue();
    }

    @Override
    public Long getFileSize() {
        return indexShard.getFileSize();
    }

    @Override
    public IndexShard getIndexShard() {
        return indexShard;
    }

    @Override
    public int hashCode() {
        return indexShard.hashCode();
    }

    @Override
    public boolean equals(final Object obj) {
        if (obj == null || !(obj instanceof IndexShardWriter)) {
            return false;
        }

        final IndexShardWriterImpl writer = (IndexShardWriterImpl) obj;
        return indexShard.equals(writer.indexShard);
    }

    @Override
    public String getPartition() {
        return indexShard.getPartition();
    }

    @Override
    public boolean isFull() {
        return documentCount.intValue() >= maxDocumentCount;
    }

    @Override
    public boolean isClosed() {
        return IndexShardStatus.CLOSED.equals(indexShard.getStatus());
    }

    @Override
    public boolean isDeleted() {
        return IndexShardStatus.DELETED.equals(indexShard.getStatus());
    }

    @Override
    public boolean isCorrupt() {
        return IndexShardStatus.CORRUPT.equals(indexShard.getStatus());
    }

    @Override
    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public synchronized void destroy() {
        if (isOpen()) {
            try {
                close();
            } catch (final Exception ex) {
                LOGGER.error("destroy() - Error closing writer %s", this);
            }
        }
    }
}
