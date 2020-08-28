package stroom.security.openid.api;

import java.util.Objects;

public class OpenIdClient {
    private final String name;
    private final String clientId;
    private final String clientSecret;
    private final String uriPattern;

    public OpenIdClient(final String name,
                        final String clientId,
                        final String clientSecret,
                        final String uriPattern) {
        this.name = Objects.requireNonNull(name);
        this.clientId = Objects.requireNonNull(clientId);
        this.clientSecret = Objects.requireNonNull(clientSecret);
        this.uriPattern = Objects.requireNonNull(uriPattern);
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

    @Override
    public String toString() {
        return "OAuth2Client{" +
                "name='" + name + '\'' +
                ", clientId='" + clientId + '\'' +
                ", clientSecret='" + clientSecret + '\'' +
                ", uriPattern='" + uriPattern + '\'' +
                '}';
    }
}
