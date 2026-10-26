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

package stroom.credentials.impl.dao;

import stroom.ai.shared.KeyStoreType;
import stroom.credentials.api.StoredSecret;
import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
import stroom.credentials.shared.KeyStoreFileSecret;
import stroom.credentials.shared.Secret;
import stroom.credentials.shared.UsernamePasswordSecret;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.PageRequest;
import stroom.util.shared.ResultPage;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Basic sanity tests for the CredentialsDao.
 */
@ExtendWith(MockitoExtension.class)
public class TestCredentialDaoImpl {

    @SuppressWarnings("unused")
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCredentialDaoImpl.class);

    @SuppressWarnings("unused")
    @Inject
    private CredentialsDao credentialsDao;

    @SuppressWarnings("unused")
    @Inject
    private CredentialsDaoImpl credentialsDaoImpl;

    @BeforeEach
    void setup() {
        Guice.createInjector(new TestModule()).injectMembers(this);
        credentialsDaoImpl.clear();
    }

    @AfterEach
    void cleanup() {
        credentialsDaoImpl.clear();
    }

    @Test
    void testDao() {
        final String clientUuid = "dummy";
        final long expires = System.currentTimeMillis();

        final Credential credential = createCred(clientUuid, "Test creds", expires);

        final Secret secret = new UsernamePasswordSecret("username", "password");
        final StoredSecret storedSecret = new StoredSecret(credential, secret, null);
        credentialsDao.putStoredSecret(storedSecret, false);

        // Get one item out
        final Credential credential2 = credentialsDao.getCredentialByUuid(clientUuid);
        final StoredSecret secret2 = credentialsDao.getStoredSecretByName(credential.getName());

        assertThat(credential2.getName()).isEqualTo("Test creds");
        assertThat(credential2.getUuid()).isEqualTo(clientUuid);
        assertThat(credential2.getCredentialType()).isEqualTo(CredentialType.USERNAME_PASSWORD);
        assertThat(credential2.getExpiryTimeMs()).isEqualTo(expires);
        assertThat(secret2).isEqualTo(storedSecret);

        // Update the credentials, then check again
        final Credential credential3 = createCred(clientUuid, "Test creds 2", expires);
        credentialsDao.putStoredSecret(new StoredSecret(credential3, secret, null), true);
        final Credential credential4 = credentialsDao.getCredentialByUuid(clientUuid);
        assertThat(credential4.getName()).isEqualTo("Test creds 2");

        // Update the secrets, then check again
        final Secret secret3 = new UsernamePasswordSecret("username", "foobar");
        credentialsDao.putStoredSecret(new StoredSecret(credential3, secret3, null), true);
        final StoredSecret secret4 = credentialsDao.getStoredSecretByName(credential3.getName());
        assertThat(((UsernamePasswordSecret) secret4.secret()).getPassword()).isEqualTo("foobar");

        // Get all items out
        final ResultPage<CredentialWithPerms> list = find(null);
        assertThat(list.size()).isEqualTo(1);
        final CredentialWithPerms credentialWithPerms = list.getFirst();
        final Credential cred = credentialWithPerms.getCredential();
        assertThat(cred.getName()).isEqualTo("Test creds 2");
        assertThat(cred.getUuid()).isEqualTo(clientUuid);
        assertThat(cred.getCredentialType()).isEqualTo(CredentialType.USERNAME_PASSWORD);
        assertThat(cred.getExpiryTimeMs()).isEqualTo(expires);
        final StoredSecret cl1Secret = credentialsDao.getStoredSecretByName(cred.getName());
        assertThat(cl1Secret).isEqualTo(secret4);

        // Get all of type USERNAME_PASSWORD
        final ResultPage<CredentialWithPerms> listOfUP = find(CredentialType.USERNAME_PASSWORD);
        assertThat(listOfUP.size()).isEqualTo(1);

        // Try other types
        final ResultPage<CredentialWithPerms> listOfAT = find(CredentialType.ACCESS_TOKEN);
        assertThat(listOfAT.size()).isEqualTo(0);
        final ResultPage<CredentialWithPerms> listOfPC = find(CredentialType.SSH_KEY);
        assertThat(listOfPC.size()).isEqualTo(0);
    }

    /// gh-5815: updating the settings of a credential must leave its secret as it is.
    @Test
    void testUpdateCredentialKeepsTheSecret() {
        final Credential credential = createCred("dummy", "Test creds", 1_000L);
        final Secret secret = new UsernamePasswordSecret("username", "password");
        credentialsDao.putStoredSecret(new StoredSecret(credential, secret, null), false);

        credentialsDao.updateCredential(createCred("dummy", "Renamed creds", 2_000L));

        final StoredSecret stored = credentialsDao.getStoredSecretByName("Renamed creds");
        assertThat(stored).isNotNull();
        assertThat(stored.secret()).isEqualTo(secret);
        assertThat(stored.credential().getExpiryTimeMs()).isEqualTo(2_000L);
        assertThat(stored.credential().getCredentialType()).isEqualTo(CredentialType.USERNAME_PASSWORD);
        assertThat(credentialsDao.getStoredSecretByName("Test creds")).isNull();
        assertThat(credentialsDao.updateCredential(createCred("missing", "Missing creds", 2_000L))).isFalse();
    }

    /// gh-5796: the path of a key store file credential is stored where it can be shown, and is
    /// kept when the settings are updated.
    @Test
    void testKeyStorePath() {
        final Credential credential = new Credential("file",
                "File creds",
                1_000L,
                1_000L,
                "admin",
                "admin",
                CredentialType.KEY_STORE_FILE,
                KeyStoreType.PKCS12,
                "/etc/certs/client.p12",
                null);
        final Secret secret = new KeyStoreFileSecret(KeyStoreType.PKCS12, "/etc/certs/client.p12", "changeit");
        credentialsDao.putStoredSecret(new StoredSecret(credential, secret, null), false);

        assertThat(credentialsDao.getCredentialByUuid("file").getKeyStorePath())
                .isEqualTo("/etc/certs/client.p12");
        assertThat(credentialsDao.getStoredSecretByName("File creds").secret())
                .isEqualTo(secret);

        credentialsDao.updateCredential(new Credential("file",
                "Renamed file creds",
                1_000L,
                2_000L,
                "admin",
                "admin",
                CredentialType.KEY_STORE_FILE,
                null,
                null,
                null));
        assertThat(credentialsDao.getCredentialByUuid("file").getKeyStorePath())
                .isEqualTo("/etc/certs/client.p12");

        credentialsDao.updateSecret(new StoredSecret(new Credential("file",
                null,
                null,
                3_000L,
                null,
                "admin",
                null,
                KeyStoreType.JKS,
                "/etc/certs/new.jks",
                null),
                new KeyStoreFileSecret(KeyStoreType.JKS, "/etc/certs/new.jks", "changeit"),
                null));
        final Credential updated = credentialsDao.getCredentialByUuid("file");
        assertThat(updated.getKeyStorePath())
                .isEqualTo("/etc/certs/new.jks");
        assertThat(updated.getKeyStoreType())
                .isEqualTo(KeyStoreType.JKS);
        assertThat(updated.getName())
                .isEqualTo("Renamed file creds");
    }

    /// Replacing a secret must not touch the name, so a rename made at the same time is kept.
    @Test
    void testUpdateSecretKeepsTheName() {
        final Credential credential = createCred("dummy", "Test creds", 1_000L);
        credentialsDao.putStoredSecret(
                new StoredSecret(credential, new UsernamePasswordSecret("username", "password"), null),
                false);
        credentialsDao.updateCredential(createCred("dummy", "Renamed creds", 1_000L));

        final Secret newSecret = new UsernamePasswordSecret("username", "new password");
        final boolean updated = credentialsDao.updateSecret(
                new StoredSecret(createCred("dummy", "Test creds", 3_000L), newSecret, null));

        assertThat(updated).isTrue();
        final StoredSecret stored = credentialsDao.getStoredSecretByUuid("dummy");
        assertThat(stored.secret()).isEqualTo(newSecret);
        assertThat(stored.credential().getName()).isEqualTo("Renamed creds");
        assertThat(stored.credential().getExpiryTimeMs()).isEqualTo(3_000L);
        assertThat(credentialsDao.updateSecret(
                new StoredSecret(createCred("missing", "Missing creds", 3_000L), newSecret, null))).isFalse();
    }

    /// Inserting a credential with a name that is already taken must fail, not re-key the
    /// existing credential to the new UUID.
    @Test
    void testInsertWithTakenNameDoesNotTakeOverTheExistingCredential() {
        final Secret secret = new UsernamePasswordSecret("username", "password");
        credentialsDao.putStoredSecret(
                new StoredSecret(createCred("first", "Test creds", 1_000L), secret, null),
                false);

        assertThatThrownBy(() -> credentialsDao.putStoredSecret(
                new StoredSecret(createCred("second", "Test creds", 1_000L),
                        new UsernamePasswordSecret("other", "other"),
                        null),
                false))
                .isInstanceOf(RuntimeException.class);

        assertThat(credentialsDao.getStoredSecretByUuid("first").secret()).isEqualTo(secret);
        assertThat(credentialsDao.getStoredSecretByUuid("second")).isNull();
    }

    /**
     * The quick filter queries on a debounce as the user types, so partially typed input is an
     * expected transient state. It must match no rows rather than propagate a parse error.
     */
    @Test
    void testFind_partiallyTypedFilterDoesNotThrow() {
        final Credential credential = createCred("dummy", "Test creds", System.currentTimeMillis());
        credentialsDao.putStoredSecret(
                new StoredSecret(credential, new UsernamePasswordSecret("username", "password"), null),
                false);

        // Sanity check - the row is findable with no filter, so an empty result below means the
        // filter excluded it rather than there being nothing to find.
        assertThat(findByFilter(null).size()).isEqualTo(1);
        assertThat(findByFilter("Test").size()).isEqualTo(1);

        // An incomplete field qualifier, as typed on the way to something valid.
        assertThat(findByFilter("foo:").size()).isEqualTo(0);
        assertThat(findByFilter("foo:bar").size()).isEqualTo(0);
        // Unbalanced quote, likewise.
        assertThat(findByFilter("\"Test").size()).isEqualTo(0);
    }

    private ResultPage<CredentialWithPerms> findByFilter(final String filter) {
        final FindCredentialRequest request = new FindCredentialRequest(
                PageRequest.unlimited(),
                null,
                filter,
                null,
                null);
        return credentialsDao.findCredentialsWithPermissions(request, cred ->
                new CredentialWithPerms(cred, true, true));
    }

    private Credential createCred(final String uuid,
                                  final String name,
                                  final long expires) {
        final long now = System.currentTimeMillis();
        return new Credential(
                uuid,
                name,
                now,
                now,
                "admin",
                "admin",
                CredentialType.USERNAME_PASSWORD,
                null,
                expires);
    }

    private FindCredentialRequest createRequest(final CredentialType credentialType) {
        return new FindCredentialRequest(
                PageRequest.unlimited(),
                null,
                null,
                credentialType == null
                        ? null
                        : Set.of(credentialType),
                null);
    }

    private ResultPage<CredentialWithPerms> find(final CredentialType credentialType) {
        return credentialsDao.findCredentialsWithPermissions(
                createRequest(credentialType), cred ->
                        new CredentialWithPerms(cred, true, true));
    }
}
