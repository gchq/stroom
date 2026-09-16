/*
 * Copyright 2026 Crown Copyright
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

package stroom.proxy.app.pipeline.stage.splitzip;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.proxy.app.handler.CanonicalGroupWriter;
import stroom.proxy.app.handler.FileGroup;
import stroom.proxy.app.handler.ZipEntryGroup;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItemProcessor;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.FileStoreRegistry;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.repo.FeedKeyInterner;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import org.apache.commons.compress.archivers.zip.ZipFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * The split-zip stage: one multi-feed canonical group in, one canonical group per feed key out,
 * each published with its key so that every input downstream carries a feed and a feed's inputs
 * can find each other ({@code designs/stages/split-zip.md}).
 * <p>
 * Every output is written straight into a store write handle - there is no temporary directory -
 * and each is committed and published before the next is begun. Once every output is published
 * the input is deleted and the worker acknowledges the message. A throw before that leaves the
 * committed outputs as orphans the store clears, the current write to discard itself, and the
 * message to be redelivered: the re-split publishes the earlier outputs again, which is the
 * accepted direction.
 * </p>
 */
public final class SplitZipStage implements FileGroupQueueItemProcessor {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SplitZipStage.class);

    private final FileStoreRegistry stores;
    private final FileStore output;
    private final FileGroupQueue outputQueue;
    private final String producerId;

    public SplitZipStage(final FileStoreRegistry stores,
                         final FileStore output,
                         final FileGroupQueue outputQueue,
                         final String producerId) {
        this.stores = Objects.requireNonNull(stores, "stores");
        this.output = Objects.requireNonNull(output, "output");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.producerId = Objects.requireNonNull(producerId, "producerId");
    }

    @Override
    public void process(final FileGroupQueueItem item) throws Exception {
        final FileGroupQueueMessage message = Objects.requireNonNull(item.getMessage(), "item.message");
        final Path sourceDir = stores.resolve(message);
        final FileGroup source = new FileGroup(sourceDir);
        source.requireComplete("Split-zip input '" + message.messageId() + "'");

        final AttributeMap headers = new AttributeMap();
        AttributeMapUtil.read(source.getMeta(), headers);
        final Map<FeedKey, List<ZipEntryGroup>> itemsByKey = new LinkedHashMap<>();
        for (final ZipEntryGroup group : ZipEntryGroup.read(source.getEntries(), FeedKeyInterner.create())) {
            itemsByKey.computeIfAbsent(group.getFeedKey(), k -> new java.util.ArrayList<>()).add(group);
        }
        if (itemsByKey.isEmpty()) {
            throw new IOException(LogUtil.message(
                    "Split-zip input '{}' has no items in its proxy.entries", message.messageId()));
        }

        try (final ZipFile zip = ZipUtil.createZipFile(source.getZip())) {
            for (final Map.Entry<FeedKey, List<ZipEntryGroup>> entry : itemsByKey.entrySet()) {
                final FeedKey key = entry.getKey();
                final FileStoreLocation location;
                try (final FileStoreWrite write = output.newWrite()) {
                    try (final CanonicalGroupWriter writer = new CanonicalGroupWriter(write.getPath())) {
                        for (final ZipEntryGroup group : entry.getValue()) {
                            writer.add(zip, group, key);
                        }
                        final AttributeMap meta = new AttributeMap(headers);
                        AttributeMapUtil.addFeedAndType(meta, key.feed(), key.type());
                        writer.finish(meta);
                    }
                    location = write.commit();
                }
                final FileGroupQueueMessage outMessage = FileGroupQueueMessage.create(
                        location,
                        key.feed(),
                        key.type(),
                        PipelineStageName.SPLIT_ZIP.getConfigName(),
                        producerId,
                        message.traceId(),
                        Map.of());
                outputQueue.publish(outMessage);
                LOGGER.debug(() -> LogUtil.message(
                        "Split {} of input {} published as {} to {}",
                        key, message.messageId(), outMessage.messageId(), outputQueue.getName()));
            }
        }

        // Housekeeping after the durable step: every output is committed and published. A failure
        // here is logged and the message still acknowledged, since throwing would re-split the input
        // and publish every output again on each redelivery for as long as the delete kept failing;
        // an input no message names is reclaimed by the store's sweep, or the start-up sweep in
        // local mode (contracts.md §3.2).
        try {
            stores.requireFileStore(message.fileStoreLocation().storeName()).delete(message.fileStoreLocation());
        } catch (final IOException | RuntimeException e) {
            LOGGER.error(() -> LogUtil.message(
                    "Split {} (feed {}) but failed to delete its input at {}: {}. The message is acknowledged; "
                    + "the group is disk left behind, reclaimed by the store's sweep, rather than data at risk.",
                    message.messageId(), message.feed(), message.fileStoreLocation().uri(),
                    LogUtil.exceptionMessage(e)), e);
            return;
        }
        // The path the store lent: the same directory on a filesystem store, already gone; a
        // per-resolve download on an object store, which is this caller's to remove (C3).
        if (Files.exists(sourceDir) && !FileUtil.deleteDir(sourceDir)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to delete the resolved copy of split input {} at {}. It is disk left behind, cleared "
                    + "at the next start, rather than data at risk.", message.messageId(), sourceDir));
        }
        LOGGER.debug(() -> LogUtil.message(
                "Split input {} into {} group(s)", message.messageId(), itemsByKey.size()));
    }
}
