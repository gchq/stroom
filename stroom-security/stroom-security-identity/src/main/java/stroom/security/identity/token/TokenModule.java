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

package stroom.security.identity.token;

import stroom.job.api.ScheduledJobsBinder;
import stroom.security.openid.api.JsonWebKeyFactory;
import stroom.security.openid.api.PublicJsonWebKeyProvider;
import stroom.security.openid.api.RevokedTokenChecker;
import stroom.security.openid.api.TokenInventory;
import stroom.security.openid.api.TokenRevoker;
import stroom.util.RunnableWrapper;
import stroom.util.guice.RestResourcesBinder;

import com.google.inject.AbstractModule;
import jakarta.inject.Inject;

public final class TokenModule extends AbstractModule {

    @Override
    protected void configure() {
        bind(JsonWebKeyFactory.class).to(JwkFactoryImpl.class);
        bind(PublicJsonWebKeyProvider.class).to(JwkCache.class);
        // The verify path lives in a different module with no dependency on this one, so it consults
        // revocation state through this interface. Same arrangement as PublicJsonWebKeyProvider above.
        bind(RevokedTokenChecker.class).to(RevokedJtiCache.class);
        // The other direction of the same seam: how the RP asks the OP to revoke.
        bind(TokenRevoker.class).to(TokenRevocationService.class);
        // ...and how the RP reads what is outstanding, so the admin list can show subjects that
        // hold tokens but have no session.
        bind(TokenInventory.class).to(OAuthTokenInventory.class);

        RestResourcesBinder.create(binder())
                .bind(TokenRevocationResourceImpl.class)
                .bind(SigningKeyResourceImpl.class);

        ScheduledJobsBinder.create(binder())
                .bindJobTo(JwkRotation.class, jobBuilder -> jobBuilder
                        .name("Identity Key Rotation")
                        .description("Rotate the internal identity provider's token signing keys, "
                                + "retiring and eventually deleting old ones.")
                        .frequencySchedule("1d"))
                .bindJobTo(OAuthTokenPurge.class, jobBuilder -> jobBuilder
                        .name("Identity Token Purge")
                        .description("Delete expired rows from the internal identity provider's issued "
                                + "token inventory. Housekeeping only - expired rows are already ignored "
                                + "by every read, so this reclaims space rather than enforcing anything.")
                        // Tied to the shortest JWT lifetime (access and id tokens default to 60 minutes),
                        // so the table does not accumulate much more than one interval's worth of dead rows.
                        .frequencySchedule("1h"));
    }

    private static class JwkRotation extends RunnableWrapper {

        @Inject
        JwkRotation(final JwkRotationTask jwkRotationTask) {
            super(jwkRotationTask::exec);
        }
    }

    private static class OAuthTokenPurge extends RunnableWrapper {

        @Inject
        OAuthTokenPurge(final OAuthTokenPurgeTask oAuthTokenPurgeTask) {
            super(oAuthTokenPurgeTask::exec);
        }
    }
}
