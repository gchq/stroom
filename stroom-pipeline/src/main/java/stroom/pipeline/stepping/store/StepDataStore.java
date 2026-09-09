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

package stroom.pipeline.stepping.store;

import stroom.pipeline.shared.SourceLocation;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.stepping.fingerprint.ElementFingerprinter;
import stroom.util.io.FileUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.ElementId;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SequencedMap;
import java.util.Set;
import java.util.function.BooleanSupplier;

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
 * All public methods synchronize on the instance, so a stream's capture and the reads serving steps from
 * it are serialized (a large read briefly blocks capture). A read/write or per-file lock would be the
 * next step if capture latency ever matters. A record index beyond what has been written reads back as
 * {@link Optional#empty()} rather than failing - {@code StreamSweep}'s progress signal is how a reader
 * waits for a record instead of guessing.
 */
public class StepDataStore {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(StepDataStore.class);

    // Reserved name for the per-part shared-scope state file (see getOrCreateStateFile).
    private static final String STATE_FILE_NAME = "__state__.dat";

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
    private final Map<String, SequencedMap<String, Boolean>> elementFingerprintLru = new HashMap<>();
    // Versions currently in use, reference counted - see pin(). A pinned version is not evictable however
    // old it is, because "least recently used" is a guess at what nobody wants and a running producer or an
    // in-flight read is proof to the contrary.
    private final Map<PinKey, Integer> pinCounts = new HashMap<>();
    // Per-part shared-scope state file: currently the per-record source-location snapshot. Keyed by part
    // only - unlike element IO it carries no fingerprint, because source location is an upstream property
    // that does not change when a downstream element is edited, so it is reused across edits.
    private final Map<Long, ElementSegmentFile> partStateFiles = new HashMap<>();

    private long totalBytes;
    private boolean deleted;

    public StepDataStore(final Path streamDir, final SteppingConfig config) {
        this.streamDir = streamDir;
        this.config = config;
    }

    /**
     * Persist one element's IO for one record. Records for a given (part, element, fingerprint) must be
     * appended in ascending record-index order starting at the part's base index, which may be non-zero
     * (reader/text record detectors are 1-based). A test seam: production writes go through
     * {@code putRecord}.
     */
    public synchronized void putElementData(final StepLocation location,
                                            final ElementId elementId,
                                            final String fingerprint,
                                            final CapturedElementData data) {
        checkNotDeleted();
        final byte[] bytes = CapturedElementDataSerializer.toBytes(data);
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
        enforceRetention(elementId, fingerprint);
    }

    /**
     * Atomically persist all of a record's per-element IO. Every element is serialised and validated
     * (size/byte caps, contiguous ordering) BEFORE anything is written, so a rejected record leaves the
     * store untouched and a reader never observes a partially-written ("torn") trailing record. Capture
     * uses this so that a record only becomes visible/navigable once every element for it is committed.
     */
    public synchronized void putRecord(final StepLocation location, final List<ElementRecord> elements) {
        putRecord(location, elements, null);
    }

    public synchronized void putRecord(final StepLocation location,
                                       final List<ElementRecord> elements,
                                       final SourceLocation sourceLocation) {
        putRecord(location, elements, sourceLocation, null);
    }

    /**
     * Atomically persist all of a record's per-element IO plus its shared-scope state snapshot (the
     * per-record {@link SourceLocation} and {@code stroom:put} map - see {@link RecordScopeState}).
     * Everything is serialised and validated (size/byte caps, contiguous ordering) BEFORE anything is
     * written, so a rejected record leaves the store untouched. The snapshot is written to a per-part state
     * file that carries no fingerprint, because shared scope state is a property of the run rather than of
     * one element's config; on a re-sweep after an edit its records are already present and are skipped,
     * exactly as the unchanged element files are.
     */
    public synchronized void putRecord(final StepLocation location,
                                       final List<ElementRecord> elements,
                                       final SourceLocation sourceLocation,
                                       final Map<String, String> scopeMap) {
        putRecord(location, elements, sourceLocation, scopeMap, RecordOrder.SEQUENTIAL);
    }

    /**
     * How the caller is producing records, which decides whether the in-order append check applies.
     */
    public enum RecordOrder {
        /**
         * One record after another, as a sweep produces them. The in-order check applies: a record arriving
         * out of sequence means a record detector has mis-keyed it, and that has to fail loudly rather than
         * silently mis-address every later record.
         */
        SEQUENTIAL,
        /**
         * Records individually, in whatever order the user visits them, each written under an index the
         * caller already knows. There is no sequence to be out of, so the check does not apply - see
         * {@code stepping-design.md} §11.
         */
        ON_DEMAND
    }

    /**
     * As {@link #putRecord(StepLocation, List, SourceLocation, Map)}, with control over whether records are
     * required to arrive in order.
     */
    public synchronized void putRecord(final StepLocation location,
                                       final List<ElementRecord> elements,
                                       final SourceLocation sourceLocation,
                                       final Map<String, String> scopeMap,
                                       final RecordOrder order) {
        putRecord(location, elements, sourceLocation, scopeMap, null, order);
    }

    /**
     * As above, additionally carrying each {@link stroom.pipeline.stepping.capture.SteppingCounter} element's
     * running total at the end of this record, so a replay of the next one can resume its count.
     */
    public synchronized void putRecord(final StepLocation location,
                                       final List<ElementRecord> elements,
                                       final SourceLocation sourceLocation,
                                       final Map<String, String> scopeMap,
                                       final Map<String, Long> elementCounts,
                                       final RecordOrder order) {
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
            final FileKey key = new FileKey(location.getPartIndex(), element.elementId(), element.fingerprint());
            final ElementSegmentFile existing = openFiles.get(key);

            // Already captured under this exact fingerprint, so by definition it is byte-identical - the
            // fingerprint covers the element's config, its upstream config and the injected code. Skipping
            // is what lets a stream be re-swept after an element is edited: the edited element and its
            // downstream get new fingerprints and are written, while everything upstream is left alone
            // (re-appending it would trip the in-order check below).
            if (existing != null && existing.contains(location.getRecordIndex())) {
                continue;
            }

            final byte[] bytes = CapturedElementDataSerializer.toBytes(element.data());
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

            // Only assert ordering on a file a sweep alone has written. A file that has had a record
            // materialised on demand has holes by design, so "the next index" is not a property it has, and
            // demanding contiguity would reject the sweep's perfectly correct next record.
            if (order == RecordOrder.SEQUENTIAL && existing != null && existing.recordCount() > 0
                && existing.isContiguouslyWritten()) {
                final long expected = existing.nextRecordIndex();
                if (recordIndex != expected) {
                    throw new StepDataStoreException(LogUtil.message(
                            "Records must be appended in order for {} fingerprint {}; expected index {} but got {}",
                            element.elementId(), element.fingerprint(), expected, recordIndex));
                }
            }
            prepared.add(new PreparedWrite(key, element.elementId(), element.fingerprint(), bytes));
        }

        // Prepare the shared-scope state snapshot (source location + stroom:put map) for this record. It is
        // always framed - an absent snapshot still occupies a segment - so the state file stays index-aligned
        // with the record stream. Skipped when the record is already present, i.e. on a re-sweep after an
        // edit; the retained snapshot is the one the full sweep took, which is what a reprocess needs since
        // it is the state as produced by the elements it is not re-running.
        byte[] stateBytes = null;
        final ElementSegmentFile existingState = partStateFiles.get(location.getPartIndex());
        if (existingState == null || !existingState.contains(recordIndex)) {
            if (order == RecordOrder.SEQUENTIAL && existingState != null && existingState.recordCount() > 0
                && existingState.isContiguouslyWritten()) {
                final long expected = existingState.nextRecordIndex();
                if (recordIndex != expected) {
                    throw new StepDataStoreException(LogUtil.message(
                            "Record state must be appended in order for part {}; expected index {} but got {}",
                            location.getPartIndex(), expected, recordIndex));
                }
            }
            stateBytes = RecordScopeStateSerializer.toBytes(
                    new RecordScopeState(sourceLocation, scopeMap, elementCounts));
            batchBytes += stateBytes.length;
        }

        if (totalBytes + batchBytes > config.getMaxBytesPerStream()) {
            throw new StepDataStoreException(LogUtil.message(
                    "Stepping store for this stream would exceed the {} byte limit; narrow your selection",
                    config.getMaxBytesPerStream()));
        }

        // Open every target file before committing anything. Creating a file (mkdirs + FileChannel.open) can
        // fail, and doing it here rather than in the append loop means such a failure happens while the
        // record is still all-or-nothing, instead of after a sibling element has already been appended.
        final List<ElementSegmentFile> targetFiles = new ArrayList<>(prepared.size());
        for (final PreparedWrite write : prepared) {
            final ElementSegmentFile file = openFiles.get(write.key());
            targetFiles.add(file != null
                    ? file
                    : getOrCreateFile(location.getPartIndex(), write.elementId(), write.fingerprint()));
        }
        final ElementSegmentFile stateFile = stateBytes == null
                ? null
                : (existingState != null ? existingState : getOrCreateStateFile(location.getPartIndex()));

        // Commit all elements now that everything has validated and every file is open. An append can still
        // fail on IO; that leaves earlier elements of this record written, but the record is never made
        // visible because partMin/MaxRecordIndex below are what readers navigate by, and the exception fails
        // the whole sweep rather than being skipped over.
        for (int i = 0; i < prepared.size(); i++) {
            final PreparedWrite write = prepared.get(i);
            final ElementSegmentFile file = targetFiles.get(i);
            file.append(recordIndex, write.bytes());
            if (order == RecordOrder.ON_DEMAND) {
                // Remember that this file no longer holds a contiguous run, so a sweep writing to it later
                // is not asked to follow on from a record it had nothing to do with.
                file.markOutOfBandWrite();
            }
            totalBytes += write.bytes().length;
            enforceRetention(write.elementId(), write.fingerprint());
        }
        if (stateFile != null) {
            stateFile.append(recordIndex, stateBytes);
            if (order == RecordOrder.ON_DEMAND) {
                stateFile.markOutOfBandWrite();
            }
            totalBytes += stateBytes.length;
        }
        partMinRecordIndex.merge(location.getPartIndex(), recordIndex, Math::min);
        partMaxRecordIndex.merge(location.getPartIndex(), recordIndex, Math::max);
    }

    /**
     * Read back one element's IO for one record, or empty if not present (element/fingerprint unknown or
     * record not yet written).
     */
    public synchronized Optional<CapturedElementData> getElementData(final StepLocation location,
                                                                   final ElementId elementId,
                                                                   final String fingerprint) {
        checkNotDeleted();
        final ElementSegmentFile file = openFiles.get(new FileKey(location.getPartIndex(), elementId, fingerprint));
        if (file == null || !file.contains(location.getRecordIndex())) {
            return Optional.empty();
        }
        final byte[] bytes = file.read(location.getRecordIndex());
        touchFingerprint(elementId, fingerprint);
        return Optional.ofNullable(CapturedElementDataSerializer.fromBytes(bytes));
    }

    /**
     * Read back the shared-scope source-location snapshot for one record, or empty if none is available
     * (part unknown, record not yet written, or the record was captured without a location). This is the
     * per-record highlight/{@code DataRange} that {@link SourceLocation} carries; the served step's
     * {@code (metaId, part, record)} coordinates come from the step location, not from here.
     */
    public synchronized Optional<SourceLocation> getSourceLocation(final StepLocation location) {
        return getRecordScopeState(location).map(RecordScopeState::sourceLocation);
    }

    /**
     * Read back the {@code stroom:put} map as it stood at the end of one record, or an empty map if the
     * record is unknown or put nothing. A reprocess restores this before replaying the record so that a
     * {@code stroom:get} below the edit still sees what the elements above it put.
     */
    public synchronized Map<String, String> getScopeMap(final StepLocation location) {
        return getRecordScopeState(location)
                .map(RecordScopeState::scopeMap)
                .orElse(Map.of());
    }

    /**
     * Read back the whole shared-scope snapshot for one record, or empty if none is available (part unknown,
     * or the record has not been written yet).
     */
    public synchronized Optional<RecordScopeState> getRecordScopeState(final StepLocation location) {
        checkNotDeleted();
        final ElementSegmentFile file = partStateFiles.get(location.getPartIndex());
        if (file == null || !file.contains(location.getRecordIndex())) {
            return Optional.empty();
        }
        return Optional.ofNullable(RecordScopeStateSerializer.fromBytes(file.read(location.getRecordIndex())));
    }

    /**
     * @return the highest ({@code highest=true}) or lowest record index this element holds in this part at
     * this fingerprint, or -1 if it holds none.
     * <p>
     * This is what lets a scan that materialises records progressively pick up where it left off without
     * anyone remembering where that was: the frontier is written in the store, so the next window simply
     * starts past it. State that would otherwise have to be carried between polls is read back instead.
     * Consumers reach it as {@code first}/{@code last} on {@link #elementCoverage} - nothing calls it
     * directly any more.
     */
    private synchronized long getElementRecordBound(final long partIndex,
                                                   final ElementId elementId,
                                                   final String fingerprint,
                                                   final boolean highest) {
        checkNotDeleted();
        final ElementSegmentFile file = openFiles.get(new FileKey(partIndex, elementId, fingerprint));
        if (file == null) {
            return -1;
        }
        return highest ? file.maxRecordIndex() : file.minRecordIndex();
    }

    /**
     * @return true if this element holds this exact record at this fingerprint. The per-record question
     * behind {@link Coverage#holds} - a sparse element has gaps inside its own bounds, and only the file's
     * extent map knows which indices are real.
     */
    public synchronized boolean hasElementRecord(final long partIndex,
                                                 final ElementId elementId,
                                                 final String fingerprint,
                                                 final long recordIndex) {
        checkNotDeleted();
        final ElementSegmentFile file = openFiles.get(new FileKey(partIndex, elementId, fingerprint));
        return file != null && file.contains(recordIndex);
    }

    /**
     * The store-wide record coverage: which records have been committed at all, per part. Bounds come from
     * the per-part min/max the readers already navigate by; {@code holds} answers from the un-fingerprinted
     * state file, which every committed record occupies, so holes punched by on-demand materialisation are
     * reported honestly rather than spanned over.
     *
     * @param extentFinal whether the producer filling this store has finished - the store itself cannot know,
     *                    because "no more records" is producer knowledge, not storage knowledge.
     */
    public synchronized Coverage recordCoverage(final BooleanSupplier extentFinal) {
        return new Coverage() {
            @Override
            public List<Long> parts() {
                return getPartIndices();
            }

            @Override
            public long first(final long partIndex) {
                return getFirstRecordIndex(partIndex);
            }

            @Override
            public long last(final long partIndex) {
                return getLastRecordIndex(partIndex);
            }

            @Override
            public boolean holds(final long partIndex, final long recordIndex) {
                synchronized (StepDataStore.this) {
                    final ElementSegmentFile state = partStateFiles.get(partIndex);
                    return state != null && state.contains(recordIndex);
                }
            }

            @Override
            public boolean isExtentFinal() {
                return extentFinal.getAsBoolean();
            }
        };
    }

    /**
     * One element's coverage at one fingerprint: the sparse truth of what has been materialised, live against
     * the files as they fill.
     *
     * @param extentFinal whether this element will receive further records - producer knowledge, as above.
     */
    public synchronized Coverage elementCoverage(final ElementId elementId,
                                                 final String fingerprint,
                                                 final BooleanSupplier extentFinal) {
        return new Coverage() {
            @Override
            public List<Long> parts() {
                synchronized (StepDataStore.this) {
                    return openFiles.keySet().stream()
                            .filter(key -> key.elementId().equals(elementId)
                                           && key.fingerprint().equals(fingerprint))
                            .map(FileKey::partIndex)
                            .sorted()
                            .toList();
                }
            }

            @Override
            public long first(final long partIndex) {
                return getElementRecordBound(partIndex, elementId, fingerprint, false);
            }

            @Override
            public long last(final long partIndex) {
                return getElementRecordBound(partIndex, elementId, fingerprint, true);
            }

            @Override
            public boolean holds(final long partIndex, final long recordIndex) {
                return hasElementRecord(partIndex, elementId, fingerprint, recordIndex);
            }

            @Override
            public boolean isExtentFinal() {
                return extentFinal.getAsBoolean();
            }
        };
    }

    /**
     * @return true if this element holds <b>every</b> record the stream has, at this fingerprint.
     * <p>
     * Distinct from {@link #hasElement}, which is true of a single stored record. That was an adequate
     * question while the only writer was a sweep that captured all of a stream or none of it, but an element
     * whose records are materialised individually as the user visits them is <i>present</i> long before it is
     * <i>reusable</i>. Anything deciding whether an element's output can be reused wholesale - the reuse plan
     * above all - has to ask this instead, or one materialised record makes the element look complete.
     * <p>
     * Answered in constant time per part: a file with no holes holds exactly {@code last - first + 1}
     * records, and this runs on every step, so it must not walk the stream to find out.
     */
    public synchronized boolean hasCompleteElement(final ElementId elementId, final String fingerprint) {
        checkNotDeleted();
        if (partMinRecordIndex.isEmpty()) {
            return false;
        }
        for (final Map.Entry<Long, Long> entry : partMinRecordIndex.entrySet()) {
            final long partIndex = entry.getKey();
            final Long max = partMaxRecordIndex.get(partIndex);
            if (max == null) {
                return false;
            }
            final ElementSegmentFile file = openFiles.get(new FileKey(partIndex, elementId, fingerprint));
            if (file == null || file.recordCount() != (max - entry.getValue() + 1)) {
                return false;
            }
        }
        return true;
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
     * @return the distinct element ids that have any captured IO in this store (across parts and
     * fingerprints). Empty before the first sweep; unioned into the pipeline-derived steppable set for reprocess
     * planning without re-deriving it from element roles.
     */
    public synchronized Set<String> getCapturedElementIds() {
        final Set<String> ids = new HashSet<>();
        for (final FileKey key : openFiles.keySet()) {
            ids.add(key.elementId().getId());
        }
        return ids;
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
     * Claim the given versions ({@code elementId -> fingerprint}, i.e. exactly an
     * {@code ElementFingerprints.getCumulativeFingerprints()} map) so that the retention LRU cannot evict
     * them while they are in use. Release the returned {@link StorePin} - in a {@code finally} or with
     * try-with-resources - when the work is done.
     * <p>
     * Why this exists: the LRU's premise is that the least-recently-used version of an element is the one
     * nobody wants any more. That is a fair guess about <i>past</i> versions, and a bad one about a version
     * something is using right now. Two producers are routinely live at once (a stream capture and a
     * materialisation of the records the user is looking at), and an edit adds a fingerprint per element per
     * distinct version of the code, so a handful of edits while a capture runs can push that capture's own
     * fingerprint off the end of the retention window. Evicting it would close and delete the file
     * underneath its writer, which then silently re-creates an empty one and produces a truncated element.
     * The same applies to a read: a step assembles several elements' IO over several calls, and a version
     * evicted between them reads back as "never captured".
     * <p>
     * Pins are reference counted, so overlapping producers and readers claiming the same version are
     * independent of one another, and pinning a version that is not in the store yet is legitimate - a
     * producer pins what it is <i>about</i> to write.
     */
    public synchronized StorePin pin(final Map<String, String> fingerprintByElementId) {
        if (fingerprintByElementId == null || fingerprintByElementId.isEmpty()) {
            return new StorePin(() -> {
            });
        }
        final List<PinKey> keys = new ArrayList<>(fingerprintByElementId.size());
        fingerprintByElementId.forEach((elementId, fingerprint) -> {
            if (elementId != null && fingerprint != null) {
                final PinKey key = new PinKey(elementId, fingerprint);
                pinCounts.merge(key, 1, Integer::sum);
                keys.add(key);
            }
        });
        return new StorePin(() -> unpin(keys));
    }

    private synchronized void unpin(final List<PinKey> keys) {
        for (final PinKey key : keys) {
            pinCounts.computeIfPresent(key, (k, count) -> count <= 1 ? null : count - 1);
        }
    }

    private boolean isPinned(final ElementId elementId, final String fingerprint) {
        return pinCounts.containsKey(new PinKey(elementId.getId(), fingerprint));
    }

    /**
     * Evict (close and delete) all files for the given element at the given fingerprint, across all parts.
     * <p>
     * This is an explicit instruction rather than the retention policy making a choice, so unlike the LRU it
     * does not consult {@link #pin} - a caller that asks for a version to go has said what it means.
     */
    public synchronized void evictElement(final ElementId elementId, final String fingerprint) {
        checkNotDeleted();
        removeFingerprintFiles(elementId, fingerprint);
        final SequencedMap<String, Boolean> lru = elementFingerprintLru.get(elementId.getId());
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
        for (final ElementSegmentFile file : partStateFiles.values()) {
            file.closeQuietly();
        }
        openFiles.clear();
        partStateFiles.clear();
        partMinRecordIndex.clear();
        partMaxRecordIndex.clear();
        elementFingerprintLru.clear();
        // Nothing is left to evict, so outstanding pins protect nothing; releasing one after this is a no-op.
        pinCounts.clear();
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

    private ElementSegmentFile getOrCreateStateFile(final long partIndex) {
        return partStateFiles.computeIfAbsent(partIndex, k -> {
            // A file (not a directory) named with a '.': element entries at this level are directories named
            // encode(elementId), and encode() always escapes '.', so this name cannot collide with one.
            final Path dataFile = streamDir
                    .resolve(Long.toString(partIndex))
                    .resolve(STATE_FILE_NAME);
            try {
                FileUtil.mkdirs(dataFile.getParent());
                return new ElementSegmentFile(dataFile);
            } catch (final IOException e) {
                throw new StepDataStoreException(LogUtil.message(
                        "Unable to open stepping store state file {}", FileUtil.getCanonicalPath(dataFile)), e);
            }
        });
    }

    /**
     * Refresh a version's recency in the retention LRU. Reads call this too - a version being read is a
     * version worth keeping - but a read never deletes anything: eviction is enforced only on writes
     * ({@link #enforceRetention}), so a get can never remove a sibling version's files as a side effect.
     */
    private void touchFingerprint(final ElementId elementId, final String fingerprint) {
        elementFingerprintLru.computeIfAbsent(
                        elementId.getId(),
                        k -> new LinkedHashMap<String, Boolean>(16, 0.75f, true))
                .put(fingerprint, Boolean.TRUE);
    }

    private void enforceRetention(final ElementId elementId, final String fingerprint) {
        touchFingerprint(elementId, fingerprint);
        // An element edited repeatedly accumulates a fingerprint per distinct version, and the oldest
        // unpinned one is evicted - which is what makes reverting an edit free only while the prior version
        // is still retained. Versions something is using are pinned (see pin()) and skipped, so the limit
        // bounds the retained HISTORY rather than capping what may be in use at once.
        final SequencedMap<String, Boolean> lru = elementFingerprintLru.get(elementId.getId());

        // Always retain at least the fingerprint we just touched; a misconfigured 0/negative retain
        // limit must not delete the data being written.
        final int max = Math.max(1, config.getMaxRetainedFingerprintsPerElement());
        while (lru.size() > max) {
            final String eldest = eldestEvictable(elementId, lru, fingerprint);
            if (eldest == null) {
                // Everything older is pinned, so the retention limit gives way rather than deleting data
                // out from under a producer or a reader. Transient by nature: the pins are released when
                // the work using them ends, and the next touch evicts as usual.
                LOGGER.debug(() -> LogUtil.message(
                        "Retaining {} versions of element {} (limit {}) - the rest are in use",
                        lru.size(), elementId, max));
                break;
            }
            lru.remove(eldest);
            removeFingerprintFiles(elementId, eldest);
            LOGGER.debug(() -> LogUtil.message(
                    "Evicted stepping IO for element {} fingerprint {} (retain limit {})",
                    elementId, eldest, max));
        }
    }

    /**
     * @return the least-recently-used fingerprint of this element that is neither pinned nor the one just
     * touched, or null if there is no such version to give up.
     */
    private String eldestEvictable(final ElementId elementId,
                                   final SequencedMap<String, Boolean> lru,
                                   final String touched) {
        // Access-ordered, so this walks eldest-first.
        for (final String candidate : lru.keySet()) {
            if (!candidate.equals(touched) && !isPinned(elementId, candidate)) {
                return candidate;
            }
        }
        return null;
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
                totalBytes = Math.max(0, totalBytes - file.size());
                file.closeQuietly();
                FileUtil.deleteFile(file.dataFile());
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
     * A pinned version. Keyed by element id string rather than {@link ElementId} to match
     * {@code ElementFingerprints}, which is keyed the same way, and the LRU, which is too.
     */
    private record PinKey(String elementId, String fingerprint) {
    }

    /**
     * One element's IO for a record, for an atomic {@link #putRecord} write.
     */
    public record ElementRecord(ElementId elementId, String fingerprint, CapturedElementData data) {
    }

    private record PreparedWrite(FileKey key, ElementId elementId, String fingerprint, byte[] bytes) {
    }

}
