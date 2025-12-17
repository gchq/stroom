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

import stroom.query.api.UserTimeZone;
import stroom.util.shared.AbstractBuilder;
import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "keyType",
        "hashLength",
        "temporalResolution",
        "timeZone"
})
@JsonInclude(Include.NON_NULL)
public class MetricKeySchema {

    private static final KeyType DEFAULT_KEY_TYPE = KeyType.TAGS;
    private static final HashLength DEFAULT_HASH_LENGTH = HashLength.INTEGER;
    private static final TemporalResolution DEFAULT_TEMPORAL_RESOLUTION = TemporalResolution.SECOND;
    private static final UserTimeZone DEFAULT_TIME_ZONE = UserTimeZone.utc();

    @JsonProperty
    private final KeyType keyType;
    @JsonPropertyDescription("The hash length to use for foreign keys")
    @JsonProperty
    private final HashLength hashLength;
    @JsonProperty
    private final TemporalResolution temporalResolution;
    @JsonProperty
    private final UserTimeZone timeZone;

    @JsonCreator
    public MetricKeySchema(@JsonProperty("keyType") final KeyType keyType,
                           @JsonProperty("hashLength") final HashLength hashLength,
                           @JsonProperty("temporalResolution") final TemporalResolution temporalResolution,
                           @JsonProperty("timeZone") final UserTimeZone timeZone) {
        this.keyType =  NullSafe.requireNonNullElse(keyType, DEFAULT_KEY_TYPE);
        this.hashLength =  NullSafe.requireNonNullElse(hashLength, DEFAULT_HASH_LENGTH);
        this.temporalResolution = NullSafe.requireNonNullElse(temporalResolution, DEFAULT_TEMPORAL_RESOLUTION);
        this.timeZone = NullSafe.requireNonNullElse(timeZone, DEFAULT_TIME_ZONE);
    }

    public KeyType getKeyType() {
        return keyType;
    }

    public HashLength getHashLength() {
        return hashLength;
    }

    public TemporalResolution getTemporalResolution() {
        return temporalResolution;
    }

    public UserTimeZone getTimeZone() {
        return timeZone;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final MetricKeySchema that = (MetricKeySchema) o;
        return keyType == that.keyType &&
               hashLength == that.hashLength &&
               temporalResolution == that.temporalResolution &&
               Objects.equals(timeZone, that.timeZone);
    }

    @Override
    public int hashCode() {
        return Objects.hash(keyType, hashLength, temporalResolution, timeZone);
    }

    @Override
    public String toString() {
        return "MetricKeySchema{" +
               "keyType=" + keyType +
               ", hashLength=" + hashLength +
               ", temporalResolution=" + temporalResolution +
               ", timeZone=" + timeZone +
               '}';
    }

    public static class Builder extends AbstractBuilder<MetricKeySchema, Builder> {

        private KeyType keyType;
        private HashLength hashLength;
        private TemporalResolution temporalResolution;
        private UserTimeZone timeZone;

        public Builder() {
        }

        public Builder(final MetricKeySchema schema) {
            if (schema != null) {
                this.keyType = schema.keyType;
                this.hashLength = schema.hashLength;
                this.temporalResolution = schema.temporalResolution;
                this.timeZone = schema.timeZone;
            }
        }

        public Builder keyType(final KeyType keyType) {
            this.keyType = keyType;
            return self();
        }

        public Builder hashLength(final HashLength hashLength) {
            this.hashLength = hashLength;
            return self();
        }

        public Builder temporalResolution(final TemporalResolution temporalResolution) {
            this.temporalResolution = temporalResolution;
            return self();
        }

        public Builder timeZone(final UserTimeZone timeZone) {
            this.timeZone = timeZone;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public MetricKeySchema build() {
            return new MetricKeySchema(
                    NullSafe.requireNonNullElse(keyType, DEFAULT_KEY_TYPE),
                    NullSafe.requireNonNullElse(hashLength, DEFAULT_HASH_LENGTH),
                    NullSafe.requireNonNullElse(temporalResolution, DEFAULT_TEMPORAL_RESOLUTION),
                    timeZone == null
                            ? DEFAULT_TIME_ZONE
                            : timeZone.copy().build());
        }
    }
}
