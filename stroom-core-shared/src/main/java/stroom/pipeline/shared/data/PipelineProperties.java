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

package stroom.pipeline.shared.data;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"add", "remove"})
public class PipelineProperties extends AbstractAddRemove<PipelineProperty> {

    @JsonCreator
    public PipelineProperties(@JsonProperty("add") final List<PipelineProperty> add,
                              @JsonProperty("remove") final List<PipelineProperty> remove) {
        super(add, remove);
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractAddRemoveListBuilder<PipelineProperty, PipelineProperties, Builder> {

        public Builder() {

        }

        public Builder(final PipelineProperties properties) {
            super(properties);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PipelineProperties build() {
            return new PipelineProperties(copyAddList(), copyRemoveList());
        }
    }
}
