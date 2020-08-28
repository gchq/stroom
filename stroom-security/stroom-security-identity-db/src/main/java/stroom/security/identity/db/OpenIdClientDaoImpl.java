package stroom.security.identity.db;

import stroom.security.identity.db.jooq.tables.OauthClient;
import stroom.security.openid.api.OpenIdClient;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.db.util.JooqUtil;

import javax.inject.Inject;
import java.util.Optional;

public class OpenIdClientDaoImpl implements OpenIdClientDao {
    private AuthDbConnProvider authDbConnProvider;

    @Inject
    OpenIdClientDaoImpl(final AuthDbConnProvider authDbConnProvider) {
        this.authDbConnProvider = authDbConnProvider;
    }

    @Override
    public void create(final OpenIdClient client) {
        JooqUtil.context(authDbConnProvider, context -> context
                .insertInto(OauthClient.OAUTH_CLIENT)
                .set(OauthClient.OAUTH_CLIENT.NAME, client.getName())
                .set(OauthClient.OAUTH_CLIENT.CLIENT_ID, client.getClientId())
                .set(OauthClient.OAUTH_CLIENT.CLIENT_SECRET, client.getClientSecret())
                .set(OauthClient.OAUTH_CLIENT.URI_PATTERN, client.getUriPattern())
                .onDuplicateKeyIgnore()
                .execute());
    }

    @Override
    public Optional<OpenIdClient> getClientForClientId(final String clientId) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(OauthClient.OAUTH_CLIENT)
                .where(OauthClient.OAUTH_CLIENT.CLIENT_ID.eq(clientId))
                .fetchOptional()
                .map(record -> new OpenIdClient(record.getName(), record.getClientId(), record.getClientSecret(), record.getUriPattern())));
    }

    @Override
    public Optional<OpenIdClient> getClientByName(final String name) {
        return JooqUtil.contextResult(authDbConnProvider, context -> context
                .selectFrom(OauthClient.OAUTH_CLIENT)
                .where(OauthClient.OAUTH_CLIENT.NAME.eq(name))
                .fetchOptional()
                .map(record -> new OpenIdClient(record.getName(), record.getClientId(), record.getClientSecret(), record.getUriPattern())));
    }
}
