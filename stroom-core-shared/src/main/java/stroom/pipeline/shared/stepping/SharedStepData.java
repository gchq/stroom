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

package stroom.pipeline.shared.stepping;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonIgnore;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import stroom.pipeline.shared.SharedElementData;
import stroom.pipeline.shared.SourceLocation;

import java.util.Map;

@JsonInclude(Include.NON_DEFAULT)
public class SharedStepData {
    @JsonProperty
    private final SourceLocation sourceLocation;
    @JsonProperty
    private final Map<String, SharedElementData> elementMap;

    @JsonCreator
    public SharedStepData(@JsonProperty("sourceLocation") final SourceLocation sourceLocation,
                          @JsonProperty("elementMap") final Map<String, SharedElementData> elementMap) {
        this.sourceLocation = sourceLocation;
        this.elementMap = elementMap;
    }

    public SourceLocation getSourceLocation() {
        return sourceLocation;
    }

    public Map<String, SharedElementData> getElementMap() {
        return elementMap;
    }

    public SharedElementData getElementData(final String elementId) {
        return elementMap.get(elementId);
    }
}
