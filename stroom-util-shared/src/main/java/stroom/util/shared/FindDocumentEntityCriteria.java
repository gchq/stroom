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

package stroom.util.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public abstract class FindDocumentEntityCriteria extends FindNamedEntityCriteria
        implements Copyable<FindDocumentEntityCriteria> {

    @JsonProperty
    private String requiredPermission;

    public FindDocumentEntityCriteria() {
    }

    public FindDocumentEntityCriteria(final String name) {
        super(name);
    }

    @JsonCreator
    public FindDocumentEntityCriteria(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                      @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                      @JsonProperty("name") final StringCriteria name,
                                      @JsonProperty("requiredPermission") final String requiredPermission) {
        super(pageRequest, sortList, name);
        this.requiredPermission = requiredPermission;
    }

    public String getRequiredPermission() {
        return requiredPermission;
    }

    public void setRequiredPermission(final String requiredPermission) {
        this.requiredPermission = requiredPermission;
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
        final FindDocumentEntityCriteria that = (FindDocumentEntityCriteria) o;
        return Objects.equals(requiredPermission, that.requiredPermission);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), requiredPermission);
    }

    @Override
    public void copyFrom(final FindDocumentEntityCriteria other) {
        this.requiredPermission = other.requiredPermission;
        super.copyFrom(other);
    }
}
