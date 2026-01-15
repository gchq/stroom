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
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class DocumentUserPermissionsRequest {

    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final UserRef userRef;

    @JsonCreator
    public DocumentUserPermissionsRequest(@JsonProperty("docRef") final DocRef docRef,
                                          @JsonProperty("userRef") final UserRef userRef) {
        this.docRef = docRef;
        this.userRef = userRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public UserRef getUserRef() {
        return userRef;
    }
}
