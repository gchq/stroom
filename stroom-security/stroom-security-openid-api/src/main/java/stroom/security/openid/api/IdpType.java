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

package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * The type of identity provider that stroom(-proxy) will use for authentication
 */
public enum IdpType {

    /**
     * Stroom's internal IDP. Not valid for stroom-proxy
     */
    @JsonPropertyDescription("Stroom's internal IDP. Not valid for stroom-proxy.")
    INTERNAL_IDP(false),

    /**
     * An external IDP such as KeyCloak/Cognito
     */
    @JsonPropertyDescription("An external IDP such as KeyCloak/Cognito")
    EXTERNAL_IDP(true),

    /**
     * Use hard-coded credentials for testing/demo only
     */
    @JsonPropertyDescription("Use hard-coded credentials for testing/demo only")
    TEST_CREDENTIALS(false),

    /**
     * No Open ID Connect identity provider. This may be used for remote proxies with no OIDC
     * infrastructure that only talk to downstream proxies or stroom where stroom is using
     * an internal IDP.  Feed status checks can be done by setting apiKey in the feedStatus config.
     */
    @JsonPropertyDescription("No Open ID Connect identity provider. This may be used for remote proxies with no OIDC " +
            "infrastructure that only talk to downstream proxies, forwarding with certificates.")
    NO_IDP(false);

    private final boolean isExternal;

    IdpType(final boolean isExternal) {
        this.isExternal = isExternal;
    }

    // Support case-insensitive de-ser, but default to uppercase for ser
    @JsonCreator
    public static IdpType fromString(final String type) {
        // Seems to deser as "null" if not set in the yaml
        return (type == null || type.equalsIgnoreCase("null"))
                ? null
                : IdpType.valueOf(type.toUpperCase());
    }

    public boolean isExternal() {
        return isExternal;
    }
}
