/*
 * Copyright 2017 Crown Copyright
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

@JsonInclude(Include.NON_NULL)
public class Sort implements Serializable {
    private static final long serialVersionUID = -5994197736743037915L;

    @JsonProperty
    private final String id;
    @JsonProperty
    private final boolean desc;
    @JsonProperty
    private final boolean ignoreCase;

    @JsonCreator
    public Sort(@JsonProperty("id") final String id,
                @JsonProperty("desc") final boolean desc,
                @JsonProperty("ignoreCase") final boolean ignoreCase) {
        this.id = id;
        this.desc = desc;
        this.ignoreCase = ignoreCase;
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
}