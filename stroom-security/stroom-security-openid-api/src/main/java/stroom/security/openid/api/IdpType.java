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
    INTERNAL_IDP,

    /**
     * An external IDP such as KeyCloak/Cognito
     */
    @JsonPropertyDescription("An external IDP such as KeyCloak/Cognito")
    EXTERNAL_IDP,

    /**
     * Use hard-coded credentials for testing/demo only
     */
    @JsonPropertyDescription("Use hard-coded credentials for testing/demo only")
    TEST_CREDENTIALS,

    /**
     * No Open ID Connect identity provider. This may be used for remote proxies with no OIDC
     * infrastructure that only talk to downstream proxies, forwarding with certificates.
     */
    @JsonPropertyDescription("No Open ID Connect identity provider. This may be used for remote proxies with no OIDC " +
            "infrastructure that only talk to downstream proxies, forwarding with certificates.")
    NO_IDP;

    // Support case-insensitive de-ser, but default to uppercase for ser
    @JsonCreator
    public static IdpType fromString(String type) {
        // Seems to deser as "null" if not set in the yaml
        return (type == null || type.equalsIgnoreCase("null"))
                ? null
                : IdpType.valueOf(type.toUpperCase());
    }
}