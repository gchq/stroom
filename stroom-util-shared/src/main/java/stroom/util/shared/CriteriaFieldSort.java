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

import java.io.Serializable;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class CriteriaFieldSort implements Serializable {

    @JsonProperty
    private final String id;
    @JsonProperty
    private final boolean desc;
    @JsonProperty
    private final boolean ignoreCase;

    @JsonCreator
    public CriteriaFieldSort(@JsonProperty("id") final String id,
                             @JsonProperty("desc") final Boolean desc,
                             @JsonProperty("ignoreCase") final Boolean ignoreCase) {
        this.id = id;
        this.desc = Objects.requireNonNullElse(desc, false);
        this.ignoreCase = Objects.requireNonNullElse(ignoreCase, false);
    }

    public String getId() {
        return id;
    }

    public boolean isDesc() {
        return desc;
    }

    public boolean isIgnoreCase() {
        return ignoreCase;
    }

    @Override
    public String toString() {
        final String ascDesc = desc
                ? "▼"
                : "▲";
        final String ignoreCaseStr = ignoreCase
                ? " (ignore)"
                : "";
        return id + " " + ascDesc + ignoreCaseStr;
    }
}
