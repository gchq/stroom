/*
 * Copyright 2025 Crown Copyright
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

import ch.qos.logback.classic.Logger;
import ch.qos.logback.classic.spi.ILoggingEvent;
import ch.qos.logback.core.read.ListAppender;
import org.jose4j.jwk.PublicJsonWebKey;
import org.junit.jupiter.api.Test;
import org.slf4j.LoggerFactory;

import java.io.PrintWriter;
import java.io.StringWriter;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestJwkFactoryImpl {

    @Test
    void createPublicKey() {
        final JwkFactoryImpl jwkFactory = new JwkFactoryImpl();

        final PublicJsonWebKey key = jwkFactory.createPublicKey();

        assertThat(key.getKeyId()).isNotBlank();
        // Deliberately not logged: asJson() serialises the private key, and a test log is no better a place for
        // one than a production log.
        assertThat(jwkFactory.asJson(key)).contains(key.getKeyId());
    }

    @Test
    void keyMaterialNeverReachesTheErrorRaisedWhenItCannotBeRead() {
        // The stored JSON is the RSA private key, so a corrupt row must not turn it into a log entry. Nothing
        // derived from the input may appear in the exception - not the message, not a nested cause, not the
        // stack - because logs are usually aggregated somewhere far less protected than the database the key
        // came from.
        final JwkFactoryImpl jwkFactory = new JwkFactoryImpl();
        final String privateMaterial = "TOTALLY-SECRET-PRIVATE-EXPONENT";
        final String malformedJson = "{\"kty\":\"RSA\",\"kid\":\"key-1\",\"d\":\"" + privateMaterial + "\"";

        assertThatThrownBy(() -> jwkFactory.fromJson(malformedJson))
                .isInstanceOf(RuntimeException.class)
                .hasMessage("Unable to read a JSON Web Key from its stored JSON");

        final String rendered = renderFully(thrownBy(() -> jwkFactory.fromJson(malformedJson)));
        assertThat(rendered)
                .as("no part of the key material may survive into anything a logger could emit")
                .doesNotContain(privateMaterial)
                .doesNotContain(malformedJson);
    }

    @Test
    void keyMaterialIsNeverLoggedWhenItCannotBeRead() {
        // The regression that matters. The leak was a log statement that emitted the whole stored JSON on a
        // parse failure - so asserting on the exception alone would not have caught it. This watches what the
        // class actually logs.
        final JwkFactoryImpl jwkFactory = new JwkFactoryImpl();
        final String privateMaterial = "TOTALLY-SECRET-PRIVATE-EXPONENT";
        final String malformedJson = "{\"kty\":\"RSA\",\"kid\":\"key-1\",\"d\":\"" + privateMaterial + "\"";

        final Logger logger = (Logger) LoggerFactory.getLogger(JwkFactoryImpl.class);
        final ListAppender<ILoggingEvent> appender = new ListAppender<>();
        appender.start();
        logger.addAppender(appender);
        try {
            thrownBy(() -> jwkFactory.fromJson(malformedJson));
        } finally {
            logger.detachAppender(appender);
            appender.stop();
        }

        final String logged = appender.list.stream()
                .map(ILoggingEvent::getFormattedMessage)
                .collect(Collectors.joining("\n"));
        assertThat(logged)
                .as("the stored JSON is the private key and must never be logged")
                .doesNotContain(privateMaterial)
                .doesNotContain(malformedJson);
    }

    private static Throwable thrownBy(final Runnable runnable) {
        try {
            runnable.run();
        } catch (final Throwable t) {
            return t;
        }
        throw new AssertionError("Expected the key to be rejected");
    }

    /**
     * Everything a logger could emit for this throwable: its message, its stack, and every nested cause.
     */
    private static String renderFully(final Throwable throwable) {
        final StringWriter writer = new StringWriter();
        try (final PrintWriter printWriter = new PrintWriter(writer)) {
            throwable.printStackTrace(printWriter);
        }
        return writer.toString();
    }
}
