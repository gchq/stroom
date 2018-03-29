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

package stroom.index;

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.miscellaneous.PerFieldAnalyzerWrapper;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.LiveIndexWriterConfig;
import org.apache.lucene.store.AlreadyClosedException;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.NIOFSDirectory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.index.analyzer.AnalyzerFactory;
import stroom.index.shared.IndexField;
import stroom.index.shared.IndexField.AnalyzerType;
import stroom.index.shared.IndexShard;
import stroom.index.shared.IndexShardKey;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LoggerPrintStream;
import stroom.util.shared.ModelStringUtil;

import java.io.IOException;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

public class IndexShardWriterImpl implements IndexShardWriter {
    private static final LambdaLogger LAMBDA_LOGGER = LambdaLoggerFactory.getLogger(IndexShardWriterImpl.class);
    private static final Logger LOGGER = LoggerFactory.getLogger(IndexShardWriterImpl.class);

    private static final int DEFAULT_RAM_BUFFER_MB_SIZE = 1024;

    /**
     * Used to manage the way fields are analysed.
     */
    private final Map<String, Analyzer> fieldAnalyzers = new ConcurrentHashMap<>();

    private final IndexShardManager indexShardManager;
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

    private final IndexShardKey indexShardKey;
    private final long creationTime;
    private volatile int maxDocumentCount;

    private final AtomicBoolean open = new AtomicBoolean();
    private final AtomicInteger adding = new AtomicInteger();
    private volatile long lastUsedTime;

    /**
     * Convenience constructor used in tests.
     */
    IndexShardWriterImpl(final IndexShardManager indexShardManager, final IndexConfig indexConfig, final IndexShardKey indexShardKey, final IndexShard indexShard) throws IOException {
        this(indexShardManager, indexConfig, indexShardKey, indexShard, DEFAULT_RAM_BUFFER_MB_SIZE);
    }

    IndexShardWriterImpl(final IndexShardManager indexShardManager, final IndexConfig indexConfig, final IndexShardKey indexShardKey, final IndexShard indexShard, final int ramBufferSizeMB) throws IOException {
        this.indexShardManager = indexShardManager;
        this.indexShardKey = indexShardKey;
        this.indexShardId = indexShard.getId();
        this.creationTime = System.currentTimeMillis();
        this.lastUsedTime = creationTime;

        // Find the index shard dir.
        dir = IndexShardUtil.getIndexPath(indexShard);

        // Make sure the index writer is primed with the necessary analysers.
        LAMBDA_LOGGER.debug(() -> "Updating field analysers");

        // Update the settings for this shard from the index.
        updateIndexConfig(indexConfig);

        Directory directory;
        IndexWriter indexWriter;
        AtomicInteger documentCount;

        // Open the index writer.
        // If we already have a directory then this is an existing index.
        final String path = FileUtil.getCanonicalPath(dir);
        if (Files.isDirectory(dir)) {
            final long count = FileUtil.count(dir);
            if (count == 0) {
                throw new IndexException("Unable to find any index shard data in directory: " + path);
            }

        } else {
            // Make sure the index hasn't been deleted.
            if (indexShard.getDocumentCount() > 0) {
                throw new IndexException("Unable to find any index shard data in directory: " + path);
            }

            // Try and make all required directories.
            try {
                Files.createDirectories(dir);
            } catch (final IOException e) {
                LAMBDA_LOGGER.error(e::getMessage, e);
                throw new IndexException("Unable to create directories for new index in \"" + path + "\"");
            }
        }

        // Create the index writer config.
        // Setup the field analyzers.
        final Analyzer defaultAnalyzer = AnalyzerFactory.create(AnalyzerType.ALPHA_NUMERIC, false);
        final PerFieldAnalyzerWrapper analyzerWrapper = new PerFieldAnalyzerWrapper(defaultAnalyzer, fieldAnalyzers);
        final IndexWriterConfig indexWriterConfig = new IndexWriterConfig(analyzerWrapper);

        // In debug mode we do extra trace in LUCENE and we also count
        // certain logging info like merge and flush
        // counts, so you can get this later using the trace method.
        if (LOGGER.isDebugEnabled()) {
            final LoggerPrintStream loggerPrintStream = new LoggerPrintStream(LOGGER);
            for (final String term : LOG_WATCH_TERMS.values()) {
                loggerPrintStream.addWatchTerm(term);
            }
            indexWriterConfig.setInfoStream(loggerPrintStream);
        }

        // Create lucene directory object.
        directory = new NIOFSDirectory(dir, LockFactoryUtil.get(dir));

        // IndexWriter to use for adding data to the index.
        indexWriter = new IndexWriter(directory, indexWriterConfig);
        open.set(true);

        final LiveIndexWriterConfig liveIndexWriterConfig = indexWriter.getConfig();
        liveIndexWriterConfig.setRAMBufferSizeMB(ramBufferSizeMB);

        // TODO : We might still want to write separate segments I'm not sure on pros/cons?
        liveIndexWriterConfig.setUseCompoundFile(false);
        liveIndexWriterConfig.setMaxBufferedDocs(Integer.MAX_VALUE);

        // Check the number of committed docs in this shard.
        final int numDocs = indexWriter.numDocs();
        documentCount = new AtomicInteger(numDocs);

        if (indexShard.getDocumentCount() != numDocs) {
            LAMBDA_LOGGER.error(() -> "Mismatch document count. Index says " + numDocs + " DB says " + indexShard.getDocumentCount());
        }

        this.directory = directory;
        this.indexWriter = indexWriter;
        this.documentCount = documentCount;
    }

    @Override
    public void addDocument(final Document document) throws IOException, IndexException, AlreadyClosedException {
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

                final long now = System.currentTimeMillis();
                this.lastUsedTime = now;
                indexWriter.addDocument(document);
                final long duration = System.currentTimeMillis() - now;
                if (duration > 1000) {
                    LAMBDA_LOGGER.warn(() -> "addDocument() - took " + ModelStringUtil.formatDurationString(duration) + " " + toString());
                }

            } catch (final RuntimeException e) {
                documentCount.decrementAndGet();
                throw e;
            }

        } finally {
            adding.decrementAndGet();
        }
    }

    @Override
    public void updateIndexConfig(final IndexConfig indexConfig) {
        this.maxDocumentCount = indexConfig.getIndex().getMaxDocsPerShard();
        if (indexConfig.getIndexFields() != null) {
            for (final IndexField indexField : indexConfig.getIndexFields().getIndexFields()) {
                // Add the field analyser.
                final Analyzer analyzer = AnalyzerFactory.create(indexField.getAnalyzerType(),
                        indexField.isCaseSensitive());
                LAMBDA_LOGGER.debug(() -> "Adding field analyser for: " + indexField.getFieldName());
                fieldAnalyzers.put(indexField.getFieldName(), analyzer);
            }
        }
    }

    @Override
    public synchronized void flush() {
        if (open.get()) {
            // Record commit start time.
            final long startTime = System.currentTimeMillis();
            LAMBDA_LOGGER.debug(() -> "Starting flush " + toString());

            try {
                // Perform commit
                indexWriter.commit();

            } catch (final IOException | RuntimeException e) {
                LAMBDA_LOGGER.error(e::getMessage, e);

            } finally {
                // Update the shard info
                updateShardInfo(startTime);
            }

            LAMBDA_LOGGER.debug(() -> "Finished flush in " + ModelStringUtil.formatDurationString((System.currentTimeMillis() - startTime)) + ") " + toString());
        }
    }

    @Override
    public synchronized void close() {
        if (open.get()) {
            // Record close start time.
            final long startTime = System.currentTimeMillis();
            LAMBDA_LOGGER.debug(() -> "Starting close " + toString());

            try {
                // Perform close.
                // Wait for us to stop adding docs.
                try {
                    while (adding.get() > 0) {
                        LAMBDA_LOGGER.debug(() -> "Waiting for " + adding.get() + " docs to finish being added before we can close this shard");
                        Thread.sleep(1000);
                    }
                } catch (final InterruptedException e) {
                    // Interrupt this thread again.
                    Thread.currentThread().interrupt();
                }

                try {
                    indexWriter.close();
                } catch (final IOException | RuntimeException e) {
                    LAMBDA_LOGGER.error(e::getMessage, e);
                } finally {
                    try {
                        if (directory != null) {
                            directory.close();
                        }
                    } catch (final IOException | RuntimeException e) {
                        LAMBDA_LOGGER.error(e::getMessage, e);
                    }

                    open.set(false);
                }

            } catch (final RuntimeException e) {
                LAMBDA_LOGGER.error(e::getMessage, e);

            } finally {
                // Update the shard info
                updateShardInfo(startTime);
            }

            LAMBDA_LOGGER.debug(() -> "Finished close in " + ModelStringUtil.formatDurationString((System.currentTimeMillis() - startTime)) + ") " + toString());
        }
    }

    private synchronized void updateShardInfo(final long startTime) {
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
            LAMBDA_LOGGER.error(e::getMessage, e);
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
                            LOGGER.trace(e.getMessage(), e);
                        }
                    });
                } catch (final IOException e) {
                    LOGGER.trace(e.getMessage(), e);
                }
                fileSize = totalSize.get();
            }
        } catch (final RuntimeException e) {
            LAMBDA_LOGGER.debug(e::getMessage, e);
        }
        return fileSize;
    }

    @Override
    public IndexWriter getWriter() {
        return indexWriter;
    }

    @Override
    public int getDocumentCount() {
        return documentCount.get();
    }

    private void update(final long indexShardId, final Integer documentCount, final Long commitDurationMs, final Long commitMs, final Long fileSize) {
        if (indexShardManager != null) {
            indexShardManager.update(indexShardId, documentCount, commitDurationMs, commitMs, fileSize);
        }
    }

    @Override
    public IndexShardKey getIndexShardKey() {
        return indexShardKey;
    }

    @Override
    public long getIndexShardId() {
        return indexShardId;
    }

    public long getCreationTime() {
        return creationTime;
    }

    @Override
    public long getLastUsedTime() {
        return lastUsedTime;
    }

    @Override
    public String toString() {
        return "(id=" + indexShardId + ")";
    }
}
