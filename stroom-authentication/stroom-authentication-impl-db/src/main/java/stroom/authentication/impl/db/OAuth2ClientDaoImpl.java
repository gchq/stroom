package stroom.authentication.impl.db;

import stroom.authentication.api.OAuth2Client;
import stroom.authentication.oauth2.OAuth2ClientDao;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import java.util.Optional;

import static stroom.authentication.impl.db.jooq.tables.OauthClient.OAUTH_CLIENT;

public class OAuth2ClientDaoImpl implements OAuth2ClientDao {
    private AuthDbConnProvider authDbConnProvider;

    @Inject
    OAuth2ClientDaoImpl(final AuthDbConnProvider authDbConnProvider) {
        this.authDbConnProvider = authDbConnProvider;
    }

    @Override
    public void create(final OAuth2Client client) {
        JooqUtil.context(authDbConnProvider, context -> context
                .insertInto(OAUTH_CLIENT)
                .set(OAUTH_CLIENT.NAME, client.getName())
                .set(OAUTH_CLIENT.CLIENT_ID, client.getClientId())
                .set(OAUTH_CLIENT.CLIENT_SECRET, client.getClientSecret())
                .set(OAUTH_CLIENT.URI_PATTERN, client.getUriPattern())
                .onDuplicateKeyIgnore()
                .execute());
    }

    @Override
    public Optional<OAuth2Client> getClientForClientId(final String clientId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(OAUTH_CLIENT)
                .where(OAUTH_CLIENT.CLIENT_ID.eq(clientId))
                .fetchOptional()
                .map(record -> new OAuth2Client(record.getName(), record.getClientId(), record.getClientSecret(), record.getUriPattern())));
    }

    @Override
    public Optional<OAuth2Client> getClientByName(final String name) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(OAUTH_CLIENT)
                .where(OAUTH_CLIENT.NAME.eq(name))
                .fetchOptional()
                .map(record -> new OAuth2Client(record.getName(), record.getClientId(), record.getClientSecret(), record.getUriPattern())));
    }
}
