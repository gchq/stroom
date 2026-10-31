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

package stroom.ai.impl;

import java.net.Socket;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import javax.net.ssl.SSLEngine;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509ExtendedTrustManager;
import javax.net.ssl.X509TrustManager;

/// Trusts what another trust manager trusts, without checking that a server's certificate names the host
/// connected to, for when 'verify hostname' is turned off in an MCP tool server's TLS configuration.
///
/// The JDK's HTTP client, which MCP connections use, has no setting for this. It checks the host name in the
/// handshake through an [X509ExtendedTrustManager], or after a plain [X509TrustManager] has checked the
/// certificate. This is an extended one that checks only the certificate, so the host name is not checked.
final class HostnameIgnoringTrustManager extends X509ExtendedTrustManager {

    private final X509TrustManager delegate;

    private HostnameIgnoringTrustManager(final X509TrustManager delegate) {
        this.delegate = delegate;
    }

    /// @param trustManagers The trust managers that decide which certificates are trusted.
    /// @return The same, but not checking host names.
    static TrustManager[] wrap(final TrustManager[] trustManagers) {
        final TrustManager[] wrapped = new TrustManager[trustManagers.length];
        for (int i = 0; i < trustManagers.length; i++) {
            wrapped[i] = trustManagers[i] instanceof final X509TrustManager x509
                    ? new HostnameIgnoringTrustManager(x509)
                    : trustManagers[i];
        }
        return wrapped;
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType, final Socket socket)
            throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkClientTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine)
            throws CertificateException {
        delegate.checkClientTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType)
            throws CertificateException {
        delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType, final Socket socket)
            throws CertificateException {
        // Without the socket, so the delegate does not check the host name.
        delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public void checkServerTrusted(final X509Certificate[] chain, final String authType, final SSLEngine engine)
            throws CertificateException {
        // Without the engine, so the delegate does not check the host name.
        delegate.checkServerTrusted(chain, authType);
    }

    @Override
    public X509Certificate[] getAcceptedIssuers() {
        return delegate.getAcceptedIssuers();
    }
}
