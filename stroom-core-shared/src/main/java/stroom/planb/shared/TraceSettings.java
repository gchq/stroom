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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({
        "maxStoreSize",
        "synchroniseMerge",
        "overwrite",
        "retention",
        "snapshotSettings"
})
@JsonInclude(Include.NON_NULL)
public final class TraceSettings extends AbstractPlanBSettings {

    @JsonCreator
    public TraceSettings(@JsonProperty("maxStoreSize") final Long maxStoreSize,
                         @JsonProperty("synchroniseMerge") final Boolean synchroniseMerge,
                         @JsonProperty("overwrite") final Boolean overwrite,
                         @JsonProperty("retention") final RetentionSettings retention,
                         @JsonProperty("snapshotSettings") final SnapshotSettings snapshotSettings) {
        super(maxStoreSize, synchroniseMerge, overwrite, retention, snapshotSettings);
    }

    public static class Builder extends AbstractBuilder<TraceSettings, Builder> {

        public Builder() {
        }

        public Builder(final TraceSettings settings) {
            super(settings);
        }

        public Builder condense(final DurationSetting condense) {
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public TraceSettings build() {
            return new TraceSettings(
                    maxStoreSize,
                    synchroniseMerge,
                    overwrite,
                    retention,
                    snapshotSettings);
        }
    }
}
