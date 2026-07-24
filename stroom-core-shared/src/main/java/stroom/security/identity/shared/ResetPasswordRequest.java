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

/**
 * A request to set a new password using the token emailed to a user who has forgotten theirs. The user
 * the password is being set for is taken from the token rather than being supplied by the caller.
 */
@JsonInclude(Include.NON_NULL)
public class ResetPasswordRequest {

    @JsonProperty
    private final String token;
    @JsonProperty
    private final String newPassword;
    @JsonProperty
    private final String confirmNewPassword;

    @JsonCreator
    public ResetPasswordRequest(@JsonProperty("token") final String token,
                                @JsonProperty("newPassword") final String newPassword,
                                @JsonProperty("confirmNewPassword") final String confirmNewPassword) {
        this.token = token;
        this.newPassword = newPassword;
        this.confirmNewPassword = confirmNewPassword;
    }

    public String getToken() {
        return token;
    }

    public String getNewPassword() {
        return newPassword;
    }

    public String getConfirmNewPassword() {
        return confirmNewPassword;
    }
}
