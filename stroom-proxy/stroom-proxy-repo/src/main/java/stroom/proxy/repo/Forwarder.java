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

package stroom.proxy.repo;

import stroom.data.zip.StroomFileNameUtil;
import stroom.data.zip.StroomZipEntry;
import stroom.data.zip.StroomZipFileType;
import stroom.db.util.JooqUtil;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.repo.db.jooq.tables.records.ForwardUrlRecord;
import stroom.receive.common.StreamHandlers;
import stroom.util.io.ByteCountInputStream;
import stroom.util.net.HostNameUtil;
import stroom.util.shared.ModelStringUtil;

import org.apache.commons.compress.archivers.zip.ZipArchiveEntry;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.jooq.Record3;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;
import java.util.concurrent.atomic.AtomicReference;
import java.util.stream.Stream;
import javax.inject.Inject;

import static stroom.proxy.repo.db.jooq.tables.Aggregate.AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.AggregateItem.AGGREGATE_ITEM;
import static stroom.proxy.repo.db.jooq.tables.ForwardAggregate.FORWARD_AGGREGATE;
import static stroom.proxy.repo.db.jooq.tables.ForwardUrl.FORWARD_URL;
import static stroom.proxy.repo.db.jooq.tables.Source.SOURCE;
import static stroom.proxy.repo.db.jooq.tables.SourceEntry.SOURCE_ENTRY;
import static stroom.proxy.repo.db.jooq.tables.SourceItem.SOURCE_ITEM;

public class Forwarder {

    private static final Logger LOGGER = LoggerFactory.getLogger(Forwarder.class);
    private static final String PROXY_FORWARD_ID = "ProxyForwardId";

    private final ProxyRepoDbConnProvider connProvider;
    private final ProxyRepoConfig proxyRepoConfig;
    private final AtomicLong proxyForwardId = new AtomicLong(0);
    private final ForwardStreamHandlers forwardStreamHandlers;

    private final Map<Integer, String> forwardIdUrlMap = new HashMap<>();
    private final List<ChangeListener> changeListeners = new CopyOnWriteArrayList<>();

    private volatile String hostName = null;

    @Inject
    Forwarder(final ProxyRepoDbConnProvider connProvider,
              final ProxyRepoConfig proxyRepoConfig,
              final ForwardStreamHandlers forwardStreamHandlers) {
        this.connProvider = connProvider;
        this.proxyRepoConfig = proxyRepoConfig;
        this.forwardStreamHandlers = forwardStreamHandlers;

        if (forwardStreamHandlers.getDestinationNames().size() > 0) {
            // Create a map of forward URLs to DB ids.
            JooqUtil.context(connProvider, context -> {
                for (final String destinationName : forwardStreamHandlers.getDestinationNames()) {
                    final Optional<Integer> optionalId = context
                            .select(FORWARD_URL.ID)
                            .from(FORWARD_URL)
                            .where(FORWARD_URL.URL.equal(destinationName))
                            .fetchOptional()
                            .map(r -> r.get(FORWARD_URL.ID));

                    final int id = optionalId.orElseGet(() -> context
                            .insertInto(FORWARD_URL, FORWARD_URL.URL)
                            .values(destinationName)
                            .returning(FORWARD_URL.ID)
                            .fetchOptional()
                            .map(ForwardUrlRecord::getId)
                            .orElse(-1));

                    forwardIdUrlMap.put(id, destinationName);
                }
            });
        }
    }

    public void forward() {
        // Start a transaction for all of the database changes.
        JooqUtil.context(connProvider, context -> {
            // Get all completed aggregates.
            try (final Stream<Record3<Integer, String, String>> stream = context
                    .select(AGGREGATE.ID, AGGREGATE.FEED_NAME, AGGREGATE.TYPE_NAME)
                    .from(AGGREGATE)
                    .where(AGGREGATE.COMPLETE.isTrue())
                    .and(AGGREGATE.FORWARD_ERROR.isFalse())
                    .orderBy(AGGREGATE.CREATE_TIME_MS)
                    .stream()) {

                stream.forEach(record -> {
                    final int aggregateId = record.get(AGGREGATE.ID);
                    final String feedName = record.get(AGGREGATE.FEED_NAME);
                    final String typeName = record.get(AGGREGATE.TYPE_NAME);

                    forwardAggregate(aggregateId, feedName, typeName);
                });
            }
        });
    }

    public void forwardAggregate(final int aggregateId,
                                 final String feedName,
                                 final String typeName) {
        final Map<Integer, String> remainingForwardUrl = new HashMap<>(forwardIdUrlMap);
        final AtomicBoolean previousFailure = new AtomicBoolean();

        // See if this data has been sent to all forward URLs.
        JooqUtil.context(connProvider, context -> context
                .select(FORWARD_AGGREGATE.FK_FORWARD_URL_ID, FORWARD_AGGREGATE.SUCCESS)
                .from(FORWARD_AGGREGATE)
                .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.eq(aggregateId))
                .fetch()
                .forEach(r -> {
                    remainingForwardUrl.remove(r.get(FORWARD_AGGREGATE.FK_FORWARD_URL_ID));
                    if (!r.get(FORWARD_AGGREGATE.SUCCESS)) {
                        previousFailure.set(true);
                    }
                }));

        // Forward to all remaining places.
        final List<CompletableFuture<Void>> futures = new ArrayList<>();
        final AtomicInteger successCount = new AtomicInteger();
        for (final Entry<Integer, String> entry : remainingForwardUrl.entrySet()) {
            final int forwardId = entry.getKey();
            final String forwardUrl = entry.getValue();
            final CompletableFuture<Void> completableFuture = CompletableFuture.runAsync(() -> {
                final boolean success = forwardAggregateData(aggregateId, feedName, typeName, forwardId, forwardUrl);
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
                    // Delete the aggregate if we have successfully forwarded to all destinations.
                    if (!previousFailure.get() &&
                            successCount.get() == remainingForwardUrl.size()) {
                        deleteAggregate(aggregateId);
                    } else {
                        // Mark the aggregate as having errors so we don't keep endlessly trying to send it.
                        JooqUtil.context(connProvider, context -> context
                                .update(AGGREGATE)
                                .set(AGGREGATE.FORWARD_ERROR, true)
                                .where(AGGREGATE.ID.eq(aggregateId))
                                .execute());
                    }
                });
    }


    private boolean forwardAggregateData(final int aggregateId,
                                         final String feedName,
                                         final String typeName,
                                         final int forwardUrlId,
                                         final String forwardUrl) {
        final AtomicBoolean success = new AtomicBoolean();
        final AtomicReference<String> error = new AtomicReference<>();

        final List<Record3<String, String, String>> entries = JooqUtil.contextResult(connProvider, context -> {
            // Get all of the source zip entries that we want to write to the forwarding location.
            return context
                    .select(SOURCE.PATH,
                            SOURCE_ITEM.NAME,
                            SOURCE_ENTRY.EXTENSION)
                    .from(SOURCE)
                    .join(SOURCE_ITEM).on(SOURCE_ITEM.FK_SOURCE_ID.eq(SOURCE.ID))
                    .join(SOURCE_ENTRY).on(SOURCE_ENTRY.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .join(AGGREGATE_ITEM).on(AGGREGATE_ITEM.FK_SOURCE_ITEM_ID.eq(SOURCE_ITEM.ID))
                    .join(AGGREGATE).on(AGGREGATE.ID.eq(AGGREGATE_ITEM.FK_AGGREGATE_ID))
                    .where(AGGREGATE.ID.eq(aggregateId))
                    .orderBy(SOURCE.PATH, SOURCE_ITEM.NAME, SOURCE_ENTRY.EXTENSION_TYPE, SOURCE_ENTRY.EXTENSION)
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

            final StreamHandlers streamHandlers = forwardStreamHandlers.getProvider(forwardUrl);

            // Start the POST
            try {
                streamHandlers.handle(attributeMap, handler -> {
                    String lastDataName = null;
                    String targetName = null;
                    long sequenceId = 1;

                    for (final Record3<String, String, String> record : entries) {
                        // Send no more if told to finish
                        if (Thread.currentThread().isInterrupted()) {
                            LOGGER.info("processFeedFiles() - Quitting early as we have been told to stop");
                            throw new RuntimeException(
                                    "processFeedFiles() - Quitting early as we have been told to stop");
                        }

                        final String sourcePath = record.get(SOURCE.PATH);
                        final String dataName = record.get(SOURCE_ITEM.NAME);
                        final String extension = record.get(SOURCE_ENTRY.EXTENSION);

                        final Path repoDir = Paths.get(proxyRepoConfig.getRepoDir());
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

                            try (final ByteCountInputStream inputStream =
                                    new ByteCountInputStream(zipFile.getInputStream(zipArchiveEntry))) {
                                if (LOGGER.isDebugEnabled()) {
                                    LOGGER.debug("sendEntry() - " + targetEntry);
                                }

                                handler.addEntry(targetName + extension, inputStream);
                                final long totalRead = inputStream.getCount();

                                if (LOGGER.isTraceEnabled()) {
                                    LOGGER.trace("sendEntry() - " +
                                            targetEntry +
                                            " " +
                                            ModelStringUtil.formatIECByteSizeString(
                                                    totalRead));
                                }
                                if (totalRead == 0) {
                                    LOGGER.warn("sendEntry() - " + targetEntry + " IS BLANK");
                                }
                                LOGGER.debug("sendEntry() - {} size is {}", targetEntry, totalRead);
                            }
                        } catch (final IOException e) {
                            throw new UncheckedIOException(e);
                        }
                    }

                    success.set(true);
                });
            } catch (final RuntimeException ex) {
                error.set(ex.getMessage());
                LOGGER.warn("processFeedFiles() - Failed to send to feed " + feedName + " ( " + ex + ")");
                if (LOGGER.isDebugEnabled()) {
                    LOGGER.debug("processFeedFiles() - Debug trace " + info, ex);
                }
            }
        } else {
            success.set(true);
        }

        // Record that we sent the data or if there was no data to send.
        JooqUtil.context(connProvider, context ->
                context
                        .insertInto(
                                FORWARD_AGGREGATE,
                                FORWARD_AGGREGATE.FK_FORWARD_URL_ID,
                                FORWARD_AGGREGATE.FK_AGGREGATE_ID,
                                FORWARD_AGGREGATE.SUCCESS,
                                FORWARD_AGGREGATE.ERROR)
                        .values(forwardUrlId, aggregateId, success.get(), error.get())
                        .execute());

        return success.get();
    }

    private void deleteAggregate(final int aggregateId) {
        JooqUtil.transaction(connProvider, context -> {
            context
                    .deleteFrom(FORWARD_AGGREGATE)
                    .where(FORWARD_AGGREGATE.FK_AGGREGATE_ID.equal(aggregateId))
                    .execute();

            context
                    .deleteFrom(AGGREGATE_ITEM)
                    .where(AGGREGATE_ITEM.FK_AGGREGATE_ID.equal(aggregateId))
                    .execute();

            context
                    .deleteFrom(AGGREGATE)
                    .where(AGGREGATE.ID.equal(aggregateId))
                    .execute();
        });

        // Once we have deleted an aggregate a cleanup operation might want to run.
        fireChange();
    }

    private String getHostName() {
        if (hostName == null) {
            hostName = HostNameUtil.determineHostName();
        }
        return hostName;
    }

    private void fireChange() {
        changeListeners.forEach(listener -> fireChange());
    }

    public void addChangeListener(final ChangeListener changeListener) {
        changeListeners.add(changeListener);
    }

    public interface ChangeListener {

        void onChange();
    }
}
