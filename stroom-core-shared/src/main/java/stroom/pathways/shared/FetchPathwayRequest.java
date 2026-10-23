/*
 * Copyright 2026 Crown Copyright
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

package stroom.pathways.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

/**
 * Asks for one learnt pathway in full, by the operation name it is keyed on.
 *
 * <p>The name is also what decides its shard, so this needs no more than the two fields to find it.
 */
@JsonInclude(Include.NON_NULL)
public class FetchPathwayRequest {

    @JsonProperty
    private final DocRef pathwaysDocRef;
    @JsonProperty
    private final String name;

    @JsonCreator
    public FetchPathwayRequest(@JsonProperty("pathwaysDocRef") final DocRef pathwaysDocRef,
                               @JsonProperty("name") final String name) {
        this.pathwaysDocRef = pathwaysDocRef;
        this.name = name;
    }

    public DocRef getPathwaysDocRef() {
        return pathwaysDocRef;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return "FetchPathwayRequest{pathwaysDocRef=" + pathwaysDocRef + ", name='" + name + "'}";
    }
}
