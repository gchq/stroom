/*
 * Copyright 2016 Crown Copyright
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
import stroom.explorer.shared.AdvancedDocumentFindRequest;
import stroom.explorer.shared.DocumentType;
import stroom.query.api.v2.ExpressionOperator;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ChangeDocumentPermissionsRequest {

    @JsonProperty
    private final ExpressionOperator expression;
    @JsonProperty
    private final DocumentPermissionChange documentPermissionChange;
    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final DocumentType docType;
    @JsonProperty
    private final DocumentPermission permission;

    @JsonCreator
    public ChangeDocumentPermissionsRequest(@JsonProperty("expression") final ExpressionOperator expression,
                                            @JsonProperty("documentPermissionChange") final
                                            DocumentPermissionChange documentPermissionChange,
                                            @JsonProperty("userRef") final UserRef userRef,
                                            @JsonProperty("docRef") final DocRef docRef,
                                            @JsonProperty("docType") final DocumentType docType,
                                            @JsonProperty("permission") final DocumentPermission permission) {
        this.expression = expression;
        this.documentPermissionChange = documentPermissionChange;
        this.userRef = userRef;
        this.docRef = docRef;
        this.docType = docType;
        this.permission = permission;
    }

    public ExpressionOperator getExpression() {
        return expression;
    }

    public DocumentPermissionChange getDocumentPermissionChange() {
        return documentPermissionChange;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public DocumentType getDocType() {
        return docType;
    }

    public DocumentPermission getPermission() {
        return permission;
    }
}
