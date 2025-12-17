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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"severity", "count", "total", "expander"})
@JsonInclude(Include.NON_NULL)
public final class Summary implements Marker, TreeRow {

    @JsonProperty
    private final Severity severity;
    @JsonProperty
    private final int count;
    @JsonProperty
    private final int total;
    @JsonProperty
    private final Expander expander;

    @JsonCreator
    public Summary(@JsonProperty("severity") final Severity severity,
                   @JsonProperty("count") final int count,
                   @JsonProperty("total") final int total,
                   @JsonProperty("expander") final Expander expander) {
        this.severity = severity;
        this.count = count;
        this.total = total;
        this.expander = expander;
    }

    @Override
    public Severity getSeverity() {
        return severity;
    }

    public int getCount() {
        return count;
    }

    public int getTotal() {
        return total;
    }

    @Override
    public Expander getExpander() {
        return expander;
    }
}
