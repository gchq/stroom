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

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.KeyStoreFileSecret;
import stroom.credentials.shared.KeyStoreSecret;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.cert.Certificate;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.Collections;
import java.util.Map;
import java.util.Objects;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Supplier;

/// Finds when the certificates in a 'Key Store' or 'Key Store File' credential expire.
///
/// For a key store holding keys, this is when the first certificate in any key's chain expires,
/// as the key can't be used after that. A key store holding only trusted certificates (a trust
/// store) often keeps old CA certificates that are never used, so for one of those it is when the
/// last of them expires, after which it trusts nothing.
///
/// Reading a key store takes a little while and it is done on every use of the credential, so
/// the result is kept for each credential until the credential is changed or, for a key store
/// file, the file is. A key store that can't be read is tried again next time, as it may have been
/// read while it was being written.
@Singleton
public class KeyStoreCertificateExpiryReader {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KeyStoreCertificateExpiryReader.class);

    private final KeyStoreFilePathChecker keyStoreFilePathChecker;
    // Credential UUID to what was read for it
    private final Map<String, CachedExpiry> cache = new ConcurrentHashMap<>();

    /// @param keyStoreFilePathChecker Checks a key store file is in an allowed directory before it
    ///                                is opened.
    @Inject
    public KeyStoreCertificateExpiryReader(final KeyStoreFilePathChecker keyStoreFilePathChecker) {
        this.keyStoreFilePathChecker = keyStoreFilePathChecker;
    }

    /// Gets when the certificates of a key store credential expire.
    ///
    /// @param credential           The credential. Nothing is read unless it is a 'Key Store' or
    ///                             'Key Store File' credential.
    /// @param storedSecretSupplier Gets the credential's secret, if the key store must be read.
    /// @return When the certificates expire, or null if the credential is not a key store, has no
    ///         certificates or can't be read. Never throws.
    public Instant getExpiry(final Credential credential,
                             final Supplier<StoredSecret> storedSecretSupplier) {
        final CredentialType credentialType = NullSafe.get(credential, Credential::getCredentialType);
        if (credentialType != CredentialType.KEY_STORE && credentialType != CredentialType.KEY_STORE_FILE) {
            return null;
        }
        final Path keyStoreFilePath;
        if (credentialType == CredentialType.KEY_STORE_FILE) {
            keyStoreFilePath = getAllowedPath(credential);
            if (keyStoreFilePath == null) {
                return null;
            }
        } else {
            keyStoreFilePath = null;
        }
        final String version = getVersion(credential, keyStoreFilePath);
        final CachedExpiry cached = cache.get(credential.getUuid());
        if (cached != null && Objects.equals(cached.version(), version)) {
            return cached.expiry();
        }

        try {
            final Instant expiry = read(storedSecretSupplier, keyStoreFilePath);
            cache.put(credential.getUuid(), new CachedExpiry(version, expiry));
            return expiry;
        } catch (final IOException | GeneralSecurityException | RuntimeException e) {
            // Not kept, so it is read again next time. Using the key store will fail too, with an
            // error that says why.
            cache.remove(credential.getUuid());
            LOGGER.debug(() -> LogUtil.message("Unable to read the certificates of credential '{}' ({}): {}",
                    credential.getName(),
                    credential.getUuid(),
                    e.getMessage()), e);
            return null;
        }
    }

    /// Forgets what was read for a credential, e.g. as it has been deleted.
    ///
    /// @param uuid The credential's UUID.
    public void forget(final String uuid) {
        cache.remove(uuid);
    }

    // The real path of a key store file, or null if it isn't in an allowed directory, as nothing
    // outside them may be opened
    private Path getAllowedPath(final Credential credential) {
        try {
            return keyStoreFilePathChecker.check(credential.getKeyStorePath());
        } catch (final IllegalArgumentException e) {
            // Using the key store will fail too, with an error that says why
            LOGGER.debug(() -> LogUtil.message("Not reading the certificates of credential '{}': {}",
                    credential.getUuid(), e.getMessage()));
            return null;
        }
    }

    // The credential's update time, and for a key store file which file it is and when it was
    // last changed, e.g. by cert-manager
    private static String getVersion(final Credential credential, final Path keyStoreFilePath) {
        if (keyStoreFilePath != null) {
            return credential.getUpdateTimeMs() + ":" + keyStoreFilePath + ":"
                   + getLastModifiedTime(keyStoreFilePath);
        }
        return String.valueOf(credential.getUpdateTimeMs());
    }

    private static String getLastModifiedTime(final Path path) {
        try {
            return Files.getLastModifiedTime(path).toString();
        } catch (final IOException e) {
            return null;
        }
    }

    private static Instant read(final Supplier<StoredSecret> storedSecretSupplier,
                                final Path keyStoreFilePath) throws IOException, GeneralSecurityException {
        final StoredSecret storedSecret = storedSecretSupplier.get();
        final KeyStore keyStore = switch (NullSafe.get(storedSecret, StoredSecret::secret)) {
            case final KeyStoreSecret keyStoreSecret -> load(keyStoreSecret.getKeyStoreType(),
                    keyStoreSecret.getKeyStorePassword(),
                    storedSecret.keyStore());
            case final KeyStoreFileSecret keyStoreFileSecret -> load(keyStoreFileSecret.getKeyStoreType(),
                    keyStoreFileSecret.getKeyStorePassword(),
                    keyStoreFilePath);
            case null, default -> null;
        };
        return getExpiry(keyStore);
    }

    private static KeyStore load(final KeyStoreType keyStoreType,
                                 final String password,
                                 final byte[] bytes) throws IOException, GeneralSecurityException {
        if (bytes == null || bytes.length == 0) {
            return null;
        }
        try (final InputStream inputStream = new ByteArrayInputStream(bytes)) {
            return load(keyStoreType, password, inputStream);
        }
    }

    private static KeyStore load(final KeyStoreType keyStoreType,
                                 final String password,
                                 final Path path) throws IOException, GeneralSecurityException {
        try (final InputStream inputStream = Files.newInputStream(path)) {
            return load(keyStoreType, password, inputStream);
        }
    }

    private static KeyStore load(final KeyStoreType keyStoreType,
                                 final String password,
                                 final InputStream inputStream) throws IOException, GeneralSecurityException {
        final KeyStore keyStore = KeyStore.getInstance(
                Objects.requireNonNullElse(keyStoreType, KeyStoreType.PKCS12).getDisplayValue());
        keyStore.load(inputStream, NullSafe.get(password, String::toCharArray));
        return keyStore;
    }

    /// @param keyStore A loaded key store, or null.
    /// @return When its certificates expire (see the class description), or null if it has none.
    static Instant getExpiry(final KeyStore keyStore) throws GeneralSecurityException {
        if (keyStore == null) {
            return null;
        }
        Instant earliestKeyExpiry = null;
        Instant latestTrustedExpiry = null;
        for (final String alias : Collections.list(keyStore.aliases())) {
            if (keyStore.isKeyEntry(alias)) {
                for (final Certificate certificate : NullSafe.asList(keyStore.getCertificateChain(alias))) {
                    earliestKeyExpiry = earliest(earliestKeyExpiry, getNotAfter(certificate));
                }
            } else if (keyStore.isCertificateEntry(alias)) {
                latestTrustedExpiry = latest(latestTrustedExpiry, getNotAfter(keyStore.getCertificate(alias)));
            }
        }
        return earliestKeyExpiry != null
                ? earliestKeyExpiry
                : latestTrustedExpiry;
    }

    private static Instant getNotAfter(final Certificate certificate) {
        return certificate instanceof final X509Certificate x509Certificate
                ? x509Certificate.getNotAfter().toInstant()
                : null;
    }

    private static Instant earliest(final Instant instant1, final Instant instant2) {
        if (instant1 == null) {
            return instant2;
        } else if (instant2 == null) {
            return instant1;
        }
        return instant1.isBefore(instant2)
                ? instant1
                : instant2;
    }

    private static Instant latest(final Instant instant1, final Instant instant2) {
        if (instant1 == null) {
            return instant2;
        } else if (instant2 == null) {
            return instant1;
        }
        return instant1.isAfter(instant2)
                ? instant1
                : instant2;
    }


    // --------------------------------------------------------------------------------


    /// @param version What the expiry was read from (see [#getVersion(Credential, Path)]).
    /// @param expiry  When the certificates expire, or null if there are none.
    private record CachedExpiry(String version, Instant expiry) {

    }
}
