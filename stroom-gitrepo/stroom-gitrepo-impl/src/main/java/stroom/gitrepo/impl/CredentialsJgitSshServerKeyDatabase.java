package stroom.gitrepo.impl;

import stroom.credentials.api.StoredSecret;
import stroom.credentials.shared.SshKeySecret;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.apache.sshd.common.config.keys.AuthorizedKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Class to check if the public key from the server matches the public key we've
 * got stored for that server.
 */
public class CredentialsJgitSshServerKeyDatabase implements ServerKeyDatabase {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(CredentialsJgitSshServerKeyDatabase.class);

    private final StoredSecret storedSecret;
    private final SshKeySecret sshKeySecret;

    public CredentialsJgitSshServerKeyDatabase(final StoredSecret storedSecret,
                                               final SshKeySecret sshKeySecret) {
        this.storedSecret = Objects.requireNonNull(storedSecret);
        this.sshKeySecret = Objects.requireNonNull(sshKeySecret);
    }

    /**
     * Retrieves all known and not revoked host keys for the given addresses.
     *
     * @param connectAddress IP address the session tried to connect to
     * @param remoteAddress  IP address as reported for the remote end point
     * @param configuration  giving access to potentially interesting configuration settings
     * @return the list of known and not revoked keys for the given addresses
     */
    @Override
    public List<PublicKey> lookup(final String connectAddress,
                                  final InetSocketAddress remoteAddress,
                                  final Configuration configuration) {
        LOGGER.debug("Lookup for connectAddress='{}', remoteAddress='{}', conf='{}'",
                connectAddress,
                remoteAddress,
                configuration);

        // Ignore here; handle in accept()
        return Collections.emptyList();
    }

    /**
     * Determines whether to accept a received server host key.
     *
     * @param connectAddress      IP address the session tried to connect to
     * @param remoteAddress       IP address as reported for the remote end point
     * @param serverPublicKey     received from the remote end
     * @param configuration       giving access to potentially interesting configuration settings
     * @param credentialsProvider for interacting with the user, if required; may be null
     * @return true if the serverKey is accepted, false otherwise
     */
    @Override
    public boolean accept(final String connectAddress,
                          final InetSocketAddress remoteAddress,
                          final PublicKey serverPublicKey,
                          final Configuration configuration,
                          final CredentialsProvider credentialsProvider) {
        LOGGER.debug("Accept for connectAddress='{}', remoteAddress='{}', serverPublicKey='{}', conf='{}', creds='{}'",
                connectAddress,
                remoteAddress,
                serverPublicKey,
                configuration,
                credentialsProvider);

        // If we aren't verifying hosts (dev only) then accept the connection.
        if (!sshKeySecret.isVerifyHosts()) {
            return true;
        }

        final String knownHostsString = NullSafe.string(sshKeySecret.getKnownHosts());
        final String[] knownHosts = knownHostsString.split("\n");
        for (final String knownHost : knownHosts) {
            try {
                // Parse: hostname keytype base64key
                final String[] parts = knownHost.split("\\s+", 3);
                if (parts.length > 0) {
                    if (parts.length >= 3) {
                        final String hostname = parts[0];
                        // See if this is an entry for the host we are trying to verify.
                        if (Objects.equals(hostname, connectAddress)) {
                            // Try to verify.
                            final PublicKeyEntry entry = AuthorizedKeyEntry.parseAuthorizedKeyEntry(knownHost);
                            if (entry != null) {
                                final PublicKey key = entry.resolvePublicKey(null, null, null);
                                // If the keys match then this host is verified.
                                if (serverPublicKey.equals(key)) {
                                    LOGGER.debug("The server public key for '{}' matches known hosts. "
                                                 + "Connection permitted.", connectAddress);
                                    return true;
                                }
                            }
                        }
                    } else {
                        LOGGER.warn("Unexpected format parsing known host for credential: {} {}",
                                storedSecret.credential().getName(),
                                knownHost);
                    }
                }
            } catch (final IOException | GeneralSecurityException e) {
                LOGGER.error("Error parsing known hosts for credential: {} {}\n{}",
                        storedSecret.credential().getName(),
                        knownHost,
                        e.getMessage(),
                        e);
            }
        }

        LOGGER.warn("The server public key for '{}' does not match known hosts. "
                    + "Connection not permitted.", connectAddress);
        return false;
    }
}
