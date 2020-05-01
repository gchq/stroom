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
    private String clientId;

    @NotNull
    private String userEmail;

    // TODO should be an enum really
    @NotNull
    @Pattern(
            regexp = "^user$|^api$|^email_reset$",
            message = "tokenType must be one of: 'user', 'api', 'email_reset'")
    private String tokenType;

    // TODO why are we using java.util.Date
    @Nullable
    private Date expiryDate;

    @Nullable
    private String comments;

    @Nullable
    private boolean enabled = true;

    // Needed for serialisation
    public CreateTokenRequest() {
    }

    public CreateTokenRequest(final String clientId,
                              final String userEmail,
                              final String tokenType,
                              final boolean enabled,
                              final String comments) {
        this.clientId = clientId;
        this.userEmail = userEmail;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
    }

    public CreateTokenRequest(final String clientId,
                              final String userEmail,
                              final String tokenType,
                              final boolean enabled,
                              final String comments,
                              final Date expiryDate) {
        this.clientId = clientId;
        this.userEmail = userEmail;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
        this.expiryDate = expiryDate;
    }

    public String getClientId() {
        return clientId;
    }

    public void setClientId(final String clientId) {
        this.clientId = clientId;
    }

    @Nullable
    public String getUserEmail() {
        return userEmail;
    }

    public void setUserEmail(String userEmail) {
        this.userEmail = userEmail;
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
    public Date getExpiryDate() {
        return expiryDate;
    }

    public void setExpiryDate(@Nullable Date expiryDate) {
        this.expiryDate = expiryDate;
    }
}
