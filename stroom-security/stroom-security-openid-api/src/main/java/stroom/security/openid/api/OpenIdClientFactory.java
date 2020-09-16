package stroom.security.openid.api;

public interface OpenIdClientFactory {
    OpenIdClient getClient();

    OpenIdClient getClient(final String clientId);
}
