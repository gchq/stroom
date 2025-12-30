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

package stroom.security.shared;

import stroom.docref.DocRef;
import stroom.entity.shared.ExpressionCriteria;
import stroom.query.api.ExpressionOperator;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.List;

@JsonInclude(Include.NON_NULL)
public class FetchDocumentUserPermissionsRequest extends ExpressionCriteria {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final PermissionShowLevel showLevel;

    @JsonCreator
    public FetchDocumentUserPermissionsRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                               @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                               @JsonProperty("expression") final ExpressionOperator expression,
                                               @JsonProperty("docRef") final DocRef docRef,
                                               @JsonProperty("userRef") final UserRef userRef,
                                               @JsonProperty("showLevel") final PermissionShowLevel showLevel) {
        super(pageRequest, sortList, expression);
        this.docRef = docRef;
        this.userRef = userRef;
        this.showLevel = showLevel;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public PermissionShowLevel getShowLevel() {
        return showLevel;
    }

    public static class Builder
            extends ExpressionCriteriaBuilder<FetchDocumentUserPermissionsRequest, Builder> {

        private DocRef docRef;
        private UserRef userRef;
        private PermissionShowLevel showLevel;

        public Builder() {

        }

        public Builder(final FetchDocumentUserPermissionsRequest request) {
            super(request);
            this.docRef = request.docRef;
            this.userRef = request.userRef;
            this.showLevel = request.showLevel;
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
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

        public PermissionShowLevel getShowLevel() {
            return showLevel;
        }

        @Override
        protected Builder self() {
            return this;
        }

        @Override
        public FetchDocumentUserPermissionsRequest build() {
            return new FetchDocumentUserPermissionsRequest(
                    pageRequest,
                    sortList,
                    expression,
                    docRef,
                    userRef,
                    showLevel);
        }
    }
}
