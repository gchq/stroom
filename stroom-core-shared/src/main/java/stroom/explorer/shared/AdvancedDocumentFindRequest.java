/*
 * Copyright 2024 Crown Copyright
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

import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AdvancedDocumentFindRequest extends ExpressionCriteria {

    @JsonProperty
    private final Set<DocumentPermission> requiredPermissions;
    @JsonProperty
    private final String quickFilter;

    public AdvancedDocumentFindRequest(final PageRequest pageRequest,
                                       final List<CriteriaFieldSort> sortList,
                                       final ExpressionOperator expression,
                                       final Set<DocumentPermission>
                                               requiredPermissions) {
        this(pageRequest, sortList, expression, requiredPermissions, null);
    }

    @JsonCreator
    public AdvancedDocumentFindRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                       @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                       @JsonProperty("expression") final ExpressionOperator expression,
                                       @JsonProperty("requiredPermissions") final Set<DocumentPermission>
                                               requiredPermissions,
                                       @JsonProperty("quickFilter") final String quickFilter) {
        super(pageRequest, sortList, expression);
        this.requiredPermissions = requiredPermissions;
        this.quickFilter = quickFilter;
    }

    public Set<DocumentPermission> getRequiredPermissions() {
        return requiredPermissions;
    }

    @Override
    public boolean equals(final Object object) {
        if (this == object) {
            return true;
        }
        if (object == null || getClass() != object.getClass()) {
            return false;
        }
        if (!super.equals(object)) {
            return false;
        }
        final AdvancedDocumentFindRequest that = (AdvancedDocumentFindRequest) object;
        return Objects.equals(requiredPermissions, that.requiredPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), requiredPermissions);
    }


    // --------------------------------------------------------------------------------


    public String getQuickFilter() {
        return quickFilter;
    }

    public static class Builder extends ExpressionCriteriaBuilder<AdvancedDocumentFindRequest, Builder> {

        private String quickFilter;

        /**
         * @see AdvancedDocumentFindRequest#getQuickFilter()
         */
        public Builder quickFilter(final String quickFilter) {
            this.quickFilter = quickFilter;
            return self();
        }


        private Set<DocumentPermission> requiredPermissions;

        public Builder() {

        }

        public Builder(final AdvancedDocumentFindRequest expressionCriteria) {
            super(expressionCriteria);
        }

        public Builder requiredPermissions(final Set<DocumentPermission> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AdvancedDocumentFindRequest build() {
            return new AdvancedDocumentFindRequest(pageRequest, sortList, expression, requiredPermissions,
                    quickFilter);
        }
    }
}
