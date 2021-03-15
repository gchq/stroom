package stroom.security.identity.db;

import stroom.db.util.JooqUtil;
import stroom.security.identity.db.jooq.tables.OauthClient;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.security.openid.api.OpenIdClient;

import java.util.Optional;
import javax.inject.Inject;

public class OpenIdClientDaoImpl implements OpenIdClientDao {

    private final IdentityDbConnProvider identityDbConnProvider;

    @Inject
    OpenIdClientDaoImpl(final IdentityDbConnProvider identityDbConnProvider) {
        this.identityDbConnProvider = identityDbConnProvider;
    }

    @Override
    public void create(final OpenIdClient client) {
        JooqUtil.context(identityDbConnProvider, context -> context
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
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                .selectFrom(OauthClient.OAUTH_CLIENT)
                .where(OauthClient.OAUTH_CLIENT.CLIENT_ID.eq(clientId))
                .fetchOptional()
                .map(record -> new OpenIdClient(record.getName(),
                        record.getClientId(),
                        record.getClientSecret(),
                        record.getUriPattern())));
    }

    @Override
    public Optional<OpenIdClient> getClientByName(final String name) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                .selectFrom(OauthClient.OAUTH_CLIENT)
                .where(OauthClient.OAUTH_CLIENT.NAME.eq(name))
                .fetchOptional()
                .map(record -> new OpenIdClient(record.getName(),
                        record.getClientId(),
                        record.getClientSecret(),
                        record.getUriPattern())));
    }
}
