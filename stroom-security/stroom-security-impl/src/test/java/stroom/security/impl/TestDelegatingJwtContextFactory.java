/*
 * Copyright 2026 Crown Copyright
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

package stroom.security.impl;

import stroom.security.common.impl.StandardJwtContextFactory;
import stroom.security.openid.api.IdpType;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * The revoked-jti denylist is deliberately confined to {@link InternalJwtContextFactory}, the
 * {@code INTERNAL_IDP} arm. These tests pin the routing that makes that confinement correct: externally minted
 * tokens are handled by {@link StandardJwtContextFactory}, which has no revocation check at all - their
 * revocation is the external IdP's business. That same class is what stroom-proxy binds, which is why proxy
 * needs no {@code RevokedTokenChecker} binding despite having no identity module.
 */
@ExtendWith(MockitoExtension.class)
class TestDelegatingJwtContextFactory {

    @Mock
    private StroomOpenIdConfig openIdConfiguration;

    @Test
    void internalIdpModeUsesTheFactoryThatChecksRevocation() {
        final InternalJwtContextFactory internal = mock(InternalJwtContextFactory.class);
        final StandardJwtContextFactory standard = mock(StandardJwtContextFactory.class);
        when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.INTERNAL_IDP);
        final DelegatingJwtContextFactory factory =
                new DelegatingJwtContextFactory(internal, standard, () -> openIdConfiguration);

        factory.getJwtContext("a.token");

        // The external factory must not see it at all.
        verifyNoInteractions(standard);
    }

    @Test
    void externalIdpModeBypassesRevocationEntirely() {
        final InternalJwtContextFactory internal = mock(InternalJwtContextFactory.class);
        final StandardJwtContextFactory standard = mock(StandardJwtContextFactory.class);
        when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.EXTERNAL_IDP);
        final DelegatingJwtContextFactory factory =
                new DelegatingJwtContextFactory(internal, standard, () -> openIdConfiguration);

        factory.getJwtContext("a.token");

        // InternalJwtContextFactory is the only place the denylist is consulted, so never reaching it is
        // exactly what "the external path is untouched" means.
        verifyNoInteractions(internal);
    }

    @Test
    void noIdpModeHasNoVerificationPathAtAll() {
        final DelegatingJwtContextFactory factory = new DelegatingJwtContextFactory(
                mock(InternalJwtContextFactory.class),
                mock(StandardJwtContextFactory.class),
                () -> openIdConfiguration);
        when(openIdConfiguration.getIdentityProviderType()).thenReturn(IdpType.NO_IDP);

        assertThatThrownBy(() -> factory.getJwtContext("a.token"))
                .isInstanceOf(UnsupportedOperationException.class);
    }

    @Test
    void standardFactoryCannotConsultTheDenylist() {
        // A structural assertion rather than a behavioural one: StandardJwtContextFactory lives in
        // stroom-security-common-impl, which stroom-proxy also uses, and it takes no RevokedTokenChecker.
        // If someone later adds one there, proxy would need a binding it cannot provide - so pin it here.
        final boolean takesAChecker = java.util.Arrays.stream(
                        StandardJwtContextFactory.class.getDeclaredConstructors())
                .flatMap(constructor -> java.util.Arrays.stream(constructor.getParameterTypes()))
                .anyMatch(type -> type.getSimpleName().equals("RevokedTokenChecker"));

        assertThat(takesAChecker)
                .as("revocation must stay confined to the internal-IdP verify path")
                .isFalse();
    }
}
