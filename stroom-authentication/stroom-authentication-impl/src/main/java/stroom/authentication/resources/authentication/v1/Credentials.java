/*
 *
 *   Copyright 2017 Crown Copyright
 *
 *   Licensed under the Apache License, Version 2.0 (the "License");
 *   you may not use this file except in compliance with the License.
 *   You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *   Unless required by applicable law or agreed to in writing, software
 *   distributed under the License is distributed on an "AS IS" BASIS,
 *   WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *   See the License for the specific language governing permissions and
 *   limitations under the License.
 *
 */

package stroom.authentication.resources.authentication.v1;

import com.fasterxml.jackson.annotation.JsonProperty;

import javax.validation.constraints.NotNull;

public final class Credentials {
    @NotNull
    private String email = "";
    @NotNull
    private String password = "";

    private String requestingClientId = "";

    @JsonProperty("email")
    @NotNull
    public final String getEmail() {
        return this.email;
    }

    public final void setEmail(@NotNull String email) {
        this.email = email;
    }

    @JsonProperty("password")
    @NotNull
    public final String getPassword() {
        return this.password;
    }

    public final void setPassword(@NotNull String password) {
        this.password = password;
    }

    public String getRequestingClientId() {
        return requestingClientId;
    }

    public void setRequestingClientId(String requestingClientId) {
        this.requestingClientId = requestingClientId;
    }
}
