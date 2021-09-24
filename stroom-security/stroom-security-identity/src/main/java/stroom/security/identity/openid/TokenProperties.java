package stroom.security.identity.openid;

public class TokenProperties {

    private final String clientId;
    private final String subject;

    public TokenProperties(final String clientId,
                           final String subject) {
        this.clientId = clientId;
        this.subject = subject;
    }

    public String getClientId() {
        return clientId;
    }

    public String getSubject() {
        return subject;
    }
}
