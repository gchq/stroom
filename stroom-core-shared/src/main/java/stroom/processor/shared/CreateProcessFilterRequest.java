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

package stroom.processor.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class CreateProcessFilterRequest {
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final QueryData queryData;
    @JsonProperty
    private final int priority;
    @JsonProperty
    private final boolean autoPriority;
    @JsonProperty
    private final boolean enabled;

    @JsonCreator
    public CreateProcessFilterRequest(@JsonProperty("pipeline") final DocRef pipeline,
                                      @JsonProperty("queryData") final QueryData queryData,
                                      @JsonProperty("priority") final int priority,
                                      @JsonProperty("autoPriority") final boolean autoPriority,
                                      @JsonProperty("enabled") final boolean enabled) {
        this.pipeline = pipeline;
        this.queryData = queryData;
        this.priority = priority;
        this.autoPriority = autoPriority;
        this.enabled = enabled;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public QueryData getQueryData() {
        return queryData;
    }

    public int getPriority() {
        return priority;
    }

    public boolean isAutoPriority() {
        return autoPriority;
    }

    public boolean isEnabled() {
        return enabled;
    }
}
