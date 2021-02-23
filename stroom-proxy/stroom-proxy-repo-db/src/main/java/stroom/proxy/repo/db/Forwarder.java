/*
 * Copyright 2019 Crown Copyright
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

package stroom.proxy.repo.db;

import stroom.data.zip.StreamProgressMonitor;
import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFile;
import stroom.data.zip.StroomZipFileType;
import stroom.db.util.JooqUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.ErrorFileUtil;
import stroom.proxy.repo.ForwardDestinationConfig;
import stroom.proxy.repo.ForwardStreamConfig;
import stroom.proxy.repo.ProxyRepositoryConfig;
import stroom.proxy.repo.ProxyRepositoryReaderConfig;
import stroom.proxy.repo.StreamHandler;
import stroom.receive.common.StroomStreamHandler;
import stroom.util.io.BufferFactory;
import stroom.util.io.CloseableUtil;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.ModelStringUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jooq.Condition;
import org.jooq.Record3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.ForwardZipDest.FORWARD_ZIP_DEST;
import static stroom.proxy.repo.db.jooq.tables.ZipData.ZIP_DATA;
import static stroom.proxy.repo.db.jooq.tables.ZipDest.ZIP_DEST;
import static stroom.proxy.repo.db.jooq.tables.ZipDestData.ZIP_DEST_DATA;
import static stroom.proxy.repo.db.jooq.tables.ZipEntry.ZIP_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.ZipSource.ZIP_SOURCE;

class Forwarder {

    private static final Logger LOGGER = LoggerFactory.getLogger(Forwarder.class);
    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final ProxyRepoDbConnProvider connProvider;
    private final ZipInfoStoreDao zipInfoStoreDao;
    private final ProxyRepositoryConfig proxyRepositoryConfig;
    private final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ScheduledExecutorService executorService = Executors.newSingleThreadScheduledExecutor();
    private final ForwardStreamHandlerFactory forwardStreamHandlerFactory;
    private final BufferFactory bufferFactory;

//    private ZipDataCollection currentCollection;

    private final Map<String, Integer> forwardUrlIdMap = new HashMap<>();
    private final Map<Integer, String> forwardIdUrlMap = new HashMap<>();

    private volatile String hostName = null;

    @Inject
    Forwarder(final ZipInfoStoreDao zipInfoStoreDao,
              final ProxyRepoDbConnProvider connProvider,
              final ProxyRepositoryConfig proxyRepositoryConfig,
              final ProxyRepositoryReaderConfig proxyRepositoryReaderConfig,
              final ForwardStreamConfig forwardStreamConfig,
              final ForwardStreamHandlerFactory forwardStreamHandlerFactory,
              final BufferFactory bufferFactory) {
        this.zipInfoStoreDao = zipInfoStoreDao;
        this.connProvider = connProvider;
        this.proxyRepositoryConfig = proxyRepositoryConfig;
        this.proxyRepositoryReaderConfig = proxyRepositoryReaderConfig;
        this.forwardStreamHandlerFactory = forwardStreamHandlerFactory;
        this.bufferFactory = bufferFactory;

        if (forwardStreamConfig.isForwardingEnabled() && forwardStreamConfig.getForwardDestinations().size() > 0) {
            // Create a map of forward URLs to DB ids.
            JooqUtil.context(connProvider, context -> {
                for (final ForwardDestinationConfig config : forwardStreamConfig.getForwardDestinations()) {
                    final Optional<Integer> optionalId = context
                            .select(FORWARD_URL.ID)
                            .from(FORWARD_URL)
                            .where(FORWARD_URL.URL.equal(config.getForwardUrl()))
                            .fetchOptional()
                            .map(r -> r.get(FORWARD_URL.ID));

                    final int id = optionalId.orElseGet(() -> context
                            .insertInto(FORWARD_URL, FORWARD_URL.URL)
                            .values(config.getForwardUrl())
                            .returning(FORWARD_URL.ID)
                            .fetchOne()
                            .getId());

                    forwardUrlIdMap.put(config.getForwardUrl(), id);
                    forwardIdUrlMap.put(id, config.getForwardUrl());
                }
            });

            // Start forwarding.
            forward();
        }
    }

    public void forward() {
        final long start = System.currentTimeMillis();

//        // Start by trying to close old destinations.
//        closeOldDestinations(start);

        // Start a transaction for all of the database changes.
        JooqUtil.context(connProvider, context -> {
            // Get all data items that have not been added to aggregate destinations.
            try (final Stream<Record3<Integer, String, String>> stream = context
                    .select(ZIP_DEST.ID, ZIP_DEST.FEED_NAME, ZIP_DEST.TYPE_NAME)
                    .from(ZIP_DEST)
                    .where(ZIP_DEST.COMPLETE.isTrue())
                    .stream()) {

                stream.forEach(record -> {
                    final int zipDestId = record.get(ZIP_DEST.ID);
                    final String feedName = record.get(ZIP_DEST.FEED_NAME);
                    final String typeName = record.get(ZIP_DEST.TYPE_NAME);

                    // See if this data has been sent to all forward URLs.
                    final Set<Integer> alreadyForwarded = context
                            .select(FORWARD_ZIP_DEST.ID)
                            .from(FORWARD_ZIP_DEST)
                            .where(FORWARD_ZIP_DEST.FK_ZIP_DEST_ID.eq(zipDestId))
                            .fetch()
                            .stream()
                            .map(r -> r.get(FORWARD_ZIP_DEST.ID))
                            .collect(Collectors.toSet());

                    // So where haven't we forwarded this data to?
                    final Set<Integer> remainingForwardIdSet = new HashSet<>(forwardIdUrlMap.keySet());
                    remainingForwardIdSet.removeAll(alreadyForwarded);

                    // Forward to all remaining places.
                    final List<CompletableFuture<Void>> futures = new ArrayList<>();
                    final AtomicInteger successCount = new AtomicInteger();
                    for (final int forwardId : remainingForwardIdSet) {
                        final String forwardUrl = forwardIdUrlMap.get(forwardId);
                        final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                            final boolean success = forwardData(zipDestId, feedName, typeName, forwardId, forwardUrl);
                            if (success) {
                                successCount.incrementAndGet();
                            }
                        });
                        futures.add(completableFuture);
                    }

                    // When all futures complete we want to try and delete the aggregate.
                    CompletableFuture
                            .allOf(futures.toArray(new CompletableFuture[0]))
                            .thenRunAsync(() -> {
                                // Delete the zip dest if we have successfully forwarded to all destinations.
                                if (successCount.get() == remainingForwardIdSet.size()) {
                                    deleteZipDest(zipDestId);
                                }
                            });

                });


            }
        });

//        // Add final entry.
//        if (currentCollection != null) {
//            addCollection(currentCollection);
//            currentCollection = null;
//        }

        // Run the aggregation again on the next set of data.
        final long frequency = proxyRepositoryReaderConfig.getAggregationFrequency().toMillis();
        // How long were we running for?
        final long duration = System.currentTimeMillis() - start;
        final long delay = Math.max(0, frequency - duration);
        executorService.schedule(this::forward, delay, TimeUnit.MILLISECONDS);
    }

    private boolean forwardData(final int zipDestId,
                             final String feedName,
                             final String typeName,
                             final int forwardUrlId,
                             final String forwardUrl) {
        boolean success = false;

        final List<Record3<String, String, String>> entries = JooqUtil.contextResult(connProvider, context -> {
            // Get all of the source zip entries that we want to write to the forwarding location.
            return context
                    .select(ZIP_SOURCE.PATH,
                            ZIP_DATA.NAME,
                            ZIP_ENTRY.EXTENSION)
                    .from(ZIP_SOURCE)
                    .join(ZIP_DATA).on(ZIP_DATA.FK_ZIP_SOURCE_ID.eq(ZIP_SOURCE.ID))
                    .join(ZIP_ENTRY).on(ZIP_ENTRY.FK_ZIP_DATA_ID.eq(ZIP_DATA.ID))
                    .join(ZIP_DEST_DATA).on(ZIP_DEST_DATA.FK_ZIP_DATA_ID.eq(ZIP_DATA.ID))
                    .join(ZIP_DEST).on(ZIP_DEST.ID.eq(ZIP_DEST_DATA.FK_ZIP_DEST_ID))
                    .where(ZIP_DEST.ID.eq(zipDestId))
                    .orderBy(ZIP_SOURCE.PATH, ZIP_DATA.NAME, ZIP_ENTRY.EXTENSION_TYPE, ZIP_ENTRY.EXTENSION)
                    .fetch();
        });

        if (entries.size() > 0) {

            final long thisPostId = proxyForwardId.incrementAndGet();
            final String info = thisPostId + " " + feedName + " - " + typeName;
            if (LOGGER.isDebugEnabled()) {
                LOGGER.debug("processFeedFiles() - proxyForwardId " + info);
            }

            final AttributeMap attributeMap = new AttributeMap();
            attributeMap.put(StandardHeaderArguments.FEED, feedName);
            if (typeName != null && !typeName.isBlank()) {
                attributeMap.put(StandardHeaderArguments.TYPE, typeName.trim());
            }
            attributeMap.put(StandardHeaderArguments.COMPRESSION, StandardHeaderArguments.COMPRESSION_ZIP);
            attributeMap.put(StandardHeaderArguments.RECEIVED_PATH, getHostName());
            if (LOGGER.isDebugEnabled()) {
                attributeMap.put(PROXY_FORWARD_ID, String.valueOf(thisPostId));
            }

            final StreamHandler handler = forwardStreamHandlerFactory.get(forwardUrl);

            try {
                // Start the POST
                handler.setAttributeMap(attributeMap);
                handler.handleHeader();

                String lastDataName = null;
                String targetName = null;
                long sequenceId = 1;
                final StreamProgressMonitor streamProgress = new StreamProgressMonitor("ProxyRepositoryReader " + info);

                for (final Record3<String, String, String> record : entries) {
                    // Send no more if told to finish
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.info("processFeedFiles() - Quitting early as we have been told to stop");
                        throw new RuntimeException("processFeedFiles() - Quitting early as we have been told to stop");
                    }

                    final String sourcePath = record.get(ZIP_SOURCE.PATH);
                    final String dataName = record.get(ZIP_DATA.NAME);
                    final String extension = record.get(ZIP_ENTRY.EXTENSION);

                    final Path repoDir = Paths.get(proxyRepositoryConfig.getDir());
                    final Path zipFilePath = repoDir.resolve(sourcePath);

                    try (final ZipFile zipFile = new ZipFile(Files.newByteChannel(zipFilePath))) {
                        final ZipArchiveEntry zipArchiveEntry = zipFile.getEntry(dataName + extension);

                        // If the data name has changed then change the target name.
                        if (lastDataName == null || !lastDataName.equals(dataName)) {
                            lastDataName = dataName;
                            targetName = StroomFileNameUtil.getIdPath(sequenceId++);
                        }

                        final StroomZipEntry targetEntry = new StroomZipEntry(
                                targetName + extension,
                                targetName,
                                StroomZipFileType.valueOf(extension));

                        try (final InputStream inputStream = zipFile.getInputStream(zipArchiveEntry)) {
                            if (LOGGER.isDebugEnabled()) {
                                LOGGER.debug("sendEntry() - " + targetEntry);
                            }

                            final byte[] buffer = bufferFactory.create();
                            int read;
                            long totalRead = 0;

                            handler.handleEntryStart(targetEntry);
                            while ((read = inputStream.read(buffer)) != -1) {
                                totalRead += read;
                                streamProgress.progress(read);
                                handler.handleEntryData(buffer, 0, read);
                            }
                            handler.handleEntryEnd();

                            if (LOGGER.isTraceEnabled()) {
                                LOGGER.trace("sendEntry() - " + targetEntry + " " + ModelStringUtil.formatIECByteSizeString(
                                        totalRead));
                            }
                            if (totalRead == 0) {
                                LOGGER.warn("sendEntry() - " + targetEntry + " IS BLANK");
                            }
                            LOGGER.debug("sendEntry() - {} size is {}", targetEntry, totalRead);

                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }

                    } catch (final IOException e) {
                        throw new UncheckedIOException(e);
                    }
                }

                handler.handleFooter();

                success = true;

            } catch (final IOException ex) {
                LOGGER.warn("processFeedFiles() - Failed to send to feed " + feedName + " ( " + ex + ")");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("processFeedFiles() - Debug trace " + info, ex);
                }
                try {
                    handler.handleError();
                } catch (final IOException ioEx) {
                    LOGGER.error("fileSend()", ioEx);
                }
            }
        } else {
            success = true;
        }

        if (success) {
            success = false;

            // Record that we sent the data or if there was no data to send.
            JooqUtil.context(connProvider, context ->
                    context
                            .insertInto(
                                    FORWARD_ZIP_DEST,
                                    FORWARD_ZIP_DEST.FK_FORWARD_URL_ID,
                                    FORWARD_ZIP_DEST.FK_ZIP_DEST_ID)
                            .values(forwardUrlId, zipDestId)
                            .execute());

            success = true;
        }

        return success;
    }

    private void deleteZipDest(final int zipDestId) {
        JooqUtil.transaction(connProvider, context -> {
            context
                    .deleteFrom(ZIP_DEST_DATA)
                    .where(ZIP_DEST_DATA.FK_ZIP_DEST_ID.equal(zipDestId))
                    .execute();

            context
                    .deleteFrom(ZIP_DEST)
                    .where(ZIP_DEST.ID.equal(zipDestId))
                    .execute();

            context
                    .deleteFrom(FORWARD_ZIP_DEST)
                    .where(FORWARD_ZIP_DEST.FK_ZIP_DEST_ID.equal(zipDestId))
                    .execute();
        });
    }


//    public Long processFeedFile(final List<? extends StroomStreamHandler> stroomStreamHandlerList,
//                                final Path file,
//                                final StreamProgressMonitor streamProgress,
//                                final long startSequence) throws IOException {
//        long entrySequence = startSequence;
//        StroomZipFile stroomZipFile = null;
//        boolean bad = true;
//
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("processFeedFile() - " + file);
//        }
//
//        IOException exception = null;
//        try {
//            stroomZipFile = new StroomZipFile(file);
//
//            for (final String sourceName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
//                bad = false;
//
//                final String targetName = StroomFileNameUtil.getIdPath(entrySequence++);
//
//                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
//                        new StroomZipEntry(null, targetName, StroomZipFileType.Meta));
//                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
//                        new StroomZipEntry(null, targetName, StroomZipFileType.Context));
//                sendEntry(stroomStreamHandlerList, stroomZipFile, sourceName, streamProgress,
//                        new StroomZipEntry(null, targetName, StroomZipFileType.Data));
//            }
//        } catch (final IOException io) {
//            exception = io;
//        } finally {
//            CloseableUtil.close(stroomZipFile);
//        }
//
//        if (exception != null) {
//            ErrorFileUtil.addErrorMessage(file, exception.getMessage(), bad);
//            throw exception;
//        }
//
//        return entrySequence;
//    }
//
//    private long getRawContentSize(final Path file) throws IOException {
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("Getting raw content size for  '" + FileUtil.getCanonicalPath(file) + "'");
//        }
//
//        long totalSize = 0;
//
//        try (final StroomZipFile stroomZipFile = new StroomZipFile(file)) {
//            for (final String sourceName : stroomZipFile.getStroomZipNameSet().getBaseNameSet()) {
//                totalSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Meta);
//                totalSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Context);
//                totalSize += getRawEntrySize(stroomZipFile, sourceName, StroomZipFileType.Data);
//            }
//        }
//
//        if (LOGGER.isDebugEnabled()) {
//            LOGGER.debug("Raw content size for  '" + FileUtil.getCanonicalPath(file) + "' is " +
//            ModelStringUtil.formatIECByteSizeString(totalSize));
//        }
//
//        return totalSize;
//    }
//
//    private long getRawEntrySize(final StroomZipFile stroomZipFile,
//                                 final String sourceName,
//                                 final StroomZipFileType fileType)
//            throws IOException {
//        final long size = stroomZipFile.getSize(sourceName, fileType);
//        if (size == -1) {
//            throw new IOException("Unknown raw file size");
//        }
//
//        return size;
//    }

//    private void sendEntry(final List<? extends StroomStreamHandler> requestHandlerList,
//                           final StroomZipFile stroomZipFile,
//                           final String sourceName,
//                           final StreamProgressMonitor streamProgress,
//                           final StroomZipEntry targetEntry)
//            throws IOException {
//        final InputStream inputStream = stroomZipFile.getInputStream(sourceName, targetEntry.getStroomZipFileType());
//        sendEntry(requestHandlerList, inputStream, streamProgress, targetEntry);
//    }
//
//    private void sendEntry(final List<? extends StroomStreamHandler> stroomStreamHandlerList,
//                           final InputStream inputStream,
//                           final StreamProgressMonitor streamProgress,
//                           final StroomZipEntry targetEntry)
//            throws IOException {
//        if (inputStream != null) {
//            if (LOGGER.isDebugEnabled()) {
//                LOGGER.debug("sendEntry() - " + targetEntry);
//            }
//            final byte[] buffer = bufferFactory.create();
//            for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
//                stroomStreamHandler.handleEntryStart(targetEntry);
//            }
//            int read;
//            long totalRead = 0;
//            while ((read = inputStream.read(buffer)) != -1) {
//                totalRead += read;
//                streamProgress.progress(read);
//                for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
//                    stroomStreamHandler.handleEntryData(buffer, 0, read);
//                }
//            }
//            for (final StroomStreamHandler stroomStreamHandler : stroomStreamHandlerList) {
//                stroomStreamHandler.handleEntryEnd();
//            }
//
//            if (LOGGER.isTraceEnabled()) {
//                LOGGER.trace("sendEntry() - " + targetEntry + " " + ModelStringUtil.formatIECByteSizeString(totalRead));
//            }
//            if (totalRead == 0) {
//                LOGGER.warn("sendEntry() - " + targetEntry + " IS BLANK");
//            }
//            LOGGER.debug("sendEntry() - {} size is {}", targetEntry, totalRead);
//
//        }
//    }


//    private synchronized void addCollection(final ZipDataCollection collection) {
//        final long maxUncompressedByteSize = proxyRepositoryReaderConfig.getMaxUncompressedByteSize();
//        final int maxItemsPerAggregate = proxyRepositoryReaderConfig.getMaxItemsPerAggregate();
//        final long maxDestSize = Math.max(0, maxUncompressedByteSize - collection.totalSize);
//
//        JooqUtil.transaction(connProvider, context -> {
//            // See if we can get an existing destination that will fit this data collection.
//            final Optional<ZipDestRecord> optionalRecord = context
//                    .selectFrom(ZIP_DEST)
//                    .where(ZIP_DEST.FEEDNAME.equal(collection.feedName))
//                    .and(ZIP_DEST.BYTE_SIZE.lessOrEqual(maxDestSize))
//                    .and(ZIP_DEST.ITEMS.lessThan(maxItemsPerAggregate))
//                    .and(ZIP_DEST.COMPLETE.isFalse())
//                    .orderBy(ZIP_DEST.CREATE_TIME_MS)
//                    .fetchOptional();
//
//            int destId;
//            if (optionalRecord.isPresent()) {
//                destId = optionalRecord.get().getId();
//
//                // We have somewhere we can add the data collection so add it to the dest.
//                context
//                        .update(ZIP_DEST)
//                        .set(ZIP_DEST.BYTE_SIZE, ZIP_DEST.BYTE_SIZE.plus(collection.totalSize))
//                        .set(ZIP_DEST.ITEMS, ZIP_DEST.ITEMS.plus(1))
//                        .where(ZIP_DEST.ID.eq(destId))
//                        .execute();
//
//            } else {
//                // Create a new dest to add this data collection to.
//                destId = context
//                        .insertInto(
//                                ZIP_DEST,
//                                ZIP_DEST.FEEDNAME,
//                                ZIP_DEST.BYTE_SIZE,
//                                ZIP_DEST.ITEMS,
//                                ZIP_DEST.CREATE_TIME_MS)
//                        .values(collection.feedName,
//                                collection.totalSize,
//                                1,
//                                System.currentTimeMillis())
//                        .returning(ZIP_DEST.ID)
//                        .fetchOne()
//                        .getId();
//            }
//
//            // Add the data collection.
//            context
//                    .insertInto(ZIP_DEST_DATA, ZIP_DEST_DATA.FK_ZIP_DEST_ID, ZIP_DEST_DATA.FK_ZIP_DATA_ID)
//                    .values(destId, collection.dataId)
//                    .execute();
//
//            // Mark data collection as added.
//            context
//                    .update(ZIP_DATA)
//                    .set(ZIP_DATA.HAS_DEST, true)
//                    .where(ZIP_DATA.ID.eq(collection.dataId))
//                    .execute();
//        });
//    }
//
//    private void closeOldDestinations(final long now) {
//        final long oldest = now - proxyRepositoryReaderConfig.getMaxAggregateAge().toMillis();
//
//        final Condition condition = DSL.and(
//                DSL.or(
//                        ZIP_DEST.ITEMS.greaterOrEqual(proxyRepositoryReaderConfig.getMaxItemsPerAggregate()),
//                        ZIP_DEST.BYTE_SIZE.greaterOrEqual(proxyRepositoryReaderConfig.getMaxUncompressedByteSize()),
//                        ZIP_DEST.CREATE_TIME_MS.lessOrEqual(oldest)
//                ),
//                ZIP_DEST.COMPLETE.eq(false)
//        );
//
//        closeDestinations(condition);
//    }

//    private synchronized int closeDestinations(final Condition condition) {
//        return JooqUtil.contextResult(connProvider, context -> context
//                .update(ZIP_DEST)
//                .set(ZIP_DEST.COMPLETE, true)
//                .where(condition)
//                .execute());
//    }


//    private record ZipDataEntry(long entryId, String extension, long size) {
//
//    }
//
//
//    private record ZipDataCollection(int dataId,
//                                     String name,
//                                     String feedName,
//                                     List<ZipDataEntry> entries,
//                                     long totalSize) {
//
//        public ZipDataCollection add(ZipDataEntry zipDataEntry) {
//            return new ZipDataCollection(
//                    dataId,
//                    name,
//                    feedName,
//                    Stream
//                            .concat(entries.stream(), Stream.of(zipDataEntry))
//                            .collect(Collectors.toUnmodifiableList()),
//                    totalSize + zipDataEntry.size);
//        }
//    }


//    private static class ZipDataEntry {
//
//        private final long entryId;
//        private final String extension;
//        private final long size;
//
//        public ZipDataEntry(final long entryId, final String extension, final long size) {
//            this.entryId = entryId;
//            this.extension = extension;
//            this.size = size;
//        }
//    }
//
//    private static class ZipDataCollection {
//
//        private final int dataId;
//        private final String name;
//        private final String feedName;
//        private final List<ZipDataEntry> entries;
//        private long totalSize;
//
//        public ZipDataCollection(final int dataId,
//                                 final String name,
//                                 final String feedName) {
//            this.dataId = dataId;
//            this.name = name;
//            this.feedName = feedName;
//            this.entries = new ArrayList<>();
//        }
//
//        public void addEntry(final ZipDataEntry entry) {
//            entries.add(entry);
//            totalSize += entry.size;
//
//        }
//
//
//    }

    private String getHostName() {
        if (hostName == null) {
            hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }
}