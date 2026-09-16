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

package stroom.proxy.app.handler;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.AttributeMapUtil;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.handler.ZipEntryGroup.Entry;
import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;
import stroom.proxy.app.pipeline.store.FileStore;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import stroom.proxy.app.pipeline.store.FileStoreWrite;
import stroom.proxy.repo.LogStream;
import stroom.receive.common.AttributeMapFilter;
import stroom.receive.common.AttributeMapFilterFactory;
import stroom.receive.common.InputStreamUtils;
import stroom.receive.common.ReceiveDataConfig;
import stroom.receive.common.StroomStreamException;
import stroom.util.io.ByteCountInputStream;
import stroom.util.io.ByteSize;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.FeedKey;
import stroom.util.zip.ZipUtil;

import jakarta.inject.Provider;
import org.apache.commons.compress.archivers.zip.ZipFile;
import org.apache.commons.compress.compressors.gzip.GzipCompressorInputStream;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.Writer;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

/**
 * The pipeline's receiver: what arrives is written straight into a receive-store write, committed,
 * and published to the receive stage's output queue, and the caller is answered only then
 * ({@code designs/stages/receive.md}).
 * <p>
 * Every group it commits is a canonical proxy zip with {@code proxy.entries} describing the whole of
 * it and {@code proxy.meta} beside it. A group holding one feed carries that feed on its message and
 * goes to the output queue; a zip holding several goes, as one group with no feed on its message, to
 * the split-zip queue.
 * </p>
 * <p>
 * The receipt policy runs once per distinct feed before anything is written: a rejection costs
 * nothing but the read, and a dropped feed's entries are never written rather than written and
 * removed. There is no receiving directory of this class's own and nothing is copied: the store's
 * write handle is the only place the group ever is, and closing it uncommitted discards it. The one
 * piece of scratch is the spool a zip body is written to so that it can be read through its central
 * directory; it is deleted after every receipt and the directory is cleared at start-up.
 * </p>
 */
public class StoringReceiver implements Receiver {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StoringReceiver.class);
    private static final String SINGLE_BASE_NAME = NumericFileNameUtil.create(1);
    private static final String SINGLE_META_ENTRY = SINGLE_BASE_NAME + ".meta";
    private static final String SINGLE_DATA_ENTRY = SINGLE_BASE_NAME + ".dat";
    private static final int[] GZIP_MAGIC = {0x1f, 0x8b};

    private final FileStore store;
    private final FileGroupQueue outputQueue;
    private final FileGroupQueue splitZipQueue;
    private final String sourceNodeId;
    private final AttributeMapFilterFactory attributeMapFilterFactory;
    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final LogStream logStream;
    private final Path spoolDir;

    /**
     * @param splitZipQueue Where a zip holding more than one feed goes, or null if this deployment
     *                      has nowhere to split one, in which case such a zip is refused.
     * @param spoolDir      Node-local scratch for zip bodies. Cleared here, at construction.
     */
    public StoringReceiver(final FileStore store,
                           final FileGroupQueue outputQueue,
                           final FileGroupQueue splitZipQueue,
                           final String sourceNodeId,
                           final AttributeMapFilterFactory attributeMapFilterFactory,
                           final Provider<ReceiveDataConfig> receiveDataConfigProvider,
                           final LogStream logStream,
                           final Path spoolDir) {
        this.store = Objects.requireNonNull(store, "store");
        this.outputQueue = Objects.requireNonNull(outputQueue, "outputQueue");
        this.splitZipQueue = splitZipQueue;
        this.sourceNodeId = Objects.requireNonNull(sourceNodeId, "sourceNodeId");
        this.attributeMapFilterFactory = Objects.requireNonNull(attributeMapFilterFactory, "attributeMapFilterFactory");
        this.receiveDataConfigProvider = Objects.requireNonNull(receiveDataConfigProvider, "receiveDataConfigProvider");
        this.logStream = Objects.requireNonNull(logStream, "logStream");
        this.spoolDir = Objects.requireNonNull(spoolDir, "spoolDir");

        DirUtil.ensureDirExists(spoolDir);
        // Whatever is here is a spool a previous process did not finish with; nothing refers to it.
        if (!FileUtil.deleteContents(spoolDir)) {
            LOGGER.error(() -> "Failed to clear the receive spool directory " + FileUtil.getCanonicalPath(spoolDir));
        }
    }

    @Override
    public void receive(final Instant startTime,
                        final AttributeMap attributeMap,
                        final String source,
                        final InputStreamSupplier body) {
        try {
            if (StandardHeaderArguments.COMPRESSION_ZIP.equalsIgnoreCase(
                    attributeMap.get(StandardHeaderArguments.COMPRESSION))) {
                receiveZipBody(startTime, attributeMap, source, body);
            } else {
                receivePlainBody(startTime, attributeMap, source, body);
            }
        } catch (final IOException | RuntimeException e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }

    @Override
    public void receiveZip(final Instant startTime,
                           final AttributeMap attributeMap,
                           final String source,
                           final Path zipFile) {
        try {
            if (!Files.isRegularFile(zipFile)) {
                throw new IOException(LogUtil.message("'{}' is not a file", zipFile));
            }
            receiveZipFile(startTime, attributeMap, source, zipFile, Files.size(zipFile));
        } catch (final IOException | RuntimeException e) {
            throw StroomStreamException.create(e, attributeMap);
        }
    }

    /**
     * A plain or gzip body: one data entry, its meta from the headers.
     */
    private void receivePlainBody(final Instant startTime,
                                  final AttributeMap attributeMap,
                                  final String source,
                                  final InputStreamSupplier body) throws IOException {
        // The policy may reject a missing feed before anything here reads it.
        if (!attributeMapFilterFactory.create().filter(attributeMap)) {
            ReceiveLog.dropped(logStream, attributeMap, source, ReceiveLog.drain(body), startTime);
            return;
        }
        final FeedKey feedKey = FeedKey.of(
                attributeMap.get(StandardHeaderArguments.FEED),
                attributeMap.get(StandardHeaderArguments.TYPE));

        final boolean gzip = StandardHeaderArguments.COMPRESSION_GZIP.equalsIgnoreCase(
                attributeMap.get(StandardHeaderArguments.COMPRESSION));
        try (final BufferedInputStream in = new BufferedInputStream(body.get())) {
            in.mark(GZIP_MAGIC.length);
            final int first = in.read();
            if (first == -1) {
                LOGGER.warn(() -> "Received an empty body, nothing to store: " + attributeMap);
                ReceiveLog.received(logStream, attributeMap, source, 0, startTime);
                return;
            }
            final int second = in.read();
            in.reset();
            if (gzip && (first != GZIP_MAGIC[0] || second != GZIP_MAGIC[1])) {
                // Checked here so the sender is told its fault as such: the decompressor's own
                // complaint is an IOException indistinguishable from a failure of ours.
                throw new StroomStreamException(StroomStatusCode.COMPRESSED_STREAM_INVALID, attributeMap,
                        "Compression is GZIP but the body is not a gzip stream");
            }

            final AttributeMap entryMeta = AttributeMapUtil.cloneAllowable(attributeMap);
            final byte[] metaBytes = AttributeMapUtil.toByteArray(entryMeta);

            try (final FileStoreWrite write = store.newWrite()) {
                final FileGroup group = new FileGroup(write.getPath());
                final long bytes;
                try (final ProxyZipWriter zipWriter = new ProxyZipWriter(group.getZip(), LocalByteBuffer.get())) {
                    zipWriter.writeStream(SINGLE_META_ENTRY, new ByteArrayInputStream(metaBytes));
                    final InputStream data = gzip
                            ? new GzipCompressorInputStream(in, true)
                            : in;
                    try (final InputStream bounded = InputStreamUtils.getBoundedInputStream(data, maxRequestSize())) {
                        bytes = zipWriter.writeStream(SINGLE_DATA_ENTRY, bounded);
                    }
                }
                final ZipEntryGroup entryGroup = new ZipEntryGroup(
                        feedKey.feed(),
                        feedKey.type(),
                        null,
                        new Entry(SINGLE_META_ENTRY, metaBytes.length),
                        null,
                        new Entry(SINGLE_DATA_ENTRY, bytes));
                writeEntries(group, List.of(entryGroup));
                AttributeMapUtil.write(entryMeta, group.getMeta());

                publish(outputQueue, write.commit(), feedKey);
                ReceiveLog.received(logStream, attributeMap, source, bytes, startTime);
            }
        }
    }

    /**
     * A zip body is spooled to disk first: a zip is read through its central directory, which is at
     * the end, and the stream form cannot be trusted to say which entries are real.
     */
    private void receiveZipBody(final Instant startTime,
                                final AttributeMap attributeMap,
                                final String source,
                                final InputStreamSupplier body) throws IOException {
        Files.createDirectories(spoolDir);
        final Path spool = Files.createTempFile(spoolDir, "upload-", ".zip");
        try {
            final long received;
            try (final InputStream bounded = InputStreamUtils.getBoundedInputStream(body.get(), maxRequestSize());
                    final ByteCountInputStream counted = ByteCountInputStream.wrap(bounded)) {
                Files.copy(counted, spool, StandardCopyOption.REPLACE_EXISTING);
                received = counted.getCount();
            }
            receiveZipFile(startTime, attributeMap, source, spool, received);
        } finally {
            Files.deleteIfExists(spool);
        }
    }

    private void receiveZipFile(final Instant startTime,
                                final AttributeMap attributeMap,
                                final String source,
                                final Path zipFile,
                                final long receivedBytes) throws IOException {
        try (final ZipFile zip = ZipUtil.createZipFile(zipFile)) {
            ReceivedZip received = ReceivedZip.index(zip, attributeMap);

            final AttributeMapFilter filter = attributeMapFilterFactory.create();
            final Set<FeedKey> allowed = new LinkedHashSet<>();
            final Map<FeedKey, FeedKey> renamed = new HashMap<>();
            for (final FeedKey feedKey : received.feedKeys()) {
                final AttributeMap feedAttributes = AttributeMapUtil.cloneAllowable(attributeMap);
                AttributeMapUtil.addFeedAndType(feedAttributes, feedKey.feed(), feedKey.type());
                // Throws when the policy rejects the feed, which refuses the whole zip.
                if (filter.filter(feedAttributes)) {
                    // The policy may have named the feed itself, for a group that arrived without one.
                    final FeedKey accepted = FeedKey.of(
                            feedAttributes.get(StandardHeaderArguments.FEED),
                            feedAttributes.get(StandardHeaderArguments.TYPE));
                    if (!accepted.equals(feedKey)) {
                        renamed.put(feedKey, accepted);
                    }
                    allowed.add(accepted);
                }
            }
            if (allowed.isEmpty()) {
                ReceiveLog.dropped(logStream, attributeMap, source, receivedBytes, startTime);
                return;
            }
            received = received.withFeedKeys(renamed);

            final FeedKey singleFeedKey = allowed.size() == 1
                    ? allowed.iterator().next()
                    : null;
            final FileGroupQueue queue = singleFeedKey != null
                    ? outputQueue
                    : splitZipQueue;
            if (queue == null) {
                throw new IOException(LogUtil.message(
                        "The zip holds {} feeds but this proxy has no split-zip queue configured "
                        + "(stages.receive.splitZipQueue), so it cannot be accepted", allowed.size()));
            }

            try (final FileStoreWrite write = store.newWrite()) {
                final FileGroup group = new FileGroup(write.getPath());
                writeEntries(group, received.copyTo(zip, allowed, group.getZip()));
                // The group's meta names its feed when it has one; a multi-feed group names none,
                // because the headers' feed describes none of its entries.
                final AttributeMap groupMeta = AttributeMapUtil.cloneAllowable(attributeMap);
                if (singleFeedKey != null) {
                    AttributeMapUtil.addFeedAndType(groupMeta, singleFeedKey.feed(), singleFeedKey.type());
                } else {
                    groupMeta.remove(StandardHeaderArguments.FEED);
                    groupMeta.remove(StandardHeaderArguments.TYPE);
                }
                AttributeMapUtil.write(groupMeta, group.getMeta());

                publish(queue, write.commit(), singleFeedKey);
                ReceiveLog.received(logStream, attributeMap, source, receivedBytes, startTime);
            }
        }
    }

    private void publish(final FileGroupQueue queue,
                         final FileStoreLocation location,
                         final FeedKey feedKey) throws IOException {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                location,
                feedKey == null ? null : feedKey.feed(),
                feedKey == null ? null : feedKey.type(),
                PipelineStageName.RECEIVE.getConfigName(),
                sourceNodeId,
                null,
                Map.of());
        queue.publish(message);
        LOGGER.debug(() -> LogUtil.message(
                "Published {} to {} for {}", message.messageId(), queue.getName(), location));
    }

    private static void writeEntries(final FileGroup group, final List<ZipEntryGroup> entryGroups) throws IOException {
        try (final Writer writer = Files.newBufferedWriter(group.getEntries())) {
            for (final ZipEntryGroup entryGroup : entryGroups) {
                entryGroup.write(writer);
            }
        }
    }

    private ByteSize maxRequestSize() {
        return receiveDataConfigProvider.get().getMaxRequestSize();
    }
}
