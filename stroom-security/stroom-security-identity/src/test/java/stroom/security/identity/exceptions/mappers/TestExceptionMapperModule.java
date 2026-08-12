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

package stroom.security.identity.exceptions.mappers;

import stroom.config.common.UriFactory;
import stroom.security.identity.config.IdentityConfig;
import stroom.security.identity.exceptions.BadRequestException;
import stroom.security.identity.exceptions.ConflictException;
import stroom.security.identity.exceptions.UnsupportedFilterException;

import com.google.inject.AbstractModule;
import com.google.inject.Guice;
import com.google.inject.Injector;
import com.google.inject.Key;
import com.google.inject.TypeLiteral;
import event.logging.AuthenticateOutcomeReason;
import jakarta.ws.rs.core.Response;
import jakarta.ws.rs.ext.ExceptionMapper;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A mapper only takes effect by being in the set the delegating mapper iterates; the {@code @Provider}
 * annotation on its own does nothing, because nothing scans for it. These mappers were written and never
 * registered, so every exception they describe was answered as a server error instead.
 */
class TestExceptionMapperModule {

    @Test
    void theModuleRegistersAMapperForEachExceptionItDefines() {
        final Set<ExceptionMapper> mappers = registeredMappers();

        assertThat(statusFor(mappers, new BadRequestException(
                "jbloggs", AuthenticateOutcomeReason.INCORRECT_USERNAME_OR_PASSWORD, "bad")))
                .as("a malformed request is the caller's error, not the server's")
                .isEqualTo(Response.Status.BAD_REQUEST.getStatusCode());
        assertThat(statusFor(mappers, new ConflictException("clash")))
                .isEqualTo(Response.Status.CONFLICT.getStatusCode());
        assertThat(statusFor(mappers, new UnsupportedFilterException("nope")))
                .as("understood but not supported")
                .isEqualTo(422);
    }

    /**
     * The delegating mapper picks a mapper by calling each in turn and moving on when the cast fails, so a
     * mapper must reject anything that is not its own exception. One that accepted a supertype would answer
     * for exceptions belonging to another mapper, and which won would depend on set ordering.
     */
    @Test
    void noMapperAnswersForAnExceptionThatIsNotItsOwn() {
        for (final ExceptionMapper mapper : registeredMappers()) {
            assertThat(mapped(mapper, new IllegalStateException("not mine")))
                    .as("%s must not answer for an unrelated exception", mapper.getClass().getSimpleName())
                    .isNull();
        }
    }

    private static Set<ExceptionMapper> registeredMappers() {
        final Injector injector = Guice.createInjector(new ExceptionMapperModule(), new AbstractModule() {
            @Override
            protected void configure() {
                // Only the certificate mapper has dependencies, and only to build a redirect.
                bind(UriFactory.class).toInstance(Mockito.mock(UriFactory.class));
                bind(IdentityConfig.class).toInstance(new IdentityConfig());
            }
        });
        return injector.getInstance(Key.get(new TypeLiteral<Set<ExceptionMapper>>() {
        }));
    }

    private static int statusFor(final Set<ExceptionMapper> mappers, final Throwable throwable) {
        return mappers.stream()
                .map(mapper -> mapped(mapper, throwable))
                .filter(java.util.Objects::nonNull)
                .findFirst()
                .map(Response::getStatus)
                .orElseThrow(() -> new AssertionError(
                        "No registered mapper handles " + throwable.getClass().getSimpleName()));
    }

    @SuppressWarnings("unchecked")
    private static Response mapped(final ExceptionMapper mapper, final Throwable throwable) {
        try {
            return mapper.toResponse(throwable);
        } catch (final ClassCastException e) {
            // How the delegating mapper itself decides a mapper does not apply.
            return null;
        }
    }
}
