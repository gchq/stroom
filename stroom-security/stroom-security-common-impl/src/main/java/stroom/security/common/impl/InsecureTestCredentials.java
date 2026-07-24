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

package stroom.security.common.impl;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import jakarta.inject.Singleton;
import jakarta.servlet.http.HttpServletRequest;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.time.Duration;
import java.time.Instant;

/**
 * An optional, insecure, test-only shared secret used to wire stroom and stroom-proxy together in CI or
 * demo environments without standing up a real identity provider. It is independent of the configured
 * {@link stroom.security.openid.api.IdpType} - the normal identity provider (e.g. the internal IDP) still
 * handles all interactive and token authentication.
 * <p>
 * It is disabled unless <strong>both</strong> of the following are supplied, as an environment variable or a
 * system property:
 * <ul>
 *     <li>{@value #ALLOW_PROP}{@code =true} - an explicit acknowledgement that this is insecure, and</li>
 *     <li>{@value #SECRET_PROP} - the shared secret to match.</li>
 * </ul>
 * When enabled, a request whose bearer token equals the secret is authenticated as the service (processing)
 * user. Because both settings are supplied at runtime (not in the config file) they do not travel with a
 * copied configuration, so a production deployment that never sets them cannot be tricked into enabling this.
 * <strong>Never set these in production.</strong>
 */
@Singleton
public class InsecureTestCredentials {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(InsecureTestCredentials.class);

    public static final String ALLOW_PROP = "STROOM_ALLOW_INSECURE_TEST_CREDENTIALS";
    public static final String SECRET_PROP = "STROOM_INSECURE_TEST_CREDENTIAL";

    private static final String BEARER_PREFIX = "Bearer ";
    private static final Duration TIME_BETWEEN_WARNINGS = Duration.ofMinutes(5);

    private final boolean enabled;
    private final byte[] secretBytes;

    private volatile Instant nextWarningTime = Instant.EPOCH;

    public InsecureTestCredentials() {
        final boolean allow = "true".equalsIgnoreCase(getSetting(ALLOW_PROP));
        final String secret = getSetting(SECRET_PROP);
        this.enabled = allow && NullSafe.isNonBlankString(secret);
        this.secretBytes = this.enabled
                ? secret.getBytes(StandardCharsets.UTF_8)
                : null;
        if (allow && !this.enabled) {
            LOGGER.error("{} is set but {} is blank - insecure test credentials are disabled.",
                    ALLOW_PROP, SECRET_PROP);
        }
    }

    private static String getSetting(final String key) {
        final String fromSysProp = System.getProperty(key);
        return NullSafe.isNonBlankString(fromSysProp)
                ? fromSysProp
                : System.getenv(key);
    }

    public boolean isEnabled() {
        return enabled;
    }

    /**
     * @return true if insecure test credentials are enabled and the request carries a bearer token that
     * equals the configured secret. The comparison is constant time.
     */
    public boolean matches(final HttpServletRequest request) {
        if (!enabled || request == null) {
            return false;
        }
        final String header = request.getHeader("Authorization");
        if (NullSafe.isBlankString(header) || !header.startsWith(BEARER_PREFIX)) {
            return false;
        }
        final String presented = header.substring(BEARER_PREFIX.length()).trim();
        final boolean matches = MessageDigest.isEqual(
                presented.getBytes(StandardCharsets.UTF_8), secretBytes);
        if (matches) {
            showWarning();
        }
        return matches;
    }

    private void showWarning() {
        final Instant now = Instant.now();
        if (now.isAfter(nextWarningTime)) {
            synchronized (this) {
                if (now.isAfter(nextWarningTime)) {
                    LOGGER.warn("Authenticating a request as the service user using the insecure test " +
                                "credential ({}). This is totally insecure and must only be used for " +
                                "test/demo. Unset {} in production.", SECRET_PROP, ALLOW_PROP);
                    nextWarningTime = now.plus(TIME_BETWEEN_WARNINGS);
                }
            }
        }
    }
}
