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

package stroom.security.identity.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;

@JsonInclude(Include.NON_NULL)
public class ChangePasswordResponse {

    @JsonProperty
    private final boolean changeSucceeded;
    @JsonProperty
    private final String message;
    @JsonProperty
    private final boolean forceSignIn;

    @JsonCreator
    public ChangePasswordResponse(@JsonProperty("changeSucceeded") final boolean changeSucceeded,
                                  @JsonProperty("message") final String message,
                                  @JsonProperty("forceSignIn") final boolean forceSignIn) {
        this.changeSucceeded = changeSucceeded;
        this.message = message;
        this.forceSignIn = forceSignIn;
    }

    public boolean isChangeSucceeded() {
        return changeSucceeded;
    }

    public String getMessage() {
        return message;
    }

    public boolean isForceSignIn() {
        return forceSignIn;
    }
}
