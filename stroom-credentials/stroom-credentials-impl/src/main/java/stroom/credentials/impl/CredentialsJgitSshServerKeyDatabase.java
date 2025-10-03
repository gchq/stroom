package stroom.credentials.impl;

import stroom.credentials.shared.Credentials;

import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.InetSocketAddress;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

public class CredentialsJgitSshServerKeyDatabase implements ServerKeyDatabase {

    /** Talks to credentials DAO to get the server's public key for this connection */
    private final Credentials credentials;

    /** Logger to figure out what is going on */
    private static final Logger LOGGER =
            LoggerFactory.getLogger(CredentialsJgitSshServerKeyDatabase.class);

    /**
     * Constructor.
     */
    public CredentialsJgitSshServerKeyDatabase(final Credentials credentials) {
        this.credentials = credentials;
    }

    /**
     * Retrieves all known and not revoked host keys for the given addresses.
     * @param connectAddress IP address the session tried to connect to
     * @param remoteAddress  IP address as reported for the remote end point
     * @param configuration  giving access to potentially interesting configuration settings
     * @return the list of known and not revoked keys for the given addresses
     */
    @Override
    public List<PublicKey> lookup(final String connectAddress,
                                  final InetSocketAddress remoteAddress,
                                  final Configuration configuration) {
        LOGGER.info("Lookup for connectAddress='{}', remoteAddress='{}', conf='{}'",
                connectAddress,
                remoteAddress,
                configuration);

        return Collections.emptyList();
    }

    /**
     * Determines whether to accept a received server host key.
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
        LOGGER.info("Accept for connectAddress='{}', remoteAddress='{}', serverPublicKey='{}', conf='{}', creds='{}'",
                connectAddress,
                remoteAddress,
                serverPublicKey,
                configuration,
                credentialsProvider);

        boolean accepted = false;

        // TODO Get server key from credentials

        // TODO Compare that server key to value passed in

        accepted = true;

        return accepted;
    }

}
