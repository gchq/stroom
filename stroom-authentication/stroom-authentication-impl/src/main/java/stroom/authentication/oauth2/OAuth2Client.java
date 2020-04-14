package stroom.authentication.oauth2;

public class OAuth2Client {
    private final String name;
    private final String clientId;
    private final String clientSecret;
    private final String uriPattern;

    public OAuth2Client(final String name,
                        final String clientId,
                        final String clientSecret,
                        final String uriPattern) {
        this.name = name;
        this.clientId = clientId;
        this.clientSecret = clientSecret;
        this.uriPattern = uriPattern;
    }

    public String getName() {
        return name;
    }

    public String getClientId() {
        return clientId;
    }

    public String getClientSecret() {
        return clientSecret;
    }

    public String getUriPattern() {
        return uriPattern;
    }
}
