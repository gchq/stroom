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

package stroom.docstore.impl.db.migration.v710.pipeline.legacy.json;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder({"add, remove"})
public class PipelineElements extends AbstractAddRemove<PipelineElement> {

    @JsonCreator
    public PipelineElements(@JsonProperty("add") final List<PipelineElement> add,
                            @JsonProperty("remove") final List<PipelineElement> remove) {
        super(add, remove);
    }


    // --------------------------------------------------------------------------------


    public static class Builder extends AbstractAddRemoveListBuilder<PipelineElement, PipelineElements, Builder> {

        public Builder() {

        }

        public Builder(final PipelineElements elements) {
            super(elements);
        }

        @Override
        protected Builder self() {
            return this;
        }

        public PipelineElements build() {
            return new PipelineElements(copyAddList(), copyRemoveList());
        }
    }
}
