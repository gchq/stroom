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
        final CredentialsSecret secret = new CredentialsSecret(
                "username",
                "password",
                null,
                null,
                null);
        final String uuid = UUID.randomUUID().toString();
        final long expires = System.currentTimeMillis();

        final Credentials credentials1 = new Credentials("Test creds",
                uuid,
                CredentialsType.USERNAME_PASSWORD,
                false,
                expires,
                secret);

        credentialsDao.store(credentials1);

        // Get one item out
        final Credentials credentials2 = credentialsDao.get(uuid);

        assertThat(credentials2.getName()).isEqualTo("Test creds");
        assertThat(credentials2.getUuid()).isEqualTo(uuid);
        assertThat(credentials2.getType()).isEqualTo(CredentialsType.USERNAME_PASSWORD);
        assertThat(credentials2.getExpires()).isEqualTo(expires);
        assertThat(credentials2.getSecret()).isEqualTo(secret);

        // Get all items out
        final List<Credentials> list = credentialsDao.list();
        assertThat(list.size()).isEqualTo(1);
        final Credentials cl1 = list.getFirst();
        assertThat(cl1.getName()).isEqualTo("Test creds");
        assertThat(cl1.getUuid()).isEqualTo(uuid);
        assertThat(cl1.getType()).isEqualTo(CredentialsType.USERNAME_PASSWORD);
        assertThat(cl1.getExpires()).isEqualTo(expires);
        assertThat(cl1.getSecret()).isEqualTo(secret);
    }

}
