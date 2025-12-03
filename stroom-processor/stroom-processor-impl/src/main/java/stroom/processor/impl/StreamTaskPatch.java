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

package stroom.processor.impl;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class StreamTaskPatch {

    @JsonProperty
    private String op;
    @JsonProperty
    private String path;
    @JsonProperty
    private String value;

    @JsonCreator
    public StreamTaskPatch(@JsonProperty("op") final String op,
                           @JsonProperty("path") final String path,
                           @JsonProperty("value") final String value) {
        this.op = op;
        this.path = path;
        this.value = value;
    }

    public String getOp() {
        return op;
    }

    public StreamTaskPatch setOp(final String op) {
        this.op = op;
        return this;
    }

    public String getPath() {
        return path;
    }

    public StreamTaskPatch setPath(final String path) {
        this.path = path;
        return this;
    }

    public String getValue() {
        return value;
    }

    public StreamTaskPatch setValue(final String value) {
        this.value = value;
        return this;
    }
}
