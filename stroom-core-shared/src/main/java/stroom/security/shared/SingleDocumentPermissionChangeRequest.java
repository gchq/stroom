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
import stroom.security.shared.AbstractDocumentPermissionsChange.RemoveAllPermissions;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
public final class SingleDocumentPermissionChangeRequest implements PermissionChangeRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final AbstractDocumentPermissionsChange change;

    @JsonCreator
    public SingleDocumentPermissionChangeRequest(@JsonProperty("docRef") final DocRef docRef,
                                                 @JsonProperty("change") final AbstractDocumentPermissionsChange
                                                         change) {
        Objects.requireNonNull(docRef, "docRef is null");
        Objects.requireNonNull(change, "Request is null");
        this.docRef = docRef;
        this.change = change;
    }

    @SerialisationTestConstructor
    private SingleDocumentPermissionChangeRequest() {
        this(new DocRef("test", "test"), new RemoveAllPermissions());
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public AbstractDocumentPermissionsChange getChange() {
        return change;
    }
}
