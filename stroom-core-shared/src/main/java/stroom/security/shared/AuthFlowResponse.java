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

package stroom.security.shared;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;

/**
 * Response DTO for the SPA authentication flow.
 * <p>
 * If {@code authenticated} is true, the response contains the user's identity
 * information and token expiry. If false, the response contains a
 * {@code redirectUrl} that the SPA should navigate to in order to start the
 * OIDC authentication flow.
 * </p>
 */
@JsonPropertyOrder(alphabetic = true)
@JsonInclude(Include.NON_NULL)
public class AuthFlowResponse {

    @JsonProperty
    private final boolean authenticated;
    @JsonProperty
    private final String subjectId;
    @JsonProperty
    private final String displayName;
    @JsonProperty
    private final String redirectUrl;
    @JsonProperty
    private final Long expiresInSec;

    @JsonCreator
    public AuthFlowResponse(@JsonProperty("authenticated") final boolean authenticated,
                            @JsonProperty("subjectId") final String subjectId,
                            @JsonProperty("displayName") final String displayName,
                            @JsonProperty("redirectUrl") final String redirectUrl,
                            @JsonProperty("expiresInSec") final Long expiresInSec) {
        this.authenticated = authenticated;
        this.subjectId = subjectId;
        this.displayName = displayName;
        this.redirectUrl = redirectUrl;
        this.expiresInSec = expiresInSec;
    }

    /**
     * Create a response indicating the user is authenticated.
     */
    public static AuthFlowResponse authenticated(final String subjectId,
                                                 final String displayName,
                                                 final Long expiresInSec) {
        return new AuthFlowResponse(true, subjectId, displayName, null, expiresInSec);
    }

    /**
     * Create a response indicating the user is not authenticated and must be
     * redirected to the given URL to start the OIDC flow.
     */
    public static AuthFlowResponse unauthenticated(final String redirectUrl) {
        return new AuthFlowResponse(false, null, null, redirectUrl, null);
    }

    public boolean isAuthenticated() {
        return authenticated;
    }

    public String getSubjectId() {
        return subjectId;
    }

    public String getDisplayName() {
        return displayName;
    }

    public String getRedirectUrl() {
        return redirectUrl;
    }

    public Long getExpiresInSec() {
        return expiresInSec;
    }

    @Override
    public String toString() {
        return "AuthFlowResponse{" +
               "authenticated=" + authenticated +
               ", subjectId='" + subjectId + '\'' +
               ", displayName='" + displayName + '\'' +
               ", redirectUrl='" + redirectUrl + '\'' +
               ", expiresInSec=" + expiresInSec +
               '}';
    }
}
