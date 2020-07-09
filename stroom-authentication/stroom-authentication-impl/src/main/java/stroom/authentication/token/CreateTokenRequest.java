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

package stroom.authentication.token;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Date;

public class CreateTokenRequest {

    @NotNull
    private String userId;

    // TODO should be an enum really
    @NotNull
    @Pattern(
            regexp = "^user$|^api$|^email_reset$",
            message = "tokenType must be one of: 'user', 'api', 'email_reset'")
    private String tokenType;

    @Nullable
    private Long expiresOnMs;

    @Nullable
    private String comments;

    @Nullable
    private boolean enabled = true;

    // Needed for serialisation
    public CreateTokenRequest() {
    }

    public CreateTokenRequest(final String userId,
                              final String tokenType,
                              final boolean enabled,
                              final String comments) {
        this.userId = userId;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
    }

    public CreateTokenRequest(final String userId,
                              final String tokenType,
                              final boolean enabled,
                              final String comments,
                              final Long expiresOnMs) {
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

    public void setTokenType(String tokenType) {
        this.tokenType = tokenType;
    }

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    @Nullable
    public String getComments() {
        return comments;
    }

    public void setComments(@Nullable String comments) {
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
