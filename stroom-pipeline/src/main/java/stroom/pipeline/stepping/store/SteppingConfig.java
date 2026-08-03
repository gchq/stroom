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

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Configuration for the persistent, chunked stepping store (see the chunked-stepping design). Hung off
 * {@code PipelineConfig} as {@code stepping}.
 */
@JsonPropertyOrder(alphabetic = true)
public class SteppingConfig extends AbstractConfig implements IsStroomConfig {

    private static final String DEFAULT_STORE_SUB_DIR = "stepping";
    private static final long DEFAULT_MAX_RECORDS_PER_STREAM = 1_000_000L;
    private static final long DEFAULT_MAX_BYTES_PER_STREAM = 2L * 1024 * 1024 * 1024; // 2 GiB
    private static final long DEFAULT_MAX_RECORD_SIZE_BYTES = 100L * 1024 * 1024; // 100 MiB
    private static final int DEFAULT_MAX_SWEPT_STREAMS_PER_SESSION = 10;
    private static final int DEFAULT_MAX_RETAINED_FINGERPRINTS_PER_ELEMENT = 3;
    private static final int DEFAULT_FILTERED_SCAN_WINDOW = 50;
    private static final boolean DEFAULT_SKELETON_SWEEP = false;
    private static final long DEFAULT_EAGER_MATERIALISATION_RECORDS = 5_000L;
    private static final int DEFAULT_PREFETCH_WINDOW = 10;

    private final String storeSubDir;
    private final long maxRecordsPerStream;
    private final long maxBytesPerStream;
    private final long maxRecordSizeBytes;
    private final int maxSweptStreamsPerSession;
    private final int maxRetainedFingerprintsPerElement;
    private final int filteredScanWindow;
    private final StroomDuration maxSessionIdleTime;
    private final StroomDuration orphanMaxAge;
    private final boolean skeletonSweep;
    private final long eagerMaterialisationRecords;
    private final int prefetchWindow;

    public SteppingConfig() {
        storeSubDir = DEFAULT_STORE_SUB_DIR;
        maxRecordsPerStream = DEFAULT_MAX_RECORDS_PER_STREAM;
        maxBytesPerStream = DEFAULT_MAX_BYTES_PER_STREAM;
        maxRecordSizeBytes = DEFAULT_MAX_RECORD_SIZE_BYTES;
        maxSweptStreamsPerSession = DEFAULT_MAX_SWEPT_STREAMS_PER_SESSION;
        maxRetainedFingerprintsPerElement = DEFAULT_MAX_RETAINED_FINGERPRINTS_PER_ELEMENT;
        filteredScanWindow = DEFAULT_FILTERED_SCAN_WINDOW;
        maxSessionIdleTime = StroomDuration.ofMinutes(10);
        orphanMaxAge = StroomDuration.ofHours(1);
        skeletonSweep = DEFAULT_SKELETON_SWEEP;
        eagerMaterialisationRecords = DEFAULT_EAGER_MATERIALISATION_RECORDS;
        prefetchWindow = DEFAULT_PREFETCH_WINDOW;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SteppingConfig(@JsonProperty("storeSubDir") final String storeSubDir,
                          @JsonProperty("maxRecordsPerStream") final Long maxRecordsPerStream,
                          @JsonProperty("maxBytesPerStream") final Long maxBytesPerStream,
                          @JsonProperty("maxRecordSizeBytes") final Long maxRecordSizeBytes,
                          @JsonProperty("maxSweptStreamsPerSession") final Integer maxSweptStreamsPerSession,
                          @JsonProperty("maxRetainedFingerprintsPerElement")
                          final Integer maxRetainedFingerprintsPerElement,
                          @JsonProperty("filteredScanWindow") final Integer filteredScanWindow,
                          @JsonProperty("maxSessionIdleTime") final StroomDuration maxSessionIdleTime,
                          @JsonProperty("orphanMaxAge") final StroomDuration orphanMaxAge,
                          @JsonProperty("skeletonSweep") final Boolean skeletonSweep,
                          @JsonProperty("eagerMaterialisationRecords") final Long eagerMaterialisationRecords,
                          @JsonProperty("prefetchWindow") final Integer prefetchWindow) {
        this.storeSubDir = Objects.requireNonNullElse(storeSubDir, DEFAULT_STORE_SUB_DIR);
        this.maxRecordsPerStream = Objects.requireNonNullElse(maxRecordsPerStream, DEFAULT_MAX_RECORDS_PER_STREAM);
        this.maxBytesPerStream = Objects.requireNonNullElse(maxBytesPerStream, DEFAULT_MAX_BYTES_PER_STREAM);
        this.maxRecordSizeBytes = Objects.requireNonNullElse(maxRecordSizeBytes, DEFAULT_MAX_RECORD_SIZE_BYTES);
        this.maxSweptStreamsPerSession = Objects.requireNonNullElse(
                maxSweptStreamsPerSession, DEFAULT_MAX_SWEPT_STREAMS_PER_SESSION);
        this.maxRetainedFingerprintsPerElement = Objects.requireNonNullElse(
                maxRetainedFingerprintsPerElement, DEFAULT_MAX_RETAINED_FINGERPRINTS_PER_ELEMENT);
        this.filteredScanWindow = Objects.requireNonNullElse(filteredScanWindow, DEFAULT_FILTERED_SCAN_WINDOW);
        this.maxSessionIdleTime = Objects.requireNonNullElse(maxSessionIdleTime, StroomDuration.ofMinutes(10));
        this.orphanMaxAge = Objects.requireNonNullElse(orphanMaxAge, StroomDuration.ofHours(1));
        this.skeletonSweep = Objects.requireNonNullElse(skeletonSweep, DEFAULT_SKELETON_SWEEP);
        this.eagerMaterialisationRecords = Objects.requireNonNullElse(
                eagerMaterialisationRecords, DEFAULT_EAGER_MATERIALISATION_RECORDS);
        this.prefetchWindow = Objects.requireNonNullElse(prefetchWindow, DEFAULT_PREFETCH_WINDOW);
    }

    /**
     * Builder-style copy used mainly by tests to vary a single value.
     */
    private SteppingConfig(final SteppingConfig source,
                           final Integer maxRetainedFingerprintsPerElement,
                           final Boolean skeletonSweep,
                           final Long eagerMaterialisationRecords,
                           final Integer prefetchWindow,
                           final Integer filteredScanWindow) {
        this.storeSubDir = source.storeSubDir;
        this.maxRecordsPerStream = source.maxRecordsPerStream;
        this.maxBytesPerStream = source.maxBytesPerStream;
        this.maxRecordSizeBytes = source.maxRecordSizeBytes;
        this.maxSweptStreamsPerSession = source.maxSweptStreamsPerSession;
        this.maxRetainedFingerprintsPerElement = Objects.requireNonNullElse(
                maxRetainedFingerprintsPerElement, source.maxRetainedFingerprintsPerElement);
        this.filteredScanWindow = Objects.requireNonNullElse(filteredScanWindow, source.filteredScanWindow);
        this.maxSessionIdleTime = source.maxSessionIdleTime;
        this.orphanMaxAge = source.orphanMaxAge;
        this.skeletonSweep = Objects.requireNonNullElse(skeletonSweep, source.skeletonSweep);
        this.eagerMaterialisationRecords = Objects.requireNonNullElse(
                eagerMaterialisationRecords, source.eagerMaterialisationRecords);
        this.prefetchWindow = Objects.requireNonNullElse(prefetchWindow, source.prefetchWindow);
    }

    @JsonPropertyDescription("The sub-directory of the Stroom temp directory under which stepping session " +
            "IO is persisted.")
    public String getStoreSubDir() {
        return storeSubDir;
    }

    @JsonPropertyDescription("The maximum number of records to capture and persist per stream before " +
            "stepping stops with an error.")
    public long getMaxRecordsPerStream() {
        return maxRecordsPerStream;
    }

    @JsonPropertyDescription("The maximum number of bytes of persisted IO per stream before stepping " +
            "stops with an error.")
    public long getMaxBytesPerStream() {
        return maxBytesPerStream;
    }

    @JsonPropertyDescription("The maximum size in bytes of a single element's IO for a single record.")
    public long getMaxRecordSizeBytes() {
        return maxRecordSizeBytes;
    }

    @JsonPropertyDescription("The maximum number of streams that may be lazily swept within a single " +
            "stepping session.")
    public int getMaxSweptStreamsPerSession() {
        return maxSweptStreamsPerSession;
    }

    @JsonPropertyDescription("How many config-fingerprint versions of an element's IO to retain on disk " +
            "before evicting the least-recently-used version. Higher values make reverting pipeline edits " +
            "cheaper at the cost of more disk.")
    public int getMaxRetainedFingerprintsPerElement() {
        return maxRetainedFingerprintsPerElement;
    }

    @JsonPropertyDescription("How many records a filtered stepping scan materialises per poll. When a " +
            "filter is applied at or below an edited element, whether a record matches is not known until " +
            "that record has been produced, so navigation materialises a window of records at a time and " +
            "scans it, resuming from where it left off on the next poll. Larger values find a distant match " +
            "in fewer polls; smaller values do less work when a filter matches nothing.")
    public int getFilteredScanWindow() {
        return filteredScanWindow;
    }

    @JsonPropertyDescription("How long a stepping session may be idle before it is torn down and its " +
            "persisted IO deleted.")
    public StroomDuration getMaxSessionIdleTime() {
        return maxSessionIdleTime;
    }

    @JsonPropertyDescription("Orphaned stepping session directories older than this are deleted by the " +
            "scheduled cleanup job.")
    public StroomDuration getOrphanMaxAge() {
        return orphanMaxAge;
    }

    @JsonPropertyDescription("Whether the first capture of a stream is a skeleton (backbone) sweep that " +
            "captures IO only for the parser - the record boundary - leaving everything below it to be " +
            "materialised on demand for the records the user actually visits. Roughly ten times cheaper as " +
            "a first pass over a stream, at the cost of below-boundary panes being produced lazily. When " +
            "off, the first capture runs the whole pipeline for every record, as before.")
    public boolean isSkeletonSweep() {
        return skeletonSweep;
    }

    @JsonPropertyDescription("Under the skeleton sweep, a stream whose record count is at or below this " +
            "is materialised in full the first time a step needs anything below the record boundary - " +
            "paying roughly what a whole-pipeline sweep would have cost, once, so that every later step is " +
            "a store read. Streams above it are materialised on demand, a record or window at a time, so " +
            "the cost follows what the user looks at. The default is set where the eager pass costs a few " +
            "seconds (measured ~1ms per record of below-boundary work on the sample event pipeline). Edits " +
            "are never materialised eagerly, whatever the stream size - a post-edit refresh stays " +
            "per-record, which is what keeps it interactive.")
    public long getEagerMaterialisationRecords() {
        return eagerMaterialisationRecords;
    }

    @JsonPropertyDescription("How many records an unfiltered navigation step materialises in its direction " +
            "of travel on a skeleton-swept stream, so that stepping onward hits records the previous step " +
            "already produced (a store read, no pipeline launch) rather than launching per record. The " +
            "demanded record is produced first, so a larger window does not delay the step being served; it " +
            "trades below-boundary work (~1ms per record on the sample pipeline) for fewer launches. " +
            "Refreshes are never widened - a post-edit refresh materialises exactly the record it names.")
    public int getPrefetchWindow() {
        return prefetchWindow;
    }

    public SteppingConfig withMaxRetainedFingerprintsPerElement(final int value) {
        return new SteppingConfig(this, value, null, null, null, null);
    }

    public SteppingConfig withSkeletonSweep(final boolean value) {
        return new SteppingConfig(this, null, value, null, null, null);
    }

    public SteppingConfig withEagerMaterialisationRecords(final long value) {
        return new SteppingConfig(this, null, null, value, null, null);
    }

    public SteppingConfig withPrefetchWindow(final int value) {
        return new SteppingConfig(this, null, null, null, value, null);
    }

    public SteppingConfig withFilteredScanWindow(final int value) {
        return new SteppingConfig(this, null, null, null, null, value);
    }

    @Override
    public String toString() {
        return "SteppingConfig{" +
                "storeSubDir='" + storeSubDir + '\'' +
                ", maxRecordsPerStream=" + maxRecordsPerStream +
                ", maxBytesPerStream=" + maxBytesPerStream +
                ", maxRecordSizeBytes=" + maxRecordSizeBytes +
                ", maxSweptStreamsPerSession=" + maxSweptStreamsPerSession +
                ", maxRetainedFingerprintsPerElement=" + maxRetainedFingerprintsPerElement +
                ", filteredScanWindow=" + filteredScanWindow +
                ", maxSessionIdleTime=" + maxSessionIdleTime +
                ", orphanMaxAge=" + orphanMaxAge +
                ", skeletonSweep=" + skeletonSweep +
                ", eagerMaterialisationRecords=" + eagerMaterialisationRecords +
                ", prefetchWindow=" + prefetchWindow +
                '}';
    }
}
