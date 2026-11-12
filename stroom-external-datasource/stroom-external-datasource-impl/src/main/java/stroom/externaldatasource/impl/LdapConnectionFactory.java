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

package stroom.externaldatasource.impl;

import com.unboundid.ldap.sdk.ExtendedResult;
import com.unboundid.ldap.sdk.LDAPConnection;
import com.unboundid.ldap.sdk.LDAPConnectionOptions;
import com.unboundid.ldap.sdk.LDAPException;
import com.unboundid.ldap.sdk.ResultCode;
import com.unboundid.ldap.sdk.extensions.StartTLSExtendedRequest;
import com.unboundid.util.ssl.HostNameSSLSocketVerifier;
import com.unboundid.util.ssl.JVMDefaultTrustManager;
import com.unboundid.util.ssl.SSLUtil;
import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.security.GeneralSecurityException;
import java.util.concurrent.TimeUnit;
import javax.net.ssl.TrustManager;

/// Opens a bound connection to an LDAP directory.
///
/// Each query has a connection of its own, as Amazon Athena queries have a client of their own:
/// connecting and binding are cheap next to a search, and a connection is never shared between users.
///
/// - TLS trusts the certificates of Stroom's JVM trust store, and checks the certificate names the host.
/// - Referrals are not followed, so a search never reaches a server the allow list did not name.
/// - Nothing is ever resolved into a Java object: this is the UnboundID SDK, not JNDI.
@Singleton
class LdapConnectionFactory {

    // The longest wait to connect, whatever the query timeout.
    private static final int MAX_CONNECT_TIMEOUT_SECONDS = 30;

    private final TrustManager trustManager;

    /// Creates a factory trusting the certificates of the JVM's trust store. Guice calls this.
    @Inject
    LdapConnectionFactory() {
        this(JVMDefaultTrustManager.getInstance());
    }

    /// Creates a factory trusting what a trust manager trusts, for tests.
    ///
    /// @param trustManager Decides which server certificates are trusted.
    LdapConnectionFactory(final TrustManager trustManager) {
        this.trustManager = trustManager;
    }

    /// Connects, starts TLS if the connection asks for it, and binds.
    ///
    /// @param connection     The resolved connection.
    /// @param timeoutSeconds The longest any one request may wait for the directory.
    /// @return The bound connection, which the caller must close.
    /// @throws LDAPException            If the directory cannot be reached, TLS fails or the bind is refused.
    /// @throws GeneralSecurityException If TLS cannot be set up.
    LDAPConnection connect(final ResolvedLdapConnection connection,
                           final int timeoutSeconds) throws LDAPException, GeneralSecurityException {
        final LDAPConnectionOptions options = new LDAPConnectionOptions();
        options.setFollowReferrals(false);
        options.setConnectTimeoutMillis((int) TimeUnit.SECONDS.toMillis(
                Math.min(timeoutSeconds, MAX_CONNECT_TIMEOUT_SECONDS)));
        options.setResponseTimeoutMillis(TimeUnit.SECONDS.toMillis(timeoutSeconds));
        options.setAbandonOnTimeout(true);
        options.setSSLSocketVerifier(new HostNameSSLSocketVerifier(true));
        final SSLUtil sslUtil = new SSLUtil(trustManager);

        final LDAPConnection ldapConnection = connection.ldaps()
                ? new LDAPConnection(sslUtil.createSSLSocketFactory(), options, connection.host(), connection.port())
                : new LDAPConnection(options, connection.host(), connection.port());
        try {
            if (connection.startTls()) {
                final ExtendedResult result = ldapConnection.processExtendedOperation(
                        new StartTLSExtendedRequest(sslUtil.createSSLContext()));
                if (result.getResultCode() != ResultCode.SUCCESS) {
                    throw new LDAPException(result.getResultCode(), "The directory refused StartTLS: "
                                                                    + result.getDiagnosticMessage());
                }
            }
            ldapConnection.bind(connection.bindDn(), connection.password());
            return ldapConnection;
        } catch (final LDAPException | GeneralSecurityException | RuntimeException e) {
            ldapConnection.close();
            throw e;
        }
    }
}
