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

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.SerialisationTestConstructor;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

import java.util.Objects;
import java.util.Set;

@JsonInclude(Include.NON_NULL)
@JsonPropertyOrder(alphabetic = true)
public class DecorateRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final Set<DocumentPermission> requiredPermissions;

    @JsonCreator
    DecorateRequest(@JsonProperty("docRef") final DocRef docRef,
                    @JsonProperty("requiredPermissions") final Set<DocumentPermission> requiredPermissions) {
        this.docRef = Objects.requireNonNull(docRef);
        this.requiredPermissions = requiredPermissions;
    }

    @SerialisationTestConstructor
    private DecorateRequest() {
        this.docRef = new DocRef("test", "test");
        this.requiredPermissions = null;
    }

    public static DecorateRequest create(final DocRef docRef) {
        return new DecorateRequest(docRef, null);
    }

    public static DecorateRequest createWithPermCheck(final DocRef docRef,
                                                      final Set<DocumentPermission> requiredPermissions) {
        return new DecorateRequest(docRef, requiredPermissions);
    }

    public DocRef getDocRef() {
        return docRef;
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
        final DecorateRequest that = (DecorateRequest) object;
        return Objects.equals(docRef, that.docRef) && Objects.equals(requiredPermissions,
                that.requiredPermissions);
    }

    @Override
    public int hashCode() {
        return Objects.hash(docRef, requiredPermissions);
    }

    @Override
    public String toString() {
        return "DecorateRequest{" +
                "docRef=" + docRef +
                ", requiredPermissions=" + requiredPermissions +
                '}';
    }
}
