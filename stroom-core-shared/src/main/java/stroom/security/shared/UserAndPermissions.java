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

import java.util.Set;

@JsonInclude(Include.NON_NULL)
public class UserAndPermissions {
    @JsonProperty
    private final String userId;
    @JsonProperty
    private final Set<String> appPermissionSet;

    @JsonCreator
    public UserAndPermissions(@JsonProperty("userId") final String userId,
                              @JsonProperty("appPermissionSet") final Set<String> appPermissionSet) {
        this.userId = userId;
        this.appPermissionSet = appPermissionSet;
    }

    public String getUserId() {
        return userId;
    }

    public Set<String> getAppPermissionSet() {
        return appPermissionSet;
    }
}
