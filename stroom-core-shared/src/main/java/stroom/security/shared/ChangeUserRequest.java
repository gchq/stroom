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

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

@JsonPropertyOrder({"user", "changedLinkedUsers", "changedAppPermissions"})
@JsonInclude(Include.NON_NULL)
public class ChangeUserRequest {
    @JsonProperty
    private final User user;
    @JsonProperty
    private final ChangeSet<User> changedLinkedUsers;
    @JsonProperty
    private final ChangeSet<String> changedAppPermissions;

    @JsonCreator
    public ChangeUserRequest(@JsonProperty("user") final User user,
                             @JsonProperty("changedLinkedUsers") final ChangeSet<User> changedLinkedUsers,
                             @JsonProperty("changedAppPermissions") final ChangeSet<String> changedAppPermissions) {
        this.user = user;
        this.changedLinkedUsers = changedLinkedUsers;
        this.changedAppPermissions = changedAppPermissions;
    }

    public User getUser() {
        return user;
    }

    public ChangeSet<User> getChangedLinkedUsers() {
        return changedLinkedUsers;
    }

    public ChangeSet<String> getChangedAppPermissions() {
        return changedAppPermissions;
    }
}
