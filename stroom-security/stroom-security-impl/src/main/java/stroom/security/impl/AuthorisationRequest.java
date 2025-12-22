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

package stroom.security.impl;

import stroom.docref.DocRef;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;

@Schema(description = "A request to determine if the user has the requested permission on a 'document'")
public class AuthorisationRequest {

    @JsonProperty
    @Schema(required = true)
    private DocRef docRef;

    @JsonProperty
    @Schema(description = "The permission (e.g. UPDATE) that the user is requesting to use with the document",
            example = "UPDATE",
            required = true)
    private String permission;

    public AuthorisationRequest() {
    }

    public AuthorisationRequest(final DocRef docRef, final String permission) {
        this.docRef = docRef;
        this.permission = permission;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public String getPermission() {
        return permission;
    }
}
