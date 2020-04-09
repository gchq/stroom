package stroom.authentication.oauth2;

public interface OAuth2ClientDao {
    OAuth2Client getClient(String clientId);
}
