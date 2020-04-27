package stroom.security.impl;

public interface OpenIdClientDetailsFactory {
//    String getClientId();
//
//    String getClientSecret();

    OAuth2Client getOAuth2Client();

    OAuth2Client getOAuth2Client(final String clientId);

}
