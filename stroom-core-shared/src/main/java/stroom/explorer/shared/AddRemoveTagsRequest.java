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

package stroom.explorer.shared;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AddRemoveTagsRequest {

    @JsonProperty
    private final List<DocRef> docRefs;
    @JsonProperty
    private final Set<String> tags;

    @JsonCreator
    public AddRemoveTagsRequest(@JsonProperty("docRefs") final List<DocRef> docRefs,
                                @JsonProperty("tags") final Set<String> tags) {
        this.tags = tags;
        this.docRefs = docRefs;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    public Set<String> getTags() {
        return tags;
    }

    @Override
    public String toString() {
        return "AddRemoveTagsRequest{" +
                "docRefs=" + docRefs +
                ", tags=" + tags +
                '}';
    }
}
