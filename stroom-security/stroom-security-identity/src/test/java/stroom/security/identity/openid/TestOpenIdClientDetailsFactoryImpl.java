/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.security.identity.openid;

import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.openid.api.AbstractOpenIdConfig;
import stroom.security.openid.api.IdpType;
import stroom.security.openid.api.OpenIdClient;

import org.junit.jupiter.api.Test;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestOpenIdClientDetailsFactoryImpl {

    private static final String REAL_CLIENT_ID = "aRealFortyCharacterRandomClientCredential";
    private static final String REAL_CLIENT_SECRET = "aRealClientSecret";

    @Test
    void anUnexpectedClientIdIsRefusedWithoutNamingTheRealOne() {
        // The client id is a random credential the rest of the system keeps unguessable, and the authorize
        // endpoint takes it from an unauthenticated caller. Naming the expected value in the error hands
        // over what is needed to build a well formed request, so the refusal has to be generic.
        final OpenIdClientDetailsFactoryImpl factory = factory();

        assertThatThrownBy(() -> factory.getClient("not-the-client-id"))
                .isInstanceOf(BadRequestException.class)
                .hasMessage("Invalid client ID");
    }

    @Test
    void noPartOfTheRealClientIdSurvivesIntoAnythingTheCallerCouldSee() {
        // Not only the message: the caller sees whatever the exception renders, so a nested cause or the
        // stack would leak it just as effectively.
        final OpenIdClientDetailsFactoryImpl factory = factory();

        final Throwable thrown = catchThrown(() -> factory.getClient("not-the-client-id"));

        assertThat(renderFully(thrown))
                .as("the expected client id belongs in the log, not in anything returned")
                .doesNotContain(REAL_CLIENT_ID)
                .doesNotContain(REAL_CLIENT_SECRET);
    }

    @Test
    void theSuppliedClientIdIsCarriedForTheAuditEvent() {
        // What the caller sent is theirs already, and the audit event needs it to record what was tried.
        final OpenIdClientDetailsFactoryImpl factory = factory();

        final Throwable thrown = catchThrown(() -> factory.getClient("not-the-client-id"));

        assertThat(((BadRequestException) thrown).getSubject()).isEqualTo("not-the-client-id");
    }

    @Test
    void theRealClientIdIsStillAccepted() {
        assertThat(factory().getClient(REAL_CLIENT_ID).getClientId()).isEqualTo(REAL_CLIENT_ID);
    }

    private static OpenIdClientDetailsFactoryImpl factory() {
        final OpenIdClient client = new OpenIdClient(
                "Stroom Client Internal", REAL_CLIENT_ID, REAL_CLIENT_SECRET);
        final OpenIdClientDao dao = new OpenIdClientDao() {
            @Override
            public void createIfNotExists(final OpenIdClient toCreate) {
            }

            @Override
            public Optional<OpenIdClient> getClientForClientId(final String clientId) {
                return REAL_CLIENT_ID.equals(clientId)
                        ? Optional.of(client)
                        : Optional.empty();
            }

            @Override
            public Optional<OpenIdClient> getClientByName(final String name) {
                return Optional.of(client);
            }
        };
        // The factory only reads the provider to decide whether this is the internal IdP, and the DAO
        // above already holds the client, so nothing is generated.
        final AbstractOpenIdConfig config = new AbstractOpenIdConfig() {
            @Override
            public IdpType getDefaultIdpType() {
                return IdpType.INTERNAL_IDP;
            }

            @Override
            public IdpType getIdentityProviderType() {
                return IdpType.INTERNAL_IDP;
            }
        };
        return new OpenIdClientDetailsFactoryImpl(dao, () -> config);
    }

    private static Throwable catchThrown(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final Throwable t) {
            return t;
        }
        throw new AssertionError("Expected the client id to be refused");
    }

    private static String renderFully(final Throwable throwable) {
        final StringWriter writer = new StringWriter();
        try (final PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return writer.toString();
    }
}
