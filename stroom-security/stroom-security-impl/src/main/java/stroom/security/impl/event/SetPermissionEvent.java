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

package stroom.security.impl.event;

import stroom.docref.DocRef;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(JsonInclude.Include.NON_NULL)
public class SetPermissionEvent implements PermissionChangeEvent {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final DocRef docRef;
    @JsonProperty
    private final DocumentPermission permission;

    @JsonCreator
    public SetPermissionEvent(@JsonProperty("userRef") final UserRef userRef,
                              @JsonProperty("docRef") final DocRef docRef,
                              @JsonProperty("permission") final DocumentPermission permission) {
        this.userRef = userRef;
        this.docRef = docRef;
        this.permission = permission;
    }

    public static void fire(final PermissionChangeEventBus eventBus,
                            final UserRef userRef,
                            final DocRef docRef,
                            final DocumentPermission permission) {
        eventBus.fire(new SetPermissionEvent(userRef, docRef, permission));
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public DocRef getDocRef() {
        return docRef;
    }

    public DocumentPermission getPermission() {
        return permission;
    }
}
