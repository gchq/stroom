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

package stroom.pipeline.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class FetchPipelineJsonResponse {

    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final String json;

    @JsonCreator
    public FetchPipelineJsonResponse(@JsonProperty("pipeline") final DocRef pipeline,
                                     @JsonProperty("json") final String json) {
        this.pipeline = pipeline;
        this.json = json;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public String getJson() {
        return json;
    }
}
