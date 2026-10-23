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
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

/**
 * The changes made to one learnt pathway, newest first unless the grid asks otherwise.
 */
@JsonInclude(Include.NON_NULL)
public class FindPathwayMutationCriteria extends BaseCriteria {

    @JsonProperty
    private final DocRef pathwaysDocRef;
    @JsonProperty
    private final String pathwayName;

    @JsonCreator
    public FindPathwayMutationCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                       @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                       @JsonProperty("pathwaysDocRef") final DocRef pathwaysDocRef,
                                       @JsonProperty("pathwayName") final String pathwayName) {
        super(pageRequest, sortList);
        this.pathwaysDocRef = pathwaysDocRef;
        this.pathwayName = pathwayName;
    }

    public DocRef getPathwaysDocRef() {
        return pathwaysDocRef;
    }

    public String getPathwayName() {
        return pathwayName;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass() || !super.equals(o)) {
            return false;
        }
        final FindPathwayMutationCriteria that = (FindPathwayMutationCriteria) o;
        return Objects.equals(pathwaysDocRef, that.pathwaysDocRef)
               && Objects.equals(pathwayName, that.pathwayName);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), pathwaysDocRef, pathwayName);
    }

    @Override
    public String toString() {
        return "FindPathwayMutationCriteria{pathwaysDocRef=" + pathwaysDocRef
               + ", pathwayName='" + pathwayName + "'}";
    }
}
