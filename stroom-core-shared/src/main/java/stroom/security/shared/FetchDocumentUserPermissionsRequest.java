/*
 * Copyright 2017 Crown Copyright
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
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.CriteriaFieldSort;
import stroom.util.shared.PageRequest;

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
    private final boolean allUsers;

    @JsonCreator
    public FetchDocumentUserPermissionsRequest(@JsonProperty("pageRequest") final PageRequest pageRequest,
                                               @JsonProperty("sortList") final List<CriteriaFieldSort> sortList,
                                               @JsonProperty("expression") final ExpressionOperator expression,
                                               @JsonProperty("docRef") final DocRef docRef,
                                               @JsonProperty("allUsers") boolean allUsers) {
        super(pageRequest, sortList, expression);
        this.docRef = docRef;
        this.allUsers = allUsers;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public boolean isAllUsers() {
        return allUsers;
    }

    public static class Builder
            extends ExpressionCriteria.AbstractBuilder<FetchDocumentUserPermissionsRequest, Builder> {

        private DocRef docRef;
        private boolean allUsers;

        public Builder() {

        }

        public Builder(final FetchDocumentUserPermissionsRequest request) {
            super(request);
            this.docRef = request.docRef;
            this.allUsers = request.allUsers;
        }

        public Builder docRef(final DocRef docRef) {
            this.docRef = docRef;
            return this;
        }

        public Builder allUsers(final boolean allUsers) {
            this.allUsers = allUsers;
            return this;
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
                    allUsers);
        }
    }
}
