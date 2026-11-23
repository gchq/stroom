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
import stroom.credentials.shared.CredentialType;
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

/// Checks the expiry of a credential each time its secret is retrieved: the earlier of its expiry
/// date and, for a key store, when its certificates expire.
///
/// An expired credential is refused or used with a warning, depending on
/// [CredentialsConfig#isRefuseExpiredCredentials()]. A credential that will expire within
/// [CredentialsConfig#getExpiryWarningPeriod()] is used with a warning. Warnings are logged at
/// most once every [#WARNING_INTERVAL] for each credential, and separately for one that is expiring
/// and one that has expired, as a secret may be retrieved very often.
@Singleton
public class CredentialExpiryChecker {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CredentialExpiryChecker.class);

    static final Duration WARNING_INTERVAL = Duration.ofHours(1);
    // The longest warning period used, so a mistyped setting can't make the time overflow
    static final Duration MAX_WARNING_PERIOD = Duration.ofDays(36_500);

    private final Provider<CredentialsConfig> credentialsConfigProvider;
    private final Supplier<Instant> nowSupplier;
    // Credential UUID and whether it was expiring or had expired, to the time a warning was last
    // logged for it
    private final Map<String, Instant> lastWarningTimes = new ConcurrentHashMap<>();

    /// @param credentialsConfigProvider Gives the expiry settings.
    @Inject
    public CredentialExpiryChecker(final Provider<CredentialsConfig> credentialsConfigProvider) {
        this(credentialsConfigProvider, Instant::now);
    }

    /// @param credentialsConfigProvider Gives the expiry settings.
    /// @param nowSupplier               Gives the current time, so tests can set it.
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
        return check(credential, null);
    }

    /// Checks the expiry of a credential that is about to be used, which is the earlier of its
    /// expiry date and when the certificates in its key store expire.
    ///
    /// @param credential        The credential whose secret has been retrieved. Nothing is done if
    ///                          it is null.
    /// @param certificateExpiry When the certificates in its key store expire, or null if it has
    ///                          none (see [KeyStoreCertificateExpiryReader]).
    /// @return True if a warning was logged.
    /// @throws IllegalStateException If the credential has expired and
    ///                               [CredentialsConfig#isRefuseExpiredCredentials()] is set.
    public boolean check(final Credential credential, final Instant certificateExpiry) {
        if (credential == null) {
            return false;
        }
        final Instant dateExpiry = NullSafe.get(credential.getExpiryTimeMs(), Instant::ofEpochMilli);
        final boolean certificateFirst = certificateExpiry != null
                                         && (dateExpiry == null || certificateExpiry.isBefore(dateExpiry));
        final Instant expiryTime = certificateFirst
                ? certificateExpiry
                : dateExpiry;
        if (expiryTime == null) {
            return false;
        }
        final String what = certificateFirst
                ? "The certificate in credential '" + credential.getName() + "'"
                : "Credential '" + credential.getName() + "'";
        final String action = getAction(credential, certificateFirst);
        final Instant now = nowSupplier.get();

        if (!now.isBefore(expiryTime)) {
            if (credentialsConfigProvider.get().isRefuseExpiredCredentials()) {
                throw new IllegalStateException(what + " expired at " + expiryTime + " so it can't be used. "
                                                + action);
            }
            if (isWarningDue(credential.getUuid(), true, now)) {
                LOGGER.warn(() -> LogUtil.message("{} ({}) expired at {} but is still being used. {}",
                        what, credential.getUuid(), expiryTime, action));
                return true;
            }
        } else if (now.plus(getWarningPeriod()).isAfter(expiryTime)
                   && isWarningDue(credential.getUuid(), false, now)) {
            LOGGER.warn(() -> LogUtil.message("{} ({}) will expire at {}. {}",
                    what, credential.getUuid(), expiryTime, action));
            return true;
        }
        return false;
    }

    /// @return How long before a credential expires to start warning about it, never null. A
    ///         negative setting gives zero and one over [#MAX_WARNING_PERIOD] gives that.
    public Duration getWarningPeriod() {
        final Duration warningPeriod = NullSafe.getOrElse(
                credentialsConfigProvider.get(),
                CredentialsConfig::getExpiryWarningPeriod,
                StroomDuration::getDuration,
                Duration.ZERO);
        if (warningPeriod.isNegative()) {
            return Duration.ZERO;
        } else if (warningPeriod.compareTo(MAX_WARNING_PERIOD) > 0) {
            return MAX_WARNING_PERIOD;
        }
        return warningPeriod;
    }

    /// Forgets the warnings logged for a credential, e.g. as it has been deleted.
    ///
    /// @param uuid The credential's UUID.
    public void forget(final String uuid) {
        lastWarningTimes.remove(getWarningKey(uuid, true));
        lastWarningTimes.remove(getWarningKey(uuid, false));
    }

    // What to do about an expiring credential
    private static String getAction(final Credential credential, final boolean certificateFirst) {
        if (!certificateFirst) {
            return "Replace its secret and update its expiry date.";
        } else if (credential.getCredentialType() == CredentialType.KEY_STORE_FILE) {
            // Replacing its secret only changes the path or password, not the file
            return "Replace its key store file.";
        }
        return "Replace its key store.";
    }

    /// Records that a warning is being logged for a credential, unless one of the same kind was
    /// logged for it within the last [#WARNING_INTERVAL]. Expiring and expired are kept apart so
    /// the first warning that it has expired isn't held back by one that it was about to.
    ///
    /// @return True if a warning should be logged now.
    private boolean isWarningDue(final String uuid, final boolean expired, final Instant now) {
        final AtomicBoolean due = new AtomicBoolean(false);
        // Atomically, so two threads using the credential at once don't both warn
        lastWarningTimes.compute(getWarningKey(uuid, expired), (key, lastWarningTime) -> {
            if (lastWarningTime == null || !now.isBefore(lastWarningTime.plus(WARNING_INTERVAL))) {
                due.set(true);
                return now;
            }
            return lastWarningTime;
        });
        return due.get();
    }

    private static String getWarningKey(final String uuid, final boolean expired) {
        return uuid + (expired
                ? ":expired"
                : ":expiring");
    }
}
