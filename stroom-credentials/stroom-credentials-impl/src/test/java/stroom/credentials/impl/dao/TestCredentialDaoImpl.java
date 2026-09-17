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

import stroom.credentials.api.StoredSecret;
import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credential;
import stroom.credentials.shared.CredentialType;
import stroom.credentials.shared.CredentialWithPerms;
import stroom.credentials.shared.FindCredentialRequest;
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
