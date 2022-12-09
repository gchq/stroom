package stroom.security.openid.api;

import com.fasterxml.jackson.annotation.JsonCreator;

/**
 * The type of identity provider that stroom(-proxy) will use for authentication
 */
public enum IdpType {

    /**
     * Stroom's internal IDP. Not valid for stroom-proxy
     */
    INTERNAL,

    /**
     * An external IDP such as KeyCloak/Cognito
     */
    EXTERNAL,

    /**
     * Use hard-coded credentials for testing/demo only
     */
    TEST;

    // Support case-insensitive de-ser, but default to uppercase for ser
    @JsonCreator
    public static IdpType fromString(String type) {
        return type == null
                ? null
                : IdpType.valueOf(type.toUpperCase());
    }
}
