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

package stroom.pipeline.stepping;

import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.util.io.FileUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ElementId;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.ByteBuffer;
import java.nio.channels.FileChannel;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * On-disk, content-addressed store of per-element stepping IO for a single stream (metaId).
 * <p>
 * Each steppable element's IO is persisted to its own segmented file keyed by a config
 * {@code fingerprint} (see {@link ElementFingerprinter}). A record's IO is one segment within that
 * file, addressed by record index, giving O(1) random access. Because files are keyed by fingerprint,
 * editing an element writes to a new file while leaving upstream (and prior-version) files intact, so
 * reverting an edit reuses the still-present file. Layout under the stream directory:
 * <pre>
 *   {partIndex}/{urlEncodedElementId}/{fingerprint}.dat
 * </pre>
 * <p>
 * This is a purpose-built segmented file (data file + in-memory offset index) rather than the fs
 * data-store's {@code RASegment*} classes, which are package-private to {@code stroom-data-store-impl-fs}
 * and not reachable from this module. The data spills to disk; only the small offset index is held in
 * memory.
 * <p>
 * All public methods synchronize on the instance, so writes and reads are serialized (a large read
 * briefly blocks capture). That is fine for Phase 1; a later phase may switch to a read/write or
 * per-file lock if capture latency matters. Callers must not read a record index beyond what has been
 * written (the session watermark, Phase 3, enforces this); an unwritten record reads back as
 * {@link Optional#empty()}.
 */
public class StepDataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StepDataStore.class);

    private final Path streamDir;
    private final SteppingConfig config;

    // Open segment files keyed by part+element+fingerprint.
    private final Map<FileKey, ElementSegmentFile> openFiles = new HashMap<>();
    // Per-part record-index range (min/max) seen. Record indices are per-part but NOT necessarily
    // 0-based: SAX record detection is 0-based, reader/text record detection is 1-based, so the store
    // preserves whatever index the detector produced (matching the StepLocations the legacy stepper uses).
    private final Map<Long, Long> partMinRecordIndex = new HashMap<>();
    private final Map<Long, Long> partMaxRecordIndex = new HashMap<>();
    // Per-element LRU of retained fingerprints (access-ordered; eldest first) for version eviction.
    private final Map<String, LinkedHashMap<String, Boolean>> elementFingerprintLru = new HashMap<>();

    private long totalBytes;
    private boolean deleted;

    public StepDataStore(final Path streamDir, final SteppingConfig config) {
        this.streamDir = streamDir;
        this.config = config;
    }

    /**
     * Persist one element's IO for one record. Records for a given (part, element, fingerprint) must be
     * appended in ascending record-index order starting at 0.
     */
    public synchronized void putElementData(final StepLocation location,
                                            final ElementId elementId,
                                            final String fingerprint,
                                            final SharedElementData data) {
        checkNotDeleted();
        final byte[] bytes = JsonUtil.writeValueAsBytes(data, false);
        if (bytes == null) {
            throw new StepDataStoreException(LogUtil.message(
                    "Unable to serialise element data for {} at {}", elementId, location));
        }
        if (bytes.length > config.getMaxRecordSizeBytes()) {
            throw new StepDataStoreException(LogUtil.message(
                    "Element IO for {} at {} is {} bytes which exceeds the {} byte limit",
                    elementId, location, bytes.length, config.getMaxRecordSizeBytes()));
        }
        if (totalBytes + bytes.length > config.getMaxBytesPerStream()) {
            throw new StepDataStoreException(LogUtil.message(
                    "Stepping store for this stream would exceed the {} byte limit; narrow your selection",
                    config.getMaxBytesPerStream()));
        }

        final long recordIndex = location.getRecordIndex();
        if (recordIndex >= config.getMaxRecordsPerStream()) {
            throw new StepDataStoreException(LogUtil.message(
                    "Stepping store for this stream would exceed the {} record limit; narrow your selection",
                    config.getMaxRecordsPerStream()));
        }

        // The first write for a (part, element, fingerprint) establishes the base record index (which may
        // be non-zero); subsequent writes must be strictly the next contiguous index. Validating before
        // creating a file means a rejected out-of-order write never leaves an empty file/channel behind.
        final ElementSegmentFile existing = openFiles.get(new FileKey(location.getPartIndex(), elementId, fingerprint));
        if (existing != null && existing.recordCount() > 0) {
            final long expected = existing.nextRecordIndex();
            if (recordIndex != expected) {
                throw new StepDataStoreException(LogUtil.message(
                        "Records must be appended in order for {} fingerprint {}; expected index {} but got {}",
                        elementId, fingerprint, expected, recordIndex));
            }
        }

        final ElementSegmentFile file = existing != null
                ? existing
                : getOrCreateFile(location.getPartIndex(), elementId, fingerprint);
        file.append(recordIndex, bytes);
        totalBytes += bytes.length;
        partMinRecordIndex.merge(location.getPartIndex(), recordIndex, Math::min);
        partMaxRecordIndex.merge(location.getPartIndex(), recordIndex, Math::max);
        touchFingerprint(elementId, fingerprint);
    }

    /**
     * Atomically persist all of a record's per-element IO. Every element is serialised and validated
     * (size/byte caps, contiguous ordering) BEFORE anything is written, so a rejected record leaves the
     * store untouched and a reader never observes a partially-written ("torn") trailing record. Capture
     * uses this so that a record only becomes visible/navigable once every element for it is committed.
     */
    public synchronized void putRecord(final StepLocation location, final List<ElementRecord> elements) {
        checkNotDeleted();
        if (elements == null || elements.isEmpty()) {
            return;
        }
        final long recordIndex = location.getRecordIndex();
        if (recordIndex >= config.getMaxRecordsPerStream()) {
            throw new StepDataStoreException(LogUtil.message(
                    "Stepping store for this stream would exceed the {} record limit; narrow your selection",
                    config.getMaxRecordsPerStream()));
        }

        // Pre-serialise and validate every element up-front so nothing is written unless all will succeed.
        final List<PreparedWrite> prepared = new ArrayList<>(elements.size());
        long batchBytes = 0;
        for (final ElementRecord element : elements) {
            final byte[] bytes = JsonUtil.writeValueAsBytes(element.data(), false);
            if (bytes == null) {
                throw new StepDataStoreException(LogUtil.message(
                        "Unable to serialise element data for {} at {}", element.elementId(), location));
            }
            if (bytes.length > config.getMaxRecordSizeBytes()) {
                throw new StepDataStoreException(LogUtil.message(
                        "Element IO for {} at {} is {} bytes which exceeds the {} byte limit",
                        element.elementId(), location, bytes.length, config.getMaxRecordSizeBytes()));
            }
            batchBytes += bytes.length;

            final FileKey key = new FileKey(location.getPartIndex(), element.elementId(), element.fingerprint());
            final ElementSegmentFile existing = openFiles.get(key);
            if (existing != null && existing.recordCount() > 0) {
                final long expected = existing.nextRecordIndex();
                if (recordIndex != expected) {
                    throw new StepDataStoreException(LogUtil.message(
                            "Records must be appended in order for {} fingerprint {}; expected index {} but got {}",
                            element.elementId(), element.fingerprint(), expected, recordIndex));
                }
            }
            prepared.add(new PreparedWrite(key, element.elementId(), element.fingerprint(), bytes));
        }
        if (totalBytes + batchBytes > config.getMaxBytesPerStream()) {
            throw new StepDataStoreException(LogUtil.message(
                    "Stepping store for this stream would exceed the {} byte limit; narrow your selection",
                    config.getMaxBytesPerStream()));
        }

        // Commit all elements now that everything has validated.
        for (final PreparedWrite write : prepared) {
            final ElementSegmentFile file = openFiles.containsKey(write.key())
                    ? openFiles.get(write.key())
                    : getOrCreateFile(location.getPartIndex(), write.elementId(), write.fingerprint());
            file.append(recordIndex, write.bytes());
            totalBytes += write.bytes().length;
            touchFingerprint(write.elementId(), write.fingerprint());
        }
        partMinRecordIndex.merge(location.getPartIndex(), recordIndex, Math::min);
        partMaxRecordIndex.merge(location.getPartIndex(), recordIndex, Math::max);
    }

    /**
     * Read back one element's IO for one record, or empty if not present (element/fingerprint unknown or
     * record not yet written).
     */
    public synchronized Optional<SharedElementData> getElementData(final StepLocation location,
                                                                   final ElementId elementId,
                                                                   final String fingerprint) {
        checkNotDeleted();
        final ElementSegmentFile file = openFiles.get(new FileKey(location.getPartIndex(), elementId, fingerprint));
        if (file == null || !file.contains(location.getRecordIndex())) {
            return Optional.empty();
        }
        final byte[] bytes = file.read(location.getRecordIndex());
        touchFingerprint(elementId, fingerprint);
        return Optional.ofNullable(JsonUtil.readValue(bytes, SharedElementData.class));
    }

    /**
     * @return true if any IO has been stored for this element at this fingerprint (across parts).
     */
    public synchronized boolean hasElement(final ElementId elementId, final String fingerprint) {
        checkNotDeleted();
        return openFiles.keySet().stream()
                .anyMatch(key -> key.elementId.equals(elementId) && key.fingerprint.equals(fingerprint));
    }

    /**
     * @return the number of records captured for the given part index.
     */
    public synchronized long getRecordCount(final long partIndex) {
        final Long min = partMinRecordIndex.get(partIndex);
        final Long max = partMaxRecordIndex.get(partIndex);
        return (min == null || max == null) ? 0L : (max - min + 1);
    }

    /**
     * @return the first (lowest) record index captured for the part, or -1 if none.
     */
    public synchronized long getFirstRecordIndex(final long partIndex) {
        return partMinRecordIndex.getOrDefault(partIndex, -1L);
    }

    /**
     * @return the last (highest) record index captured for the part, or -1 if none.
     */
    public synchronized long getLastRecordIndex(final long partIndex) {
        return partMaxRecordIndex.getOrDefault(partIndex, -1L);
    }

    /**
     * @return the number of distinct parts that have had records captured.
     */
    public synchronized int getPartCount() {
        return partMinRecordIndex.size();
    }

    /**
     * @return the part indices that have records, in ascending order.
     */
    public synchronized List<Long> getPartIndices() {
        final List<Long> parts = new ArrayList<>(partMinRecordIndex.keySet());
        parts.sort(Comparator.naturalOrder());
        return parts;
    }

    /**
     * Evict (close and delete) all files for the given element at the given fingerprint, across all parts.
     */
    public synchronized void evictElement(final ElementId elementId, final String fingerprint) {
        checkNotDeleted();
        removeFingerprintFiles(elementId, fingerprint);
        final LinkedHashMap<String, Boolean> lru = elementFingerprintLru.get(elementId.getId());
        if (lru != null) {
            lru.remove(fingerprint);
            if (lru.isEmpty()) {
                elementFingerprintLru.remove(elementId.getId());
            }
        }
    }

    /**
     * Close all open files and delete the stream directory. The store must not be used afterwards.
     */
    public synchronized void deleteAll() {
        if (deleted) {
            return;
        }
        for (final ElementSegmentFile file : openFiles.values()) {
            file.closeQuietly();
        }
        openFiles.clear();
        partMinRecordIndex.clear();
        partMaxRecordIndex.clear();
        elementFingerprintLru.clear();
        totalBytes = 0;
        FileUtil.deleteDir(streamDir);
        deleted = true;
    }

    Path getStreamDir() {
        return streamDir;
    }

    private ElementSegmentFile getOrCreateFile(final long partIndex,
                                               final ElementId elementId,
                                               final String fingerprint) {
        final FileKey key = new FileKey(partIndex, elementId, fingerprint);
        return openFiles.computeIfAbsent(key, k -> {
            final Path dataFile = streamDir
                    .resolve(Long.toString(partIndex))
                    .resolve(encode(elementId.getId()))
                    .resolve(fingerprint + ".dat");
            try {
                FileUtil.mkdirs(dataFile.getParent());
                return new ElementSegmentFile(dataFile);
            } catch (final IOException e) {
                throw new StepDataStoreException(LogUtil.message(
                        "Unable to open stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
            }
        });
    }

    private void touchFingerprint(final ElementId elementId, final String fingerprint) {
        // NOTE: this is a plain LRU. When staged reprocess (Phase 2) writes a new fingerprint for an
        // element while an older one is still in use, the active fingerprints for the current pipeline
        // config must be pinned (or maxRetainedFingerprintsPerElement kept >= the active count) so a
        // fingerprint currently being captured/served is never evicted.
        final LinkedHashMap<String, Boolean> lru = elementFingerprintLru.computeIfAbsent(
                elementId.getId(),
                k -> new LinkedHashMap<>(16, 0.75f, true));
        lru.put(fingerprint, Boolean.TRUE);

        // Always retain at least the fingerprint we just touched; a misconfigured 0/negative retain
        // limit must not delete the data being written.
        final int max = Math.max(1, config.getMaxRetainedFingerprintsPerElement());
        while (lru.size() > max) {
            final String eldest = lru.entrySet().iterator().next().getKey();
            lru.remove(eldest);
            removeFingerprintFiles(elementId, eldest);
            LOGGER.debug(() -> LogUtil.message(
                    "Evicted stepping IO for element {} fingerprint {} (retain limit {})",
                    elementId, eldest, max));
        }
    }

    private void removeFingerprintFiles(final ElementId elementId, final String fingerprint) {
        final List<FileKey> toRemove = new ArrayList<>();
        for (final FileKey key : openFiles.keySet()) {
            if (key.elementId.equals(elementId) && key.fingerprint.equals(fingerprint)) {
                toRemove.add(key);
            }
        }
        for (final FileKey key : toRemove) {
            final ElementSegmentFile file = openFiles.remove(key);
            if (file != null) {
                // Reclaim the byte budget so the maxBytesPerStream cap reflects only live data.
                totalBytes = Math.max(0, totalBytes - file.size);
                file.closeQuietly();
                FileUtil.deleteFile(file.dataFile);
            }
        }
    }

    private void checkNotDeleted() {
        if (deleted) {
            throw new StepDataStoreException("Stepping store for this stream has been deleted");
        }
    }

    private static String encode(final String value) {
        // URLEncoder encodes path separators but leaves '.' untouched, so also escape dots to stop an
        // element id of "." or ".." from resolving to a parent directory.
        return URLEncoder.encode(value, StandardCharsets.UTF_8).replace(".", "%2E");
    }

    // --------------------------------------------------------------------------------

    private record FileKey(long partIndex, ElementId elementId, String fingerprint) {
    }

    /**
     * One element's IO for a record, for an atomic {@link #putRecord} write.
     */
    public record ElementRecord(ElementId elementId, String fingerprint, SharedElementData data) {
    }

    private record PreparedWrite(FileKey key, ElementId elementId, String fingerprint, byte[] bytes) {
    }

    // --------------------------------------------------------------------------------

    /**
     * A single element's segmented data file: raw record bytes appended to a data file, with an
     * in-memory index of record end offsets for O(1) random access by record index.
     */
    private static final class ElementSegmentFile {

        private final Path dataFile;
        private final FileChannel channel;
        // endOffsets.get(s) = exclusive end byte offset of segment s; segment s = recordIndex - baseRecordIndex.
        private final List<Long> endOffsets = new ArrayList<>();
        // The record index of the first record written (segment 0); may be non-zero (e.g. reader detectors
        // are 1-based). -1 until the first append.
        private long baseRecordIndex = -1;
        private long size;

        private ElementSegmentFile(final Path dataFile) throws IOException {
            this.dataFile = dataFile;
            this.channel = FileChannel.open(dataFile,
                    StandardOpenOption.CREATE,
                    StandardOpenOption.READ,
                    StandardOpenOption.WRITE);
        }

        private void append(final long recordIndex, final byte[] bytes) {
            if (baseRecordIndex < 0) {
                baseRecordIndex = recordIndex;
            }
            try {
                final ByteBuffer buffer = ByteBuffer.wrap(bytes);
                long position = size;
                while (buffer.hasRemaining()) {
                    position += channel.write(buffer, position);
                }
                size = position;
                endOffsets.add(size);
            } catch (final IOException e) {
                throw new StepDataStoreException(LogUtil.message(
                        "Unable to write to stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
            }
        }

        private byte[] read(final long recordIndex) {
            final int index = (int) (recordIndex - baseRecordIndex);
            final long start = index == 0 ? 0L : endOffsets.get(index - 1);
            final long end = endOffsets.get(index);
            final int length = (int) (end - start);
            final ByteBuffer buffer = ByteBuffer.allocate(length);
            try {
                long position = start;
                while (buffer.hasRemaining()) {
                    final int read = channel.read(buffer, position);
                    if (read < 0) {
                        throw new StepDataStoreException(LogUtil.message(
                                "Unexpected end of stepping store file {} reading record {}",
                                FileUtil.getCanonicalPath(dataFile), recordIndex));
                    }
                    position += read;
                }
            } catch (final IOException e) {
                throw new StepDataStoreException(LogUtil.message(
                        "Unable to read stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
            }
            return buffer.array();
        }

        private long recordCount() {
            return endOffsets.size();
        }

        /**
         * @return the next expected (contiguous) record index, or -1 if nothing written yet.
         */
        private long nextRecordIndex() {
            return baseRecordIndex < 0 ? -1 : baseRecordIndex + endOffsets.size();
        }

        /**
         * @return true if the given record index falls within the range written to this file.
         */
        private boolean contains(final long recordIndex) {
            return baseRecordIndex >= 0
                    && recordIndex >= baseRecordIndex
                    && recordIndex < baseRecordIndex + endOffsets.size();
        }

        private void closeQuietly() {
            try {
                channel.close();
            } catch (final IOException e) {
                LOGGER.debug(() -> LogUtil.message(
                        "Error closing stepping store file {}", FileUtil.getCanonicalPath(dataFile)), e);
            }
        }
    }
}
