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

import stroom.util.shared.UserName;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class UserAndPermissions {

    @JsonProperty
    private final UserName userName;
    @JsonProperty
    private final Set<String> permissions;

    @JsonCreator
    public UserAndPermissions(@JsonProperty("userName") final UserName userName,
                              @JsonProperty("permissions") final Set<String> permissions) {
        this.userName = userName;
        this.permissions = permissions;
    }

    /**
     * @return
     */
    public UserName getUserName() {
        return userName;
    }

    public Set<String> getPermissions() {
        return permissions;
    }

    @Override
    public String toString() {
        return "UserAndPermissions{" +
                "userId='" + userName + '\'' +
                ", permissions=" + permissions +
                '}';
    }
}
