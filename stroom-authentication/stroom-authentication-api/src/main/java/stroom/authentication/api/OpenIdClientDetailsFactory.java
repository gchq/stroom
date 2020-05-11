package stroom.authentication.api;

public interface OpenIdClientDetailsFactory {

    OAuth2Client getOAuth2Client();

    OAuth2Client getOAuth2Client(final String clientId);
}
