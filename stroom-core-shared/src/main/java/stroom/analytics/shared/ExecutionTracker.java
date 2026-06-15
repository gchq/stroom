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

package stroom.analytics.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({
        "actualExecutionTimeMs",
        "lastEffectiveExecutionTimeMs",
        "nextEffectiveExecutionTimeMs"
})
public class ExecutionTracker {

    @JsonProperty
    private final long actualExecutionTimeMs;
    @JsonProperty
    private final long lastEffectiveExecutionTimeMs;
    @JsonProperty
    private final long nextEffectiveExecutionTimeMs;

    @JsonCreator
    public ExecutionTracker(@JsonProperty("actualExecutionTimeMs") final long actualExecutionTimeMs,
                            @JsonProperty("lastEffectiveExecutionTimeMs") final long lastEffectiveExecutionTimeMs,
                            @JsonProperty("nextEffectiveExecutionTimeMs") final long nextEffectiveExecutionTimeMs) {
        this.actualExecutionTimeMs = actualExecutionTimeMs;
        this.lastEffectiveExecutionTimeMs = lastEffectiveExecutionTimeMs;
        this.nextEffectiveExecutionTimeMs = nextEffectiveExecutionTimeMs;
    }

    public long getActualExecutionTimeMs() {
        return actualExecutionTimeMs;
    }

    public long getLastEffectiveExecutionTimeMs() {
        return lastEffectiveExecutionTimeMs;
    }

    public long getNextEffectiveExecutionTimeMs() {
        return nextEffectiveExecutionTimeMs;
    }

    @Override
    public String toString() {
        return "ExecutionTracker{" +
               "actualExecutionTimeMs=" + actualExecutionTimeMs +
               ", lastEffectiveExecutionTimeMs=" + lastEffectiveExecutionTimeMs +
               ", nextEffectiveExecutionTimeMs=" + nextEffectiveExecutionTimeMs +
               '}';
    }
}
