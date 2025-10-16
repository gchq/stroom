package stroom.credentials.impl.db;

import stroom.credentials.impl.CredentialsDao;
import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.google.inject.Guice;
import jakarta.inject.Inject;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.IOException;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Basic sanity tests for the CredentialsDao.
 */
@ExtendWith(MockitoExtension.class)
public class TestCredentialsDaoImpl {
    @SuppressWarnings("unused")
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(TestCredentialsDaoImpl.class);

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
    void testDao() throws IOException {
        final String uuid = UUID.randomUUID().toString();
        final CredentialsSecret secret = new CredentialsSecret(
                uuid,
                "username",
                "password",
                null,
                null,
                null,
                null);
        final long expires = System.currentTimeMillis();

        final Credentials credentials1 = new Credentials("Test creds",
                uuid,
                CredentialsType.USERNAME_PASSWORD,
                false,
                expires);

        credentialsDao.createCredentials(credentials1);
        credentialsDao.storeSecret(secret);

        // Get one item out
        final Credentials credentials2 = credentialsDao.getCredentials(uuid);
        final CredentialsSecret secret2 = credentialsDao.getSecret(uuid);

        assertThat(credentials2.getName()).isEqualTo("Test creds");
        assertThat(credentials2.getUuid()).isEqualTo(uuid);
        assertThat(credentials2.getType()).isEqualTo(CredentialsType.USERNAME_PASSWORD);
        assertThat(credentials2.isCredsExpire()).isEqualTo(false);
        assertThat(credentials2.getExpires()).isEqualTo(expires);
        assertThat(secret2).isEqualTo(secret);

        // Update the credentials, then check again
        credentials2.setName("Test creds 2");
        credentialsDao.storeCredentials(credentials2);
        final Credentials credentials3 = credentialsDao.getCredentials(uuid);
        assertThat(credentials3.getName()).isEqualTo("Test creds 2");

        // Update the secrets, then check again
        secret2.setPassphrase("foobar");
        credentialsDao.storeSecret(secret2);
        final CredentialsSecret secret3 = credentialsDao.getSecret(uuid);
        assertThat(secret3.getPassphrase()).isEqualTo("foobar");

        // Get all items out
        final List<Credentials> list = credentialsDao.listCredentials();
        assertThat(list.size()).isEqualTo(1);
        final Credentials cl1 = list.getFirst();
        assertThat(cl1.getName()).isEqualTo("Test creds 2");
        assertThat(cl1.getUuid()).isEqualTo(uuid);
        assertThat(cl1.getType()).isEqualTo(CredentialsType.USERNAME_PASSWORD);
        assertThat(cl1.isCredsExpire()).isEqualTo(false);
        assertThat(cl1.getExpires()).isEqualTo(expires);
        final CredentialsSecret cl1Secret = credentialsDao.getSecret(cl1.getUuid());
        assertThat(cl1Secret).isEqualTo(secret3);

        // Get all of type USERNAME_PASSWORD
        final List<Credentials> listOfUP = credentialsDao.listCredentials(CredentialsType.USERNAME_PASSWORD);
        assertThat(listOfUP.size()).isEqualTo(1);

        // Try other types
        final List<Credentials> listOfAT = credentialsDao.listCredentials(CredentialsType.ACCESS_TOKEN);
        assertThat(listOfAT.size()).isEqualTo(0);
        final List<Credentials> listOfPC = credentialsDao.listCredentials(CredentialsType.PRIVATE_CERT);
        assertThat(listOfPC.size()).isEqualTo(0);
    }

}
