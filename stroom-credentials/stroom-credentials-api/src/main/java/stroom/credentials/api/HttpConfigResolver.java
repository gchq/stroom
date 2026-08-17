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

package stroom.credentials.api;

import stroom.util.http.HttpAuthConfiguration;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.shared.NullSafe;
import stroom.util.shared.http.HttpAuthConfig;
import stroom.util.shared.http.HttpClientConfig;
import stroom.util.shared.http.HttpProxyConfig;
import stroom.util.shared.http.HttpTlsConfig;
import stroom.util.time.SimpleDurationUtil;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;

import java.util.Objects;

/**
 * Turns the HTTP client configuration held on a document into the server side configuration used to build
 * a real client.
 * <p>
 * The two halves exist because a document is exportable and a key store is not. {@link HttpTlsConfig} names
 * the key stores it wants; the material behind those names lives in the secret store and is resolved here,
 * on the server, at the point of use. That is what keeps passwords and file paths out of exported content -
 * so resolve late, and do not be tempted to cache the result on anything that gets serialised.
 * </p>
 * <p>
 * This lives in the credentials API rather than with the other HTTP utilities because resolving those names
 * is the whole job; {@code stroom-util} has no business knowing about the secret store.
 * </p>
 */
@Singleton
public class HttpConfigResolver {

    private final Provider<StoredSecrets> storedSecretsProvider;

    @Inject
    public HttpConfigResolver(final Provider<StoredSecrets> storedSecretsProvider) {
        this.storedSecretsProvider = storedSecretsProvider;
    }

    public HttpClientConfiguration resolve(final HttpClientConfig config) {
        Objects.requireNonNull(config, "Null HTTP client configuration");

        return HttpClientConfiguration
                .builder()
                .timeout(SimpleDurationUtil.convertToStroomDuration(config.getTimeout()))
                .connectionTimeout(SimpleDurationUtil.convertToStroomDuration(config.getConnectionTimeout()))
                .connectionRequestTimeout(
                        SimpleDurationUtil.convertToStroomDuration(config.getConnectionRequestTimeout()))
                .timeToLive(SimpleDurationUtil.convertToStroomDuration(config.getTimeToLive()))
                .cookiesEnabled(config.isCookiesEnabled())
                .maxConnections(config.getMaxConnections())
                .maxConnectionsPerRoute(config.getMaxConnectionsPerRoute())
                .keepAlive(SimpleDurationUtil.convertToStroomDuration(config.getKeepAlive()))
                .retries(config.getRetries())
                .userAgent(config.getUserAgent())
                .proxyConfiguration(resolve(config.getProxy()))
                .validateAfterInactivityPeriod(
                        SimpleDurationUtil.convertToStroomDuration(config.getValidateAfterInactivityPeriod()))
                .tlsConfiguration(resolve(config.getTls()))
                .build();
    }

    public HttpProxyConfiguration resolve(final HttpProxyConfig config) {
        if (config == null) {
            return null;
        }

        return HttpProxyConfiguration
                .builder()
                .host(config.getHost())
                .port(config.getPort())
                .scheme(config.getScheme())
                .auth(resolve(config.getAuth()))
                .nonProxyHosts(config.getNonProxyHosts())
                .build();
    }

    public HttpAuthConfiguration resolve(final HttpAuthConfig config) {
        if (config == null) {
            return null;
        }

        return HttpAuthConfiguration
                .builder()
                .username(config.getUsername())
                .password(config.getPassword())
                .authScheme(config.getAuthScheme())
                .realm(config.getRealm())
                .hostname(config.getHostname())
                .domain(config.getDomain())
                .credentialType(config.getCredentialType())
                .build();
    }

    public HttpTlsConfiguration resolve(final HttpTlsConfig config) {
        if (config == null) {
            return null;
        }

        final HttpTlsConfiguration.Builder builder = HttpTlsConfiguration.builder();
        if (NullSafe.isNonBlankString(config.getKeyStoreName())) {
            final KeyStore keyStore = storedSecretsProvider.get().getKeyStore(config.getKeyStoreName());
            builder
                    .keyStorePath(keyStore.keyStorePath())
                    .keyStorePassword(keyStore.keyStorePassword())
                    .keyStoreType(keyStore.keyStoreType())
                    .keyStoreProvider(keyStore.keyStoreProvider());
        }

        if (NullSafe.isNonBlankString(config.getTrustStoreName())) {
            final KeyStore trustStore = storedSecretsProvider.get().getKeyStore(config.getTrustStoreName());
            builder
                    .trustStorePath(trustStore.keyStorePath())
                    .trustStorePassword(trustStore.keyStorePassword())
                    .trustStoreType(trustStore.keyStoreType())
                    .trustStoreProvider(trustStore.keyStoreProvider());
        }

        return builder
                .protocol(config.getProtocol())
                .provider(config.getProvider())
                .trustSelfSignedCertificates(config.isTrustSelfSignedCertificates())
                .verifyHostname(config.isVerifyHostname())
                .supportedProtocols(config.getSupportedProtocols())
                .supportedCiphers(config.getSupportedCiphers())
                .certAlias(config.getCertAlias())
                .build();
    }
}
