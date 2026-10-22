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

package stroom.util.http;

import stroom.util.shared.NullSafe;

import io.dropwizard.client.proxy.NonProxyListProxyRoutePlanner;
import org.apache.hc.client5.http.auth.AuthScope;
import org.apache.hc.client5.http.auth.Credentials;
import org.apache.hc.client5.http.auth.CredentialsProvider;
import org.apache.hc.client5.http.auth.NTCredentials;
import org.apache.hc.client5.http.auth.UsernamePasswordCredentials;
import org.apache.hc.client5.http.impl.auth.BasicCredentialsProvider;
import org.apache.hc.client5.http.routing.HttpRoutePlanner;
import org.apache.hc.client5.http.ssl.ClientTlsStrategyBuilder;
import org.apache.hc.client5.http.ssl.DefaultHostnameVerifier;
import org.apache.hc.client5.http.ssl.HostnameVerificationPolicy;
import org.apache.hc.client5.http.ssl.NoopHostnameVerifier;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.nio.ssl.TlsStrategy;
import org.apache.hc.core5.ssl.PrivateKeyStrategy;
import org.apache.hc.core5.ssl.SSLContextBuilder;
import org.apache.hc.core5.ssl.SSLContexts;
import org.apache.hc.core5.ssl.TrustStrategy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.util.List;
import java.util.Objects;
import javax.net.ssl.SSLContext;

/// Turns the server side HTTP client configuration into the parts needed to build an Apache HttpClient 5
/// *async* client, e.g. the one used by the Elasticsearch Java client.
///
/// Dropwizard builds classic (blocking) clients from the same configuration, but has nothing for async
/// clients, so the TLS and proxy handling here deliberately mirrors Dropwizard's
/// `DropwizardSSLConnectionSocketFactory` and `HttpClientBuilder` so that a given configuration behaves the
/// same whichever kind of client it ends up in.
public final class HttpAsyncClientUtil {

    // Trusts a certificate chain of length one, i.e. a self-signed certificate. This is what the deprecated
    // `TrustSelfSignedStrategy` does.
    private static final TrustStrategy TRUST_SELF_SIGNED_STRATEGY = (chain, authType) -> chain.length == 1;

    private HttpAsyncClientUtil() {
        // Utility class
    }

    /// Creates a TLS strategy for an async client connection manager.
    ///
    /// @param tlsConfiguration The resolved TLS configuration, i.e. with real key/trust store paths and
    ///                         passwords.
    /// @return A TLS strategy to pass to `PoolingAsyncClientConnectionManagerBuilder.setTlsStrategy()`.
    /// @throws IllegalStateException If the key or trust store cannot be loaded or the SSL context cannot
    ///                               be created.
    public static TlsStrategy createTlsStrategy(final HttpTlsConfiguration tlsConfiguration) {
        Objects.requireNonNull(tlsConfiguration, "Null TLS configuration");

        final ClientTlsStrategyBuilder builder = ClientTlsStrategyBuilder
                .create()
                .setSslContext(createSslContext(tlsConfiguration));

        if (NullSafe.hasItems(tlsConfiguration.getSupportedProtocols())) {
            builder.setTlsVersions(toArray(tlsConfiguration.getSupportedProtocols()));
        }
        if (NullSafe.hasItems(tlsConfiguration.getSupportedCiphers())) {
            builder.setCiphers(toArray(tlsConfiguration.getSupportedCiphers()));
        }
        // Always set the hostname verifier explicitly and verify on the client side. The default (BUILTIN)
        // policy relies on JSSE endpoint identification, which httpclient5 5.6.1 sets on the SSL parameters
        // after they have been applied to the engine, so for async clients the hostname is never checked.
        builder.setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                .setHostnameVerifier(tlsConfiguration.isVerifyHostname()
                        ? new DefaultHostnameVerifier()
                        : NoopHostnameVerifier.INSTANCE);

        return builder.buildAsync();
    }

    /// Creates a TLS strategy for when there is no TLS configuration, i.e. one that trusts the JVM's default
    /// CAs and verifies hostnames.
    ///
    /// Use this rather than relying on the async client's own default, which does not verify hostnames with
    /// some versions of httpclient5 (see [#createTlsStrategy(HttpTlsConfiguration)]).
    ///
    /// @return A TLS strategy to pass to `PoolingAsyncClientConnectionManagerBuilder.setTlsStrategy()`.
    public static TlsStrategy createDefaultTlsStrategy() {
        return ClientTlsStrategyBuilder
                .create()
                .setSslContext(SSLContexts.createSystemDefault())
                .setHostVerificationPolicy(HostnameVerificationPolicy.CLIENT)
                .setHostnameVerifier(new DefaultHostnameVerifier())
                .buildAsync();
    }

    /// Creates an SSL context holding the key and trust material named in the TLS configuration.
    ///
    /// If no trust store is configured the JVM's default trust store is used.
    ///
    /// @param tlsConfiguration The resolved TLS configuration, i.e. with real key/trust store paths and
    ///                         passwords.
    /// @return The SSL context.
    /// @throws IllegalStateException If the key or trust store cannot be loaded or the SSL context cannot
    ///                               be created.
    public static SSLContext createSslContext(final HttpTlsConfiguration tlsConfiguration) {
        Objects.requireNonNull(tlsConfiguration, "Null TLS configuration");

        try {
            final SSLContextBuilder builder = SSLContextBuilder.create()
                    .setProtocol(tlsConfiguration.getProtocol());
            if (NullSafe.isNonBlankString(tlsConfiguration.getProvider())) {
                builder.setProvider(tlsConfiguration.getProvider());
            }

            if (NullSafe.isNonBlankString(tlsConfiguration.getKeyStorePath())) {
                final char[] password = toChars(tlsConfiguration.getKeyStorePassword());
                final KeyStore keyStore = loadKeyStore(
                        tlsConfiguration.getKeyStorePath(),
                        password,
                        tlsConfiguration.getKeyStoreType(),
                        tlsConfiguration.getKeyStoreProvider());
                builder.loadKeyMaterial(keyStore, password, createPrivateKeyStrategy(tlsConfiguration));
            }

            KeyStore trustStore = null;
            if (NullSafe.isNonBlankString(tlsConfiguration.getTrustStorePath())) {
                trustStore = loadKeyStore(
                        tlsConfiguration.getTrustStorePath(),
                        toChars(tlsConfiguration.getTrustStorePassword()),
                        tlsConfiguration.getTrustStoreType(),
                        tlsConfiguration.getTrustStoreProvider());
            }
            final TrustStrategy trustStrategy = tlsConfiguration.isTrustSelfSignedCertificates()
                    ? TRUST_SELF_SIGNED_STRATEGY
                    : null;
            builder.loadTrustMaterial(trustStore, trustStrategy);

            return builder.build();
        } catch (final GeneralSecurityException | IOException e) {
            throw new IllegalStateException("Unable to initialise SSL context: " + e.getMessage(), e);
        }
    }

    /// Creates a route planner that sends requests via the configured proxy, except for those to hosts in the
    /// proxy's non-proxy host list.
    ///
    /// @param proxyConfiguration The proxy configuration, which may be `null`.
    /// @return The route planner, or `null` if there is no proxy configured.
    public static HttpRoutePlanner createRoutePlanner(final HttpProxyConfiguration proxyConfiguration) {
        if (!isProxyConfigured(proxyConfiguration)) {
            return null;
        }
        return new NonProxyListProxyRoutePlanner(
                getProxyHost(proxyConfiguration),
                proxyConfiguration.getNonProxyHosts());
    }

    /// Creates a credentials provider holding the credentials needed to authenticate with the configured
    /// proxy.
    ///
    /// @param proxyConfiguration The proxy configuration, which may be `null`.
    /// @return The credentials provider, or `null` if there is no proxy or the proxy needs no authentication.
    public static CredentialsProvider createProxyCredentialsProvider(
            final HttpProxyConfiguration proxyConfiguration) {
        if (!isProxyConfigured(proxyConfiguration) || proxyConfiguration.getAuth() == null) {
            return null;
        }

        final HttpAuthConfiguration auth = proxyConfiguration.getAuth();
        final AuthScope authScope = new AuthScope(
                getProxyHost(proxyConfiguration),
                auth.getRealm(),
                auth.getAuthScheme());
        final BasicCredentialsProvider credentialsProvider = new BasicCredentialsProvider();
        credentialsProvider.setCredentials(authScope, createCredentials(auth));
        return credentialsProvider;
    }

    private static boolean isProxyConfigured(final HttpProxyConfiguration proxyConfiguration) {
        return proxyConfiguration != null && NullSafe.isNonBlankString(proxyConfiguration.getHost());
    }

    private static HttpHost getProxyHost(final HttpProxyConfiguration proxyConfiguration) {
        return new HttpHost(
                proxyConfiguration.getScheme(),
                proxyConfiguration.getHost(),
                proxyConfiguration.getPort());
    }

    private static Credentials createCredentials(final HttpAuthConfiguration auth) {
        final char[] password = toChars(auth.getPassword());
        if (HttpAuthConfiguration.NT_CREDS.equalsIgnoreCase(auth.getCredentialType())) {
            return new NTCredentials(auth.getUsername(), password, auth.getHostname(), auth.getDomain());
        } else {
            return new UsernamePasswordCredentials(auth.getUsername(), password);
        }
    }

    private static PrivateKeyStrategy createPrivateKeyStrategy(final HttpTlsConfiguration tlsConfiguration) {
        final String certAlias = tlsConfiguration.getCertAlias();
        if (NullSafe.isNonBlankString(certAlias)) {
            // Always use the configured alias, so a missing alias fails clearly rather than another key
            // quietly being used in its place.
            return (aliases, sslParameters) -> certAlias;
        }
        return null;
    }

    private static KeyStore loadKeyStore(final String path,
                                         final char[] password,
                                         final String type,
                                         final String provider)
            throws GeneralSecurityException, IOException {
        final KeyStore keyStore = getKeyStoreInstance(type, provider);
        try (final InputStream inputStream = Files.newInputStream(Path.of(path))) {
            keyStore.load(inputStream, password);
        }
        return keyStore;
    }

    private static KeyStore getKeyStoreInstance(final String type,
                                                final String provider) throws GeneralSecurityException {
        if (NullSafe.isNonBlankString(provider)) {
            try {
                return KeyStore.getInstance(type, provider);
            } catch (final KeyStoreException e) {
                // Same as Dropwizard, fall back to any provider that supports the type
                return KeyStore.getInstance(type);
            }
        }
        return KeyStore.getInstance(type);
    }

    private static char[] toChars(final String password) {
        return NullSafe.getOrElse(password, String::toCharArray, new char[0]);
    }

    private static String[] toArray(final List<String> list) {
        return list.toArray(new String[0]);
    }
}
