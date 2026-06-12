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

package stroom.ui.config.shared;

import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsStroomConfig;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;

@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class ProcessConfig extends AbstractConfig implements IsStroomConfig {

    private static final long DEFAULT_TIME_LIMIT = 30L;
    private static final long DEFAULT_RECORD_LIMIT = 1000000L;

    @JsonProperty
    @JsonPropertyDescription("The default number of minutes that batch search processing will be limited by.")
    private final long defaultTimeLimit;

    // Would be nice to make this a StroomDuration but it is used by a UI control which can only deal in whole minutes
    @JsonProperty
    @JsonPropertyDescription("The default number of records that batch search processing will be limited by.")
    private final long defaultRecordLimit;

    public ProcessConfig() {
        defaultTimeLimit = DEFAULT_TIME_LIMIT;
        defaultRecordLimit = DEFAULT_RECORD_LIMIT;
    }

    @JsonCreator
    public ProcessConfig(@JsonProperty("defaultTimeLimit") final long defaultTimeLimit,
                         @JsonProperty("defaultRecordLimit") final long defaultRecordLimit) {
        this.defaultTimeLimit = defaultTimeLimit;
        this.defaultRecordLimit = defaultRecordLimit;
    }

    public long getDefaultTimeLimit() {
        return defaultTimeLimit;
    }

    public long getDefaultRecordLimit() {
        return defaultRecordLimit;
    }

    @Override
    public String toString() {
        return "ProcessConfig{" +
                "defaultTimeLimit=" + defaultTimeLimit +
                ", defaultRecordLimit=" + defaultRecordLimit +
                '}';
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final ProcessConfig that = (ProcessConfig) o;
        return defaultTimeLimit == that.defaultTimeLimit &&
                defaultRecordLimit == that.defaultRecordLimit;
    }

    @Override
    public int hashCode() {
        return Objects.hash(defaultTimeLimit, defaultRecordLimit);
    }
}
