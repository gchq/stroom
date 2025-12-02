package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;
import stroom.credentials.shared.CredentialsSecret;
import stroom.credentials.shared.CredentialsType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.sshd.common.Property;
import org.apache.sshd.common.Property.StringProperty;
import org.apache.sshd.common.util.security.SecurityUtils;
import org.eclipse.jgit.api.TransportConfigCallback;
import org.eclipse.jgit.transport.SshSessionFactory;
import org.eclipse.jgit.transport.SshTransport;
import org.eclipse.jgit.transport.Transport;
import org.eclipse.jgit.transport.sshd.SshdSessionFactoryBuilder;
import org.jspecify.annotations.NullMarked;

import java.io.ByteArrayInputStream;
import java.io.File;
import java.io.IOException;
import java.security.GeneralSecurityException;
import java.security.KeyPair;
import java.util.Objects;

@NullMarked
public class CredentialsJgitSshTransportCallback implements TransportConfigCallback {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(CredentialsJgitSshTransportCallback.class);

    /** DAO to get credentials given the credentialsID */
    private final CredentialsDao credentialsDao;

    /** Temporary directory to use for home and ssh directories. Not used but must exist. */
    private final File tempDir;

    /** The ID of the credentials object within the database */
    private final String credentialsId;

    /** Comma separated list of authentication mechanisms */
    private static final String PREFERRED_AUTHENTICATIONS = "publickey";

    /**
     * Constructor
     * @param credentialsId The ID of the credentials object in the database. Must not be null.
     */
    public CredentialsJgitSshTransportCallback(final CredentialsDao credentialsDao,
                                               final File tempDir,
                                               final String credentialsId) {

        Objects.requireNonNull(credentialsDao);
        Objects.requireNonNull(credentialsId);
        Objects.requireNonNull(tempDir);

        this.credentialsDao = credentialsDao;
        this.credentialsId = credentialsId;
        this.tempDir = tempDir;
    }

    /**
     * Callback to configure the Transport link.
     * @param transport a {@link org.eclipse.jgit.transport.Transport} object.
     */
    @Override
    public void configure(final Transport transport) {
        LOGGER.debug("Configuring transport ");

        if (transport instanceof final SshTransport sshTransport) {

            try {
                // Create key pair from DB
                final Credentials credentials = credentialsDao.getCredentials(credentialsId);
                final CredentialsSecret secret = credentialsDao.getSecret(credentialsId);
                final CredentialsJgitSshServerKeyDatabase serverKeyDatabase =
                        new CredentialsJgitSshServerKeyDatabase(secret);

                if (credentials.getType() == CredentialsType.PRIVATE_CERT) {
                    LOGGER.debug("Configuring transport to use credentials: {}", credentials);

                    // Store the name of the credentials to improve error messages
                    final Property.StringProperty credentialsName = new StringProperty(credentials.getName());

                    // Generate the key pair from the private key using the passphrase
                    final Iterable<KeyPair> keyPairs = SecurityUtils.loadKeyPairIdentities(
                            null,
                            credentialsName,
                            new ByteArrayInputStream(secret.getPrivateKey().getBytes()),
                            (sessionContext,
                             namedResource,
                             retryIndex) -> secret.getPassphrase());

                    final SshSessionFactory sshFactory = new SshdSessionFactoryBuilder()
                            .setPreferredAuthentications(PREFERRED_AUTHENTICATIONS)
                            .setDefaultKeysProvider(ignored -> keyPairs)
                            .setHomeDirectory(tempDir)
                            .setSshDirectory(tempDir)
                            .setServerKeyDatabase((ignoredHomeDir, ignoredSshDir) -> serverKeyDatabase)
                            .build(null);

                    sshTransport.setSshSessionFactory(sshFactory);
                }

            } catch (final IOException | GeneralSecurityException e) {
                LOGGER.error("Error configuring credentials for transport: {}", e.getMessage(), e);
                throw new RuntimeException("Error configuring credentials for transport: " + e.getMessage(), e);
            }
        }
    }
}
