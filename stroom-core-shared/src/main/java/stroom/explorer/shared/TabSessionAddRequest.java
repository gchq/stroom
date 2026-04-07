/*
 * Copyright 2016-2026 Crown Copyright
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
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public class TabSessionAddRequest {

    @JsonProperty
    private final String name;

    @JsonProperty
    private final List<DocRef> docRefs;

    @JsonCreator
    public TabSessionAddRequest(@JsonProperty("name") final String name,
                                @JsonProperty("docRefs") final List<DocRef> docRefs) {
        this.name = name;
        this.docRefs = docRefs;
    }

    public String getName() {
        return name;
    }

    public List<DocRef> getDocRefs() {
        return docRefs;
    }

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final TabSessionAddRequest that = (TabSessionAddRequest) o;
        return Objects.equals(name, that.name)
               && Objects.equals(docRefs, that.docRefs);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, docRefs);
    }

    @Override
    public String toString() {
        return "TabSessionAddRequest{" +
               ", name='" + name + '\'' +
               ", docRefs=" + docRefs +
               '}';
    }
}
