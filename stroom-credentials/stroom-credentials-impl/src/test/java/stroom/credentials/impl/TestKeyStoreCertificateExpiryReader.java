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
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.util.io.PathCreator;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.attribute.FileTime;
import java.security.cert.CertificateFactory;
import java.security.cert.X509Certificate;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class TestKeyStoreCertificateExpiryReader {

    private static final String PASSWORD = "changeit";

    @TempDir
    private Path tempDir;
    private Path allowedDir;
    private KeyStoreCertificateExpiryReader reader;

    @BeforeEach
    void setUp() throws IOException {
        allowedDir = Files.createDirectories(tempDir.resolve("certs"));
        final PathCreator pathCreator = mock(PathCreator.class);
        when(pathCreator.toAppPath(allowedDir.toString())).thenReturn(allowedDir);
        final CredentialsConfig config = new CredentialsConfig(
                null, null, List.of(allowedDir.toString()), null, null, null, null, null);
        reader = new KeyStoreCertificateExpiryReader(new KeyStoreFilePathChecker(pathCreator, () -> config));
    }

    @Test
    void testOtherCredentialTypesAreNotRead() {
        final Credential credential = createCredential(CredentialType.USERNAME_PASSWORD, null, 1L);

        assertThat(reader.getExpiry(credential, () -> {
            throw new AssertionError("The secret should not be needed");
        })).isNull();
    }

    @Test
    void testKeyStoreWithAKeyGivesTheKeysCertificateExpiry() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        generateKey(keyStore, "key", "-100d", 10);
        // A trusted certificate that expires later is ignored when there is a key
        final Path otherKeyStore = tempDir.resolve("other.p12");
        final Path certificate = generateKey(otherKeyStore, "other", "-100d", 1000);
        importCertificate(keyStore, "trusted", certificate);

        final Instant expiry = reader.getExpiry(
                createCredential(CredentialType.KEY_STORE, null, 1L),
                () -> keyStoreSecret(keyStore));

        assertThat(expiry)
                .isEqualTo(getNotAfter(exportCertificate(keyStore, "key")))
                .isBefore(Instant.now());
    }

    @Test
    void testTrustStoreGivesTheLastCertificatesExpiry() throws Exception {
        final Path trustStore = allowedDir.resolve("trust.p12");
        final Path early = generateKey(tempDir.resolve("early.p12"), "early", "-100d", 10);
        final Path late = generateKey(tempDir.resolve("late.p12"), "late", "-100d", 1000);
        importCertificate(trustStore, "early", early);
        importCertificate(trustStore, "late", late);

        final Instant expiry = reader.getExpiry(
                createCredential(CredentialType.KEY_STORE, null, 1L),
                () -> keyStoreSecret(trustStore));

        assertThat(expiry)
                .isEqualTo(getNotAfter(late))
                .isAfter(Instant.now());
    }

    @Test
    void testKeyStoreFileIsReadWhereItIs() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        final Path certificate = generateKey(keyStore, "key", "-1d", 30);
        final Credential credential = createCredential(CredentialType.KEY_STORE_FILE, keyStore, 1L);

        final Instant expiry = reader.getExpiry(credential, () -> keyStoreFileSecret(credential, keyStore));

        assertThat(expiry)
                .isEqualTo(getNotAfter(certificate));
    }

    @Test
    void testKeyStoreFileOutsideTheAllowedDirsIsNotRead() throws Exception {
        final Path keyStore = tempDir.resolve("outside.p12");
        generateKey(keyStore, "key", "-1d", 30);
        final Credential credential = createCredential(CredentialType.KEY_STORE_FILE, keyStore, 1L);

        assertThat(reader.getExpiry(credential, () -> {
            throw new AssertionError("The secret should not be needed");
        })).isNull();
    }

    @Test
    void testKeyStoreThatCantBeReadGivesNull() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        generateKey(keyStore, "key", "-1d", 30);
        final Credential credential = createCredential(CredentialType.KEY_STORE, null, 1L);
        final byte[] keyStoreBytes = Files.readAllBytes(keyStore);

        assertThat(reader.getExpiry(credential, () -> new StoredSecret(credential,
                new KeyStoreSecret("ks-uuid", KeyStoreType.PKCS12, "wrong password", null),
                keyStoreBytes)))
                .isNull();
        assertThat(reader.getExpiry(createCredential(CredentialType.KEY_STORE, null, 2L),
                () -> new StoredSecret(credential, new UsernamePasswordSecret("user", "pass"), null)))
                .isNull();
    }

    @Test
    void testSecretThatCantBeLoadedGivesNull() {
        assertThat(reader.getExpiry(createCredential(CredentialType.KEY_STORE, null, 1L), () -> {
            throw new RuntimeException("Unable to read the secret JSON");
        })).isNull();
    }

    /// A failed read isn't kept, as the key store may have been read while it was being written.
    @Test
    void testFailedReadIsTriedAgain() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        final Path certificate = generateKey(keyStore, "key", "-1d", 30);
        final Credential credential = createCredential(CredentialType.KEY_STORE, null, 1L);
        final byte[] keyStoreBytes = Files.readAllBytes(keyStore);
        final byte[] partBytes = Arrays.copyOf(keyStoreBytes, keyStoreBytes.length / 2);

        assertThat(reader.getExpiry(credential, () -> new StoredSecret(credential,
                new KeyStoreSecret("ks-uuid", KeyStoreType.PKCS12, PASSWORD, null),
                partBytes)))
                .isNull();
        assertThat(reader.getExpiry(credential, () -> keyStoreSecret(keyStore)))
                .isEqualTo(getNotAfter(certificate));
    }

    @Test
    void testForgottenCredentialIsReadAgain() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        generateKey(keyStore, "key", "-1d", 30);
        final Credential credential = createCredential(CredentialType.KEY_STORE, null, 1L);
        final AtomicInteger reads = new AtomicInteger();
        final Supplier<StoredSecret> supplier = () -> {
            reads.incrementAndGet();
            return keyStoreSecret(keyStore);
        };

        reader.getExpiry(credential, supplier);
        reader.forget(credential.getUuid());
        reader.getExpiry(credential, supplier);

        assertThat(reads).hasValue(2);
    }

    @Test
    void testExpiryIsKeptUntilTheCredentialChanges() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        generateKey(keyStore, "key", "-1d", 30);
        final AtomicInteger reads = new AtomicInteger();

        reader.getExpiry(createCredential(CredentialType.KEY_STORE, null, 1L), () -> {
            reads.incrementAndGet();
            return keyStoreSecret(keyStore);
        });
        reader.getExpiry(createCredential(CredentialType.KEY_STORE, null, 1L), () -> {
            reads.incrementAndGet();
            return keyStoreSecret(keyStore);
        });
        assertThat(reads).hasValue(1);

        // Its secret replaced
        reader.getExpiry(createCredential(CredentialType.KEY_STORE, null, 2L), () -> {
            reads.incrementAndGet();
            return keyStoreSecret(keyStore);
        });
        assertThat(reads).hasValue(2);
    }

    @Test
    void testKeyStoreFileIsReadAgainWhenTheFileChanges() throws Exception {
        final Path keyStore = allowedDir.resolve("client.p12");
        generateKey(keyStore, "key", "-1d", 30);
        final Credential credential = createCredential(CredentialType.KEY_STORE_FILE, keyStore, 1L);

        final Instant first = reader.getExpiry(credential, () -> keyStoreFileSecret(credential, keyStore));

        // Replaced on disk, e.g. by cert-manager, with a certificate that lasts longer
        Files.delete(keyStore);
        final Path renewed = generateKey(keyStore, "key", "-1d", 90);
        Files.setLastModifiedTime(keyStore, FileTime.from(Instant.now().plusSeconds(60)));

        final Instant second = reader.getExpiry(credential, () -> keyStoreFileSecret(credential, keyStore));

        assertThat(second)
                .isEqualTo(getNotAfter(renewed))
                .isAfter(first);
    }

    private static Credential createCredential(final CredentialType credentialType,
                                               final Path keyStorePath,
                                               final long updateTimeMs) {
        return new Credential("uuid-1",
                "a credential",
                1L,
                updateTimeMs,
                "creator",
                "creator",
                credentialType,
                KeyStoreType.PKCS12,
                keyStorePath == null
                        ? null
                        : keyStorePath.toString(),
                null);
    }

    private static StoredSecret keyStoreSecret(final Path keyStore) {
        try {
            return new StoredSecret(createCredential(CredentialType.KEY_STORE, null, 1L),
                    new KeyStoreSecret("ks-uuid", KeyStoreType.PKCS12, PASSWORD, null),
                    Files.readAllBytes(keyStore));
        } catch (final IOException e) {
            throw new RuntimeException(e);
        }
    }

    private static StoredSecret keyStoreFileSecret(final Credential credential, final Path keyStore) {
        return new StoredSecret(credential,
                new KeyStoreFileSecret(KeyStoreType.PKCS12, keyStore.toString(), PASSWORD),
                null);
    }

    /// Makes a key with a self-signed certificate in a PKCS12 key store, with keytool as the JDK
    /// has no API for making certificates.
    ///
    /// @param startDate When the certificate starts, relative to now, e.g. `-100d`.
    /// @param days      How many days it lasts.
    /// @return The key's certificate, exported to a file.
    static Path generateKey(final Path keyStore,
                            final String alias,
                            final String startDate,
                            final int days) throws Exception {
        keytool("-genkeypair",
                "-alias", alias,
                "-keyalg", "EC",
                "-groupname", "secp256r1",
                "-dname", "CN=" + alias,
                "-startdate", startDate,
                "-validity", String.valueOf(days),
                "-keystore", keyStore.toString(),
                "-storetype", "PKCS12",
                "-storepass", PASSWORD);
        return exportCertificate(keyStore, alias);
    }

    private static Path exportCertificate(final Path keyStore, final String alias) throws Exception {
        final Path certificate = keyStore.resolveSibling(keyStore.getFileName() + "-" + alias + ".cer");
        Files.deleteIfExists(certificate);
        keytool("-exportcert",
                "-alias", alias,
                "-file", certificate.toString(),
                "-keystore", keyStore.toString(),
                "-storetype", "PKCS12",
                "-storepass", PASSWORD);
        return certificate;
    }

    private static void importCertificate(final Path keyStore,
                                          final String alias,
                                          final Path certificate) throws Exception {
        keytool("-importcert",
                "-noprompt",
                "-alias", alias,
                "-file", certificate.toString(),
                "-keystore", keyStore.toString(),
                "-storetype", "PKCS12",
                "-storepass", PASSWORD);
    }

    private static void keytool(final String... args) throws Exception {
        final List<String> command = new ArrayList<>();
        command.add(Path.of(System.getProperty("java.home"), "bin", "keytool").toString());
        command.addAll(List.of(args));
        final Process process = new ProcessBuilder(command)
                .redirectErrorStream(true)
                .start();
        final String output = new String(process.getInputStream().readAllBytes());
        assertThat(process.waitFor(60, TimeUnit.SECONDS)).isTrue();
        assertThat(process.exitValue())
                .withFailMessage("keytool failed: %s", output)
                .isZero();
    }

    private static Instant getNotAfter(final Path certificate) throws Exception {
        try (final InputStream inputStream = Files.newInputStream(certificate)) {
            final X509Certificate x509Certificate = (X509Certificate) CertificateFactory.getInstance("X.509")
                    .generateCertificate(inputStream);
            return x509Certificate.getNotAfter().toInstant();
        }
    }
}
