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

import stroom.util.shared.AbstractConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

/**
 * Configuration for the persistent, chunked stepping store (see the chunked-stepping design).
 * <p>
 * NOTE: In Phase 1 this is a plain config holder used directly by {@link StepDataStoreManager}. When the
 * manager becomes an injected singleton (Phase 4) this should be promoted to an
 * {@code IsStroomConfig} hung off {@code PipelineConfig} so it is configurable at runtime (which also
 * requires regenerating {@code ConfigProvidersModule}).
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
public class SteppingConfig extends AbstractConfig {

    private static final String DEFAULT_STORE_SUB_DIR = "stepping";
    private static final long DEFAULT_MAX_RECORDS_PER_STREAM = 1_000_000L;
    private static final long DEFAULT_MAX_BYTES_PER_STREAM = 2L * 1024 * 1024 * 1024; // 2 GiB
    private static final long DEFAULT_MAX_RECORD_SIZE_BYTES = 100L * 1024 * 1024; // 100 MiB
    private static final int DEFAULT_MAX_SWEPT_STREAMS_PER_SESSION = 10;
    private static final int DEFAULT_MAX_RETAINED_FINGERPRINTS_PER_ELEMENT = 3;

    private final String storeSubDir;
    private final long maxRecordsPerStream;
    private final long maxBytesPerStream;
    private final long maxRecordSizeBytes;
    private final int maxSweptStreamsPerSession;
    private final int maxRetainedFingerprintsPerElement;
    private final StroomDuration maxSessionIdleTime;
    private final StroomDuration orphanMaxAge;

    public SteppingConfig() {
        storeSubDir = DEFAULT_STORE_SUB_DIR;
        maxRecordsPerStream = DEFAULT_MAX_RECORDS_PER_STREAM;
        maxBytesPerStream = DEFAULT_MAX_BYTES_PER_STREAM;
        maxRecordSizeBytes = DEFAULT_MAX_RECORD_SIZE_BYTES;
        maxSweptStreamsPerSession = DEFAULT_MAX_SWEPT_STREAMS_PER_SESSION;
        maxRetainedFingerprintsPerElement = DEFAULT_MAX_RETAINED_FINGERPRINTS_PER_ELEMENT;
        maxSessionIdleTime = StroomDuration.ofMinutes(10);
        orphanMaxAge = StroomDuration.ofHours(1);
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
                          @JsonProperty("maxSessionIdleTime") final StroomDuration maxSessionIdleTime,
                          @JsonProperty("orphanMaxAge") final StroomDuration orphanMaxAge) {
        this.storeSubDir = Objects.requireNonNullElse(storeSubDir, DEFAULT_STORE_SUB_DIR);
        this.maxRecordsPerStream = Objects.requireNonNullElse(maxRecordsPerStream, DEFAULT_MAX_RECORDS_PER_STREAM);
        this.maxBytesPerStream = Objects.requireNonNullElse(maxBytesPerStream, DEFAULT_MAX_BYTES_PER_STREAM);
        this.maxRecordSizeBytes = Objects.requireNonNullElse(maxRecordSizeBytes, DEFAULT_MAX_RECORD_SIZE_BYTES);
        this.maxSweptStreamsPerSession = Objects.requireNonNullElse(
                maxSweptStreamsPerSession, DEFAULT_MAX_SWEPT_STREAMS_PER_SESSION);
        this.maxRetainedFingerprintsPerElement = Objects.requireNonNullElse(
                maxRetainedFingerprintsPerElement, DEFAULT_MAX_RETAINED_FINGERPRINTS_PER_ELEMENT);
        this.maxSessionIdleTime = Objects.requireNonNullElse(maxSessionIdleTime, StroomDuration.ofMinutes(10));
        this.orphanMaxAge = Objects.requireNonNullElse(orphanMaxAge, StroomDuration.ofHours(1));
    }

    /**
     * Builder-style copy used mainly by tests to vary a single cap.
     */
    private SteppingConfig(final SteppingConfig source,
                           final Integer maxRetainedFingerprintsPerElement) {
        this.storeSubDir = source.storeSubDir;
        this.maxRecordsPerStream = source.maxRecordsPerStream;
        this.maxBytesPerStream = source.maxBytesPerStream;
        this.maxRecordSizeBytes = source.maxRecordSizeBytes;
        this.maxSweptStreamsPerSession = source.maxSweptStreamsPerSession;
        this.maxRetainedFingerprintsPerElement = Objects.requireNonNullElse(
                maxRetainedFingerprintsPerElement, source.maxRetainedFingerprintsPerElement);
        this.maxSessionIdleTime = source.maxSessionIdleTime;
        this.orphanMaxAge = source.orphanMaxAge;
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

    public SteppingConfig withMaxRetainedFingerprintsPerElement(final int value) {
        return new SteppingConfig(this, value);
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
                ", maxSessionIdleTime=" + maxSessionIdleTime +
                ", orphanMaxAge=" + orphanMaxAge +
                '}';
    }
}
