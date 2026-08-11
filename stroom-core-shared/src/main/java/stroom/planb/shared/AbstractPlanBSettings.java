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

package stroom.planb.shared;

import stroom.docs.shared.Description;
import stroom.util.shared.time.SimpleDuration;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = StateSettings.class, name = "state"),
        @JsonSubTypes.Type(value = TemporalStateSettings.class, name = "temporalState"),
        @JsonSubTypes.Type(value = RangeStateSettings.class, name = "rangeState"),
        @JsonSubTypes.Type(value = TemporalRangeStateSettings.class, name = "temporalRangeState"),
        @JsonSubTypes.Type(value = SessionSettings.class, name = "session"),
        @JsonSubTypes.Type(value = HistogramSettings.class, name = "histogram"),
        @JsonSubTypes.Type(value = MetricSettings.class, name = "metric"),
        @JsonSubTypes.Type(value = TraceSettings.class, name = "trace")
})
@Description("Defines settings for Plan B")
@JsonPropertyOrder({
        "maxStoreSize",
        "retention"
})
@JsonInclude(Include.NON_NULL)
public abstract sealed class AbstractPlanBSettings permits
        AbstractHttpStoreSettings,
        TraceSettings {

    // 10 GiB
    public static final long DEFAULT_MAX_STORE_SIZE = 10737418240L;

    @JsonProperty
    private final Long maxStoreSize;
    @JsonProperty
    private final RetentionSettings retention;

    public AbstractPlanBSettings(final Long maxStoreSize,
                                 final RetentionSettings retention) {
        this.maxStoreSize = Objects.requireNonNullElse(maxStoreSize, DEFAULT_MAX_STORE_SIZE);
        this.retention = Objects.requireNonNullElse(retention, new RetentionSettings.Builder().build());
    }

    public Long getMaxStoreSize() {
        return maxStoreSize;
    }

    public RetentionSettings getRetention() {
        return retention;
    }

    /**
     * Validates the settings that have to agree with each other, for both the client (which blocks
     * the save) and the server (which backstops the import and REST paths).
     *
     * @return the first user-facing message found, or null if the settings are valid
     */
    public static String validationError(final AbstractPlanBSettings settings) {
        if (settings == null) {
            return null;
        }
        final String checkIntervalError = RetentionSettings.checkIntervalError(settings.getRetention());
        if (checkIntervalError != null) {
            return checkIntervalError;
        }
        return archiveAgeError(settings);
    }

    private static String archiveAgeError(final AbstractPlanBSettings settings) {
        final ArchivalSettings archival =
                HasSharedFileStore.archivalSettings(settings).orElse(null);
        final RetentionSettings retention = settings.getRetention();
        if (archival == null || retention == null || !retention.isEnabled()) {
            return null;
        }
        final SimpleDuration archiveAge = archival.getDuration();
        final SimpleDuration retainFor = retention.getDuration();
        if (archiveAge == null || retainFor == null) {
            return null;
        }
        if (archiveAge.getApproxMillis() >= retainFor.getApproxMillis()) {
            return "'Archive Data Older Than' (" + archiveAge.toLongString() + ") must be shorter than "
                   + "'Retain For' (" + retainFor.toLongString() + "), otherwise retention deletes the "
                   + "data before it can be archived and nothing is ever written to the archive.";
        }
        return null;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final AbstractPlanBSettings settings = (AbstractPlanBSettings) o;
        return Objects.equals(maxStoreSize, settings.maxStoreSize) &&
               Objects.equals(retention, settings.retention);
    }

    @Override
    public int hashCode() {
        return Objects.hash(maxStoreSize, retention);
    }

    @Override
    public String toString() {
        return "maxStoreSize=" + maxStoreSize +
               ", retention=" + retention;
    }

    public abstract static class AbstractBuilder<T extends AbstractPlanBSettings, B extends AbstractBuilder<T, ?>> {

        protected Long maxStoreSize;
        protected RetentionSettings retention;

        public AbstractBuilder() {
        }

        public AbstractBuilder(final AbstractPlanBSettings settings) {
            if (settings != null) {
                this.maxStoreSize = settings.maxStoreSize;
                this.retention = settings.retention;
            }
        }

        public B maxStoreSize(final Long maxStoreSize) {
            this.maxStoreSize = maxStoreSize;
            return self();
        }

        public B retention(final RetentionSettings retention) {
            this.retention = retention;
            return self();
        }

        protected abstract B self();

        public abstract T build();
    }
}
