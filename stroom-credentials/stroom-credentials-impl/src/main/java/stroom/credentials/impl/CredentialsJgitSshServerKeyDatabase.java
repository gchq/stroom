/*
 * Copyright 2016-2025 Crown Copyright
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

import stroom.credentials.shared.CredentialsSecret;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.sshd.common.config.keys.KeyUtils;
import org.apache.sshd.common.config.keys.PublicKeyEntry;
import org.apache.sshd.common.config.keys.PublicKeyEntryResolver;
import org.eclipse.jgit.transport.CredentialsProvider;
import org.eclipse.jgit.transport.sshd.ServerKeyDatabase;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.security.GeneralSecurityException;
import java.security.PublicKey;
import java.util.Collections;
import java.util.List;

/**
 * Class to check if the public key from the server matches the public key we've
 * got stored for that server.
 */
public class CredentialsJgitSshServerKeyDatabase implements ServerKeyDatabase {

    private static final LambdaLogger LOGGER =
            LambdaLoggerFactory.getLogger(CredentialsJgitSshServerKeyDatabase.class);

    /** Talks to credentials DAO to get the server's public key for this connection */
    private final CredentialsSecret secret;

    /**
     * Constructor.
     * @param secret The secrets we want to use for the connection.
     */
    public CredentialsJgitSshServerKeyDatabase(final CredentialsSecret secret) {
        this.secret = secret;
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
        LOGGER.debug("Lookup for connectAddress='{}', remoteAddress='{}', conf='{}'",
                connectAddress,
                remoteAddress,
                configuration);

        // Ignore here; handle in accept()
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
        LOGGER.debug("Accept for connectAddress='{}', remoteAddress='{}', serverPublicKey='{}', conf='{}', creds='{}'",
                connectAddress,
                remoteAddress,
                serverPublicKey,
                configuration,
                credentialsProvider);

        boolean accepted = false;

        // Get server key from credentials
        final String storedServerPublicKeyString = secret.getServerPublicKey();

        if (storedServerPublicKeyString == null || storedServerPublicKeyString.isBlank()) {
            LOGGER.warn("No server public key stored for '{}'; accepting server.", connectAddress);
            accepted = true;
        } else {
            final PublicKeyEntry storedServerPublicKeyEntry =
                    PublicKeyEntry.parsePublicKeyEntry(storedServerPublicKeyString);
            final PublicKey storedServerPublicKey;
            try {
                storedServerPublicKey = storedServerPublicKeyEntry.resolvePublicKey(
                        null,
                        null,
                        PublicKeyEntryResolver.IGNORING);
            } catch (final IOException | GeneralSecurityException e) {
                LOGGER.error("Error resolving public key for '{}': {}", connectAddress, e.getMessage(), e);
                throw new CredentialsJgitRuntimeException("Error resolving public key for '"
                                                          + connectAddress + "': " + e.getMessage(), e);
            }

            accepted = KeyUtils.compareKeys(serverPublicKey, storedServerPublicKey);
            if (accepted) {
                LOGGER.debug("The server public key for '{}' matches the stored server public key. "
                             + "Connection permitted.", connectAddress);
            } else {
                LOGGER.warn("The server public key for '{}' does not match the stored server public key. "
                        + "Connection not permitted.", connectAddress);
            }
        }

        return accepted;
    }

}
