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

package stroom.pipeline.shared.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import stroom.docref.DocRef;
import stroom.docstore.shared.DocRefUtil;
import stroom.pipeline.shared.PipelineDoc;

@JsonInclude(Include.NON_DEFAULT)
@JsonPropertyOrder({"pipeline"})
public class SourcePipeline {
    @JsonProperty
    private final DocRef pipeline;

    @JsonCreator
    public SourcePipeline(@JsonProperty("pipeline") final PipelineDoc pipeline) {
        this.pipeline = DocRefUtil.create(pipeline);
    }

    public DocRef getPipeline() {
        return pipeline;
    }
}
