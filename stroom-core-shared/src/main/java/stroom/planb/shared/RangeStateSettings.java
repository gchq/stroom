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

import stroom.util.shared.NullSafe;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "overwrite",
        "retention",
        "snapshotSettings",
        "keySchema",
        "valueSchema"
})
@JsonInclude(Include.NON_NULL)
public final class RangeStateSettings extends AbstractPlanBSettings {

    @JsonProperty
    private final RangeKeySchema keySchema;
    @JsonProperty
    private final StateValueSchema valueSchema;

    @JsonCreator
    public RangeStateSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                              @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                              @JsonProperty("overwrite") final Boolean overwrite,
                              @JsonProperty("retention") final RetentionSettings retention,
                              @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings,
                              @JsonProperty("keySchema") final RangeKeySchema keySchema,
                              @JsonProperty("valueSchema") final StateValueSchema valueSchema) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
        this.keySchema = NullSafe.requireNonNullElse(keySchema, new RangeKeySchema.Builder().build());
        this.valueSchema = NullSafe.requireNonNullElse(valueSchema, new StateValueSchema.Builder().build());
    }

    public RangeKeySchema getKeySchema() {
        return keySchema;
    }

    public StateValueSchema getValueSchema() {
        return valueSchema;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final RangeStateSettings that = (RangeStateSettings) o;
        return Objects.equals(keySchema, that.keySchema) &&
               Objects.equals(valueSchema, that.valueSchema);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), keySchema, valueSchema);
    }

    @Override
    public String toString() {
        return "RangedStateSettings{" +
               super.toString() +
               ", keySchema=" + keySchema +
               ", valueSchema=" + valueSchema +
               '}';
    }

    public static class Builder extends AbstractBuilder<RangeStateSettings, Builder> {

        private RangeKeySchema keySchema;
        private StateValueSchema valueSchema;

        public Builder() {
        }

        public Builder(final RangeStateSettings settings) {
            super(settings);
            if (settings != null) {
                this.keySchema = settings.keySchema;
                this.valueSchema = settings.valueSchema;
            }
        }

        public Builder keySchema(final RangeKeySchema keySchema) {
            this.keySchema = keySchema;
            return self();
        }

        public Builder valueSchema(final StateValueSchema valueSchema) {
            this.valueSchema = valueSchema;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public RangeStateSettings build() {
            return new RangeStateSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    overwrite,
                    retention,
                    snapshotSettings,
                    keySchema,
                    valueSchema);
        }
    }
}
