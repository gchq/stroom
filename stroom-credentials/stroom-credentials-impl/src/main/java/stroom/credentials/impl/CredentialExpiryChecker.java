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

package stroom.credentials.impl;

import stroom.credentials.shared.Credential;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Supplier;

/// Checks the expiry date of a credential each time its secret is retrieved.
///
/// An expired credential is refused or used with a warning, depending on
/// [CredentialsConfig#isRefuseExpiredCredentials()]. A credential that will expire within
/// [CredentialsConfig#getExpiryWarningPeriod()] is used with a warning. Warnings are logged at
/// most once every [#WARNING_INTERVAL] for each credential, as a secret may be retrieved very often.
@Singleton
public class CredentialExpiryChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialExpiryChecker.class);

    static final Duration WARNING_INTERVAL = Duration.ofHours(1);

    private final Provider<CredentialsConfig> credentialsConfigProvider;
    private final Supplier<Instant> nowSupplier;
    // Credential UUID to the time a warning was last logged for it
    private final Map<String, Instant> lastWarningTimes = new ConcurrentHashMap<>();

    @Inject
    public CredentialExpiryChecker(final Provider<CredentialsConfig> credentialsConfigProvider) {
        this(credentialsConfigProvider, Instant::now);
    }

    CredentialExpiryChecker(final Provider<CredentialsConfig> credentialsConfigProvider,
                            final Supplier<Instant> nowSupplier) {
        this.credentialsConfigProvider = credentialsConfigProvider;
        this.nowSupplier = nowSupplier;
    }

    /// Checks the expiry date of a credential that is about to be used.
    ///
    /// @param credential The credential whose secret has been retrieved. Nothing is done if it is
    ///                   null or has no expiry date.
    /// @return True if a warning was logged.
    /// @throws IllegalStateException If the credential has expired and
    ///                               [CredentialsConfig#isRefuseExpiredCredentials()] is set.
    public boolean check(final Credential credential) {
        final Long expiryTimeMs = NullSafe.get(credential, Credential::getExpiryTimeMs);
        if (expiryTimeMs == null) {
            return false;
        }
        final CredentialsConfig config = credentialsConfigProvider.get();
        final Instant now = nowSupplier.get();
        final Instant expiryTime = Instant.ofEpochMilli(expiryTimeMs);

        if (!now.isBefore(expiryTime)) {
            if (config.isRefuseExpiredCredentials()) {
                throw new IllegalStateException("Credential '" + credential.getName()
                                                + "' expired at " + expiryTime
                                                + " and can't be used. Replace its secret and "
                                                + "update its expiry date.");
            }
            if (isWarningDue(credential.getUuid(), now)) {
                LOGGER.warn(() -> LogUtil.message(
                        "Credential '{}' ({}) expired at {} but is still being used. "
                        + "Replace its secret and update its expiry date.",
                        credential.getName(),
                        credential.getUuid(),
                        expiryTime));
                return true;
            }
        } else if (now.plus(getWarningPeriod(config)).isAfter(expiryTime)
                   && isWarningDue(credential.getUuid(), now)) {
            LOGGER.warn(() -> LogUtil.message(
                    "Credential '{}' ({}) will expire at {}. "
                    + "Replace its secret and update its expiry date before then.",
                    credential.getName(),
                    credential.getUuid(),
                    expiryTime));
            return true;
        }
        return false;
    }

    private static Duration getWarningPeriod(final CredentialsConfig config) {
        return NullSafe.getOrElse(
                config,
                CredentialsConfig::getExpiryWarningPeriod,
                StroomDuration::getDuration,
                Duration.ZERO);
    }

    /// Records that a warning is being logged for a credential, unless one was logged for it
    /// within the last [#WARNING_INTERVAL].
    ///
    /// @return True if a warning should be logged now.
    private boolean isWarningDue(final String uuid, final Instant now) {
        final AtomicBoolean due = new AtomicBoolean(false);
        // Atomically, so two threads using the credential at once don't both warn
        lastWarningTimes.compute(uuid, (key, lastWarningTime) -> {
            if (lastWarningTime == null || !now.isBefore(lastWarningTime.plus(WARNING_INTERVAL))) {
                due.set(true);
                return now;
            }
            return lastWarningTime;
        });
        return due.get();
    }
}
