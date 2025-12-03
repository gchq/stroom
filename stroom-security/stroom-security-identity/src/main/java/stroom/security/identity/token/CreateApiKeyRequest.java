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

package stroom.security.identity.token;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.annotation.Nullable;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;

@JsonInclude(JsonInclude.Include.NON_NULL)
@Deprecated // Keeping it else it breaks the React code
public class CreateApiKeyRequest {

    @NotNull
    @JsonProperty
    private String userId;

    // TODO should be an enum really
    @NotNull
    @Pattern(
            regexp = "^user$|^api$|^email_reset$",
            message = "tokenType must be one of: 'user', 'api', 'email_reset'")
    @JsonProperty
    private String tokenType;

    @Nullable
    @JsonProperty
    private Long expiresOnMs;

    @Nullable
    @JsonProperty
    private String comments;

    @Nullable
    @JsonProperty
    private boolean enabled = true;

    // Needed for serialisation
    public CreateApiKeyRequest() {
    }

    public CreateApiKeyRequest(final String userId,
                               final String tokenType,
                               final boolean enabled,
                               final String comments) {
        this.userId = userId;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
    }

    @JsonCreator
    public CreateApiKeyRequest(@JsonProperty("userId") final String userId,
                               @JsonProperty("tokenType") final String tokenType,
                               @JsonProperty("enabled") final boolean enabled,
                               @JsonProperty("comments") final String comments,
                               @JsonProperty("expiresOnMs") final Long expiresOnMs) {
        this.userId = userId;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
        this.expiresOnMs = expiresOnMs;
    }

    public String getUserId() {
        return userId;
    }

    public void setUserId(final String userId) {
        this.userId = userId;
    }

    @Nullable
    public String getTokenType() {
        return tokenType;
    }

    public void setTokenType(final String tokenType) {
        this.tokenType = tokenType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(final boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public String getComments() {
        return comments;
    }

    public void setComments(@Nullable final String comments) {
        this.comments = comments;
    }

    @Nullable
    public Long getExpiresOnMs() {
        return expiresOnMs;
    }

    public void setExpiresOnMs(@Nullable final Long expiresOnMs) {
        this.expiresOnMs = expiresOnMs;
    }
}
