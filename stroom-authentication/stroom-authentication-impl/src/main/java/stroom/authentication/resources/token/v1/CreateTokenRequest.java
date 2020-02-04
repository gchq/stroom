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

package stroom.authentication.resources.token.v1;

import io.swagger.annotations.ApiModel;
import io.swagger.annotations.ApiModelProperty;

import javax.annotation.Nullable;
import javax.validation.constraints.NotNull;
import javax.validation.constraints.Pattern;
import java.util.Date;
import java.util.Optional;

@ApiModel(description = "A request to create a token.")
public class CreateTokenRequest {

    @NotNull
    @ApiModelProperty(value = "The email of the user whom the token is for.", required = true)
    private String userEmail;

    @NotNull
    @Pattern(
            regexp = "^user$|^api$|^email_reset$",
            message = "tokenType must be one of: 'user', 'api', 'email_reset'")
    @ApiModelProperty(value = "The type of token to create: e.g. user, api, or email_reset.", required = true)
    private String tokenType;

    @Nullable
    @ApiModelProperty(value = "The expiry date for an API key.")
    private Date expiryDate;

    @Nullable
    @ApiModelProperty(value = "Comments about the token.")
    private String comments;

    @Nullable
    @ApiModelProperty(value = "Whether or not the new token should be enabled.")
    private boolean enabled = true;

    // Needed for serialisation
    public CreateTokenRequest() {
    }

    public CreateTokenRequest(String userEmail, String tokenType, boolean enabled, String comments) {
        this.userEmail = userEmail;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
    }

    public CreateTokenRequest(String userEmail, String tokenType, boolean enabled, String comments, Date expiryDate) {
        this.userEmail = userEmail;
        this.tokenType = tokenType;
        this.enabled = enabled;
        this.comments = comments;
        this.expiryDate = expiryDate;
    }

    public Optional<Token.TokenType> getParsedTokenType() {
        switch (tokenType.toLowerCase()) {
            case "api":
                return Optional.of(Token.TokenType.API);
            case "user":
                return Optional.of(Token.TokenType.USER);
            case "email_reset":
                return Optional.of(Token.TokenType.EMAIL_RESET);
            default:
                return Optional.empty();
        }
    }

    @Nullable
    public String getUserEmail() {
        return userEmail;
    }

    @Nullable
    public String getTokenType() {
        return tokenType;
    }

    public boolean isEnabled() {
        return enabled;
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
}
