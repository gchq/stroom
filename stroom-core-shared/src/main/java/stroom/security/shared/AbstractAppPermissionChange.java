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

import stroom.security.shared.AbstractAppPermissionChange.AddAppPermission;
import stroom.security.shared.AbstractAppPermissionChange.RemoveAppPermission;
import stroom.util.shared.SerialisationTestConstructor;
import stroom.util.shared.UserRef;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSubTypes;
import com.fasterxml.jackson.annotation.JsonTypeInfo;

import java.util.Objects;

@JsonInclude(Include.NON_NULL)
@JsonTypeInfo(
        use = JsonTypeInfo.Id.NAME,
        property = "type"
)
@JsonSubTypes({
        @JsonSubTypes.Type(value = AddAppPermission.class, name = "AddAppPermission"),
        @JsonSubTypes.Type(value = RemoveAppPermission.class, name = "RemoveAppPermission"),
})
public abstract sealed class AbstractAppPermissionChange permits AddAppPermission, RemoveAppPermission {

    @JsonProperty
    private final UserRef userRef;
    @JsonProperty
    private final AppPermission permission;

    @JsonCreator
    public AbstractAppPermissionChange(@JsonProperty("userRef") final UserRef userRef,
                                       @JsonProperty("permission") final AppPermission permission) {
        Objects.requireNonNull(userRef, "Null user ref");
        Objects.requireNonNull(permission, "Null permission");
        this.userRef = userRef;
        this.permission = permission;
    }

    public UserRef getUserRef() {
        return userRef;
    }

    public AppPermission getPermission() {
        return permission;
    }

    @JsonInclude(Include.NON_NULL)
    public static final class AddAppPermission extends AbstractAppPermissionChange {


        @JsonCreator
        public AddAppPermission(@JsonProperty("userRef") final UserRef userRef,
                                @JsonProperty("permission") final AppPermission permission) {
            super(userRef, permission);
        }

        @SerialisationTestConstructor
        private AddAppPermission() {
            this(UserRef.builder().build(), AppPermission.ADMINISTRATOR);
        }
    }

    @JsonInclude(Include.NON_NULL)
    public static final class RemoveAppPermission extends AbstractAppPermissionChange {

        @JsonCreator
        public RemoveAppPermission(@JsonProperty("userRef") final UserRef userRef,
                                   @JsonProperty("permission") final AppPermission permission) {
            super(userRef, permission);
        }

        @SerialisationTestConstructor
        private RemoveAppPermission() {
            this(UserRef.builder().build(), AppPermission.ADMINISTRATOR);
        }
    }
}
