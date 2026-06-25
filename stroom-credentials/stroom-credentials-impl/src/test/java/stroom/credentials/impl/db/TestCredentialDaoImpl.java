package stroom.credentials.impl.db;

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
