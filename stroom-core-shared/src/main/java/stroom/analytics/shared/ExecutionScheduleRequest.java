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

package stroom.analytics.shared;

import stroom.docref.DocRef;
import stroom.util.shared.BaseCriteria;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.List;
import java.util.Objects;

@JsonPropertyOrder({
        "pageRequest",
        "sortList",
        "ownerDocRef",
        "nodeName",
        "enabled"
})
@JsonInclude(Include.NON_NULL)
public class ExecutionScheduleRequest extends BaseCriteria {

    @JsonProperty
    private final DocRef ownerDocRef;
    @JsonProperty
    private final String nodeName;
    @JsonProperty
    private final Boolean enabled;

    @JsonCreator
    public ExecutionScheduleRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                    @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                    @JsonProperty("ownerDocRef") final DocRef ownerDocRef,
                                    @JsonProperty("nodeName") final String nodeName,
                                    @JsonProperty("enabled") final Boolean enabled) {
        super(pageRequest, sortList);
        this.ownerDocRef = ownerDocRef;
        this.nodeName = nodeName;
        this.enabled = enabled;
    }

    public DocRef getOwnerDocRef() {
        return ownerDocRef;
    }

    public String getNodeName() {
        return nodeName;
    }

    public Boolean getEnabled() {
        return enabled;
    }

    @Override
    public boolean equals(final Object o) {
        if (this == o) {
            return true;
        }
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        if (!super.equals(o)) {
            return false;
        }
        final ExecutionScheduleRequest that = (ExecutionScheduleRequest) o;
        return Objects.equals(ownerDocRef, that.ownerDocRef) && Objects.equals(nodeName,
                that.nodeName) && Objects.equals(enabled, that.enabled);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), ownerDocRef, nodeName, enabled);
    }

    @Override
    public String toString() {
        return "ExecutionScheduleRequest{" +
               "ownerDocRef=" + ownerDocRef +
               ", nodeName=" + nodeName +
               ", enabled=" + enabled +
               '}';
    }

    public Builder copy() {
        return new Builder(this);
    }

    public static Builder builder() {
        return new Builder();
    }

    public static class Builder {

        private PageRequest pageRequest;
        private List<CriteriaFieldSort> sortList;
        private DocRef ownerDocRef;
        private String nodeName;
        private Boolean enabled;

        private Builder() {
        }

        private Builder(final ExecutionScheduleRequest request) {
            this.pageRequest = request.getPageRequest();
            this.sortList = request.getSortList();
            this.ownerDocRef = request.ownerDocRef;
            this.nodeName = request.nodeName;
            this.enabled = request.enabled;
        }


        public Builder pageRequest(final PageRequest pageRequest) {
            this.pageRequest = pageRequest;
            return this;
        }

        public Builder sortList(final List<CriteriaFieldSort> sortList) {
            this.sortList = sortList;
            return this;
        }

        public Builder ownerDocRef(final DocRef ownerDocRef) {
            this.ownerDocRef = ownerDocRef;
            return this;
        }

        public Builder nodeName(final String nodeName) {
            this.nodeName = nodeName;
            return this;
        }

        public Builder enabled(final Boolean enabled) {
            this.enabled = enabled;
            return this;
        }

        public ExecutionScheduleRequest build() {
            return new ExecutionScheduleRequest(
                    pageRequest,
                    sortList,
                    ownerDocRef,
                    nodeName,
                    enabled);
        }
    }
}
