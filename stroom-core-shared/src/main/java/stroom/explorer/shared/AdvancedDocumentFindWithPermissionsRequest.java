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

import stroom.query.api.ExpressionOperator;
import stroom.security.shared.DocumentPermission;
import stroom.security.shared.PermissionShowLevel;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;
import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class AdvancedDocumentFindWithPermissionsRequest extends AdvancedDocumentFindRequest {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final PermissionShowLevel showLevel;

    @JsonCreator
    public AdvancedDocumentFindWithPermissionsRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                                      @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                                      @JsonProperty("expression") final ExpressionOperator expression,
                                                      @JsonProperty("requiredPermissions") final Set<DocumentPermission>
                                                              requiredPermissions,
                                                      @JsonProperty("userRef") final UserRef userRef,
                                                      @JsonProperty("showLevel") final PermissionShowLevel showLevel) {
        super(pageRequest, sortList, expression, requiredPermissions);
        this.userRef = userRef;
        this.showLevel = showLevel;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public PermissionShowLevel getShowLevel() {
        return showLevel;
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
        final AdvancedDocumentFindWithPermissionsRequest that = (AdvancedDocumentFindWithPermissionsRequest) object;
        return showLevel == that.showLevel && Objects.equals(userRef, that.userRef);
    }

    @Override
    public int hashCode() {
        return Objects.hash(super.hashCode(), userRef, showLevel);
    }

    // --------------------------------------------------------------------------------


    public static class Builder extends ExpressionCriteriaBuilder<AdvancedDocumentFindWithPermissionsRequest, Builder> {

        private Set<DocumentPermission> requiredPermissions;
        private UserRef userRef;
        private PermissionShowLevel showLevel;

        public Builder() {

        }

        public Builder(final AdvancedDocumentFindWithPermissionsRequest expressionCriteria) {
            super(expressionCriteria);
            this.requiredPermissions = expressionCriteria.getRequiredPermissions();
            this.userRef = expressionCriteria.userRef;
            this.showLevel = expressionCriteria.showLevel;
        }

        public Builder requiredPermissions(final Set<DocumentPermission> requiredPermissions) {
            this.requiredPermissions = requiredPermissions;
            return self();
        }

        public Builder userRef(final UserRef userRef) {
            this.userRef = userRef;
            return self();
        }

        public Builder showLevel(final PermissionShowLevel showLevel) {
            this.showLevel = showLevel;
            return self();
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public AdvancedDocumentFindWithPermissionsRequest build() {
            return new AdvancedDocumentFindWithPermissionsRequest(
                    pageRequest,
                    sortList,
                    expression,
                    requiredPermissions,
                    userRef,
                    showLevel);
        }
    }
}
