/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.security.identity.db;

import stroom.db.util.JooqUtil;
import stroom.security.api.SecurityContext;
import stroom.security.identity.db.jooq.tables.OauthClient;
import stroom.security.identity.openid.OpenIdClientDao;
import stroom.security.openid.api.OpenIdClient;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;

import java.util.Optional;

public class OpenIdClientDaoImpl implements OpenIdClientDao {

    private final IdentityDbConnProvider identityDbConnProvider;
    private final SecurityContext securityContext;

    @Inject
    OpenIdClientDaoImpl(final IdentityDbConnProvider identityDbConnProvider,
                        final SecurityContext securityContext) {
        this.identityDbConnProvider = identityDbConnProvider;
        this.securityContext = securityContext;
    }

    @Override
    public void createIfNotExists(final OpenIdClient client) {
        final long now = System.currentTimeMillis();
        final String user = NullSafe.getOrElse(securityContext, SecurityContext::getUserIdentityForAudit, "");
        JooqUtil.onDuplicateKeyIgnore(() ->
                JooqUtil.context(identityDbConnProvider, context -> context
                        .insertInto(OauthClient.OAUTH_CLIENT)
                        .set(OauthClient.OAUTH_CLIENT.VERSION, 0)
                        .set(OauthClient.OAUTH_CLIENT.CREATE_TIME_MS, now)
                        .set(OauthClient.OAUTH_CLIENT.CREATE_USER, user)
                        .set(OauthClient.OAUTH_CLIENT.UPDATE_TIME_MS, now)
                        .set(OauthClient.OAUTH_CLIENT.UPDATE_USER, user)
                        .set(OauthClient.OAUTH_CLIENT.NAME, client.getName())
                        .set(OauthClient.OAUTH_CLIENT.CLIENT_ID, client.getClientId())
                        .set(OauthClient.OAUTH_CLIENT.CLIENT_SECRET, client.getClientSecret())
                        .set(OauthClient.OAUTH_CLIENT.URI_PATTERN, client.getUriPattern())
                        .execute()));
    }

    @Override
    public Optional<OpenIdClient> getClientForClientId(final String clientId) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(OauthClient.OAUTH_CLIENT)
                        .where(OauthClient.OAUTH_CLIENT.CLIENT_ID.eq(clientId))
                        .fetchOptional())
                .map(record -> new OpenIdClient(record.getName(),
                        record.getClientId(),
                        record.getClientSecret(),
                        record.getUriPattern()));
    }

    @Override
    public Optional<OpenIdClient> getClientByName(final String name) {
        return JooqUtil.contextResult(identityDbConnProvider, context -> context
                        .selectFrom(OauthClient.OAUTH_CLIENT)
                        .where(OauthClient.OAUTH_CLIENT.NAME.eq(name))
                        .fetchOptional())
                .map(record -> new OpenIdClient(record.getName(),
                        record.getClientId(),
                        record.getClientSecret(),
                        record.getUriPattern()));
    }
}
