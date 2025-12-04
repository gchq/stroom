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

package stroom.pipeline.shared.stepping;

import stroom.pipeline.shared.data.PipelineElement;
import stroom.pipeline.shared.data.PipelineProperty;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FindElementDocRequest {

    @JsonProperty
    private final PipelineElement pipelineElement;
    @JsonProperty
    private final List<PipelineProperty> properties;
    @JsonProperty
    private final String feedName;
    @JsonProperty
    private final String pipelineName;

    @JsonCreator
    public FindElementDocRequest(@JsonProperty("pipelineElement") final PipelineElement pipelineElement,
                                 @JsonProperty("properties") final List<PipelineProperty> properties,
                                 @JsonProperty("feedName") final String feedName,
                                 @JsonProperty("pipelineName") final String pipelineName) {
        this.pipelineElement = pipelineElement;
        this.properties = properties;
        this.feedName = feedName;
        this.pipelineName = pipelineName;
    }

    public PipelineElement getPipelineElement() {
        return pipelineElement;
    }

    public List<PipelineProperty> getProperties() {
        return properties;
    }

    public String getFeedName() {
        return feedName;
    }

    public String getPipelineName() {
        return pipelineName;
    }

    public static Builder builder() {
        return new Builder();
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static final class Builder {

        private PipelineElement pipelineElement;
        private List<PipelineProperty> properties;
        private String feedName;
        private String pipelineName;

        private Builder() {
        }

        private Builder(final FindElementDocRequest findElementDocRequest) {
            this.pipelineElement = findElementDocRequest.pipelineElement;
            this.properties = findElementDocRequest.properties;
            this.feedName = findElementDocRequest.feedName;
            this.pipelineName = findElementDocRequest.pipelineName;
        }

        public Builder pipelineElement(final PipelineElement pipelineElement) {
            this.pipelineElement = pipelineElement;
            return this;
        }

        public Builder properties(final List<PipelineProperty> properties) {
            this.properties = properties;
            return this;
        }

        public Builder feedName(final String feedName) {
            this.feedName = feedName;
            return this;
        }

        public Builder pipelineName(final String pipelineName) {
            this.pipelineName = pipelineName;
            return this;
        }

        public FindElementDocRequest build() {
            return new FindElementDocRequest(pipelineElement, properties, feedName, pipelineName);
        }
    }
}
