/*
 * Copyright 2016 Crown Copyright
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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.docref.DocRef;

@JsonInclude(Include.NON_DEFAULT)
public class SavePipelineXmlRequest {
    @JsonProperty
    private final DocRef pipeline;
    @JsonProperty
    private final String xml;

    @JsonCreator
    public SavePipelineXmlRequest(@JsonProperty("pipeline") final DocRef pipeline,
                                  @JsonProperty("xml") final String xml) {
        this.pipeline = pipeline;
        this.xml = xml;
    }

    public DocRef getPipeline() {
        return pipeline;
    }

    public String getXml() {
        return xml;
    }
}
