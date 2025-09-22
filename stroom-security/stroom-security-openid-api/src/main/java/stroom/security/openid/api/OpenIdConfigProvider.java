package stroom.security.openid.api;

import java.util.List;

public interface OpenIdConfigProvider {

    /**
     * @return True if at least one IDP is configured and enabled.
     */
    boolean hasIdpConfig();

    /**
     * If true, use hard-coded credentials for testing/demo only.
     * NOT for production use as it will use credentials/secrets that are publicly available.
     */
    boolean isUseTestCredentials();

    /**
     * @return A list of all enabled IDP configurations
     */
    List<OpenIdConfiguration> getAll();

    /**
     * @return A list of all enabled IDP configurations with type idpType
     */
    List<OpenIdConfiguration> getByType(final IdpType idpType);

    /**
     * @return The IDP configuration matching name idpName (case in-sensitive) or null if not found.
     */
    OpenIdConfiguration getByName(final String idpName);

    /**
     * @return The internal IDP configuration.
     * Will return null for stroom-proxy as that has no internal IDP.
     */
    OpenIdConfiguration getInternal();
}
