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

package stroom.util.http;

import stroom.util.io.FileUtil;
import stroom.util.io.PathCreator;
import stroom.util.time.StroomDuration;

import jakarta.inject.Inject;
import jakarta.inject.Singleton;

import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;

// Singleton so we only have to discover all the getters/setters once
@Singleton
public class HttpClientConfigConverter {

    private final Map<TypeMapping, Function<?, ?>> mappingFunctionMap = new HashMap<>();

    @Inject
    public HttpClientConfigConverter(final PathCreator pathCreator) {

        // Converters for HttpClientConfiguration
        put(stroom.util.http.HttpClientConfiguration.class, io.dropwizard.client.HttpClientConfiguration.class, in -> {
            final io.dropwizard.client.HttpClientConfiguration out = new io.dropwizard.client.HttpClientConfiguration();
            out.setKeepAlive(convert(in.getKeepAlive(),
                    io.dropwizard.util.Duration.class));
            out.setMaxConnectionsPerRoute(convert(in.getMaxConnectionsPerRoute(),
                    int.class));
            out.setTimeout(convert(in.getTimeout(),
                    io.dropwizard.util.Duration.class));
            out.setConnectionTimeout(convert(in.getConnectionTimeout(),
                    io.dropwizard.util.Duration.class));
            out.setConnectionRequestTimeout(convert(in.getConnectionRequestTimeout(),
                    io.dropwizard.util.Duration.class));
            out.setTimeToLive(convert(in.getTimeToLive(),
                    io.dropwizard.util.Duration.class));
            out.setCookiesEnabled(convert(in.isCookiesEnabled(),
                    boolean.class));
            out.setMaxConnections(convert(in.getMaxConnections(),
                    int.class));
            out.setRetries(convert(in.getRetries(),
                    int.class));
            out.setUserAgent(convertOptional(in.getUserAgent(),
                    String.class));
            out.setProxyConfiguration(convert(in.getProxyConfiguration(),
                    io.dropwizard.client.proxy.ProxyConfiguration.class));
            out.setValidateAfterInactivityPeriod(convert(in.getValidateAfterInactivityPeriod(),
                    io.dropwizard.util.Duration.class));
            out.setTlsConfiguration(convert(in.getTlsConfiguration(),
                    io.dropwizard.client.ssl.TlsConfiguration.class));
            return out;
        });

        put(io.dropwizard.client.HttpClientConfiguration.class, stroom.util.http.HttpClientConfiguration.class, in ->
                HttpClientConfiguration
                        .builder()
                        .keepAlive(convert(in.getKeepAlive(),
                                StroomDuration.class))
                        .maxConnectionsPerRoute(convert(in.getMaxConnectionsPerRoute(),
                                int.class))
                        .timeout(convert(in.getTimeout(),
                                StroomDuration.class))
                        .connectionTimeout(convert(in.getConnectionTimeout(),
                                StroomDuration.class))
                        .connectionRequestTimeout(convert(in.getConnectionRequestTimeout(),
                                StroomDuration.class))
                        .timeToLive(convert(in.getTimeToLive(),
                                StroomDuration.class))
                        .cookiesEnabled(convert(in.isCookiesEnabled(),
                                boolean.class))
                        .maxConnections(convert(in.getMaxConnections(),
                                int.class))
                        .retries(convert(in.getRetries(),
                                int.class))
                        .userAgent(convert(in.getUserAgent().orElse(null),
                                String.class))
                        .proxyConfiguration(convert(in.getProxyConfiguration(),
                                stroom.util.http.HttpProxyConfiguration.class))
                        .validateAfterInactivityPeriod(convert(in.getValidateAfterInactivityPeriod(),
                                StroomDuration.class))
                        .tlsConfiguration(convert(in.getTlsConfiguration(),
                                stroom.util.http.HttpTlsConfiguration.class))
                        .build());

        // Converters for ProxyConfiguration
        put(stroom.util.http.HttpProxyConfiguration.class, io.dropwizard.client.proxy.ProxyConfiguration.class, in -> {
            final io.dropwizard.client.proxy.ProxyConfiguration out =
                    new io.dropwizard.client.proxy.ProxyConfiguration();
            out.setHost(convert(in.getHost(), String.class));
            out.setPort(convert(in.getPort(), Integer.class));
            out.setScheme(convert(in.getScheme(), String.class));
            out.setAuth(convert(in.getAuth(), io.dropwizard.client.proxy.AuthConfiguration.class));
            out.setNonProxyHosts(convertList(in.getNonProxyHosts(), String.class));
            return out;
        });

        put(io.dropwizard.client.proxy.ProxyConfiguration.class, stroom.util.http.HttpProxyConfiguration.class, in ->
                HttpProxyConfiguration
                        .builder()
                        .host(convert(in.getHost(), String.class))
                        .port(convert(in.getPort(), Integer.class))
                        .scheme(convert(in.getScheme(), String.class))
                        .auth(convert(in.getAuth(), stroom.util.http.HttpAuthConfiguration.class))
                        .nonProxyHosts(convertList(in.getNonProxyHosts(), String.class))
                        .build());

        // Converters for AuthConfiguration
        put(stroom.util.http.HttpAuthConfiguration.class, io.dropwizard.client.proxy.AuthConfiguration.class, in -> {
            final io.dropwizard.client.proxy.AuthConfiguration out =
                    new io.dropwizard.client.proxy.AuthConfiguration();
            out.setUsername(convert(in.getUsername(), String.class));
            out.setPassword(convert(in.getPassword(), String.class));
            out.setAuthScheme(convert(in.getAuthScheme(), String.class));
            out.setRealm(convert(in.getRealm(), String.class));
            out.setHostname(convert(in.getHostname(), String.class));
            out.setDomain(convert(in.getDomain(), String.class));
            out.setCredentialType(convert(in.getCredentialType(), String.class));
            return out;
        });

        put(io.dropwizard.client.proxy.AuthConfiguration.class, stroom.util.http.HttpAuthConfiguration.class, in ->
                HttpAuthConfiguration
                        .builder()
                        .username(convert(in.getUsername(), String.class))
                        .password(convert(in.getPassword(), String.class))
                        .authScheme(convert(in.getAuthScheme(), String.class))
                        .realm(convert(in.getRealm(), String.class))
                        .hostname(convert(in.getHostname(), String.class))
                        .domain(convert(in.getDomain(), String.class))
                        .credentialType(convert(in.getCredentialType(), String.class))
                        .build());

        // Converters for TlsConfiguration
        put(stroom.util.http.HttpTlsConfiguration.class, io.dropwizard.client.ssl.TlsConfiguration.class, in -> {
            final io.dropwizard.client.ssl.TlsConfiguration out =
                    new io.dropwizard.client.ssl.TlsConfiguration();
            out.setProtocol(convert(in.getProtocol(), String.class));
            out.setProvider(convert(in.getProvider(), String.class));
            out.setKeyStorePath(convert(in.getKeyStorePath(), File.class));
            out.setKeyStorePassword(convert(in.getKeyStorePassword(), String.class));
            out.setKeyStoreType(convert(in.getKeyStoreType(), String.class));
            out.setKeyStoreProvider(convert(in.getKeyStoreProvider(), String.class));
            out.setTrustStorePath(convert(in.getTrustStorePath(), File.class));
            out.setTrustStorePassword(convert(in.getTrustStorePassword(), String.class));
            out.setTrustStoreType(convert(in.getTrustStoreType(), String.class));
            out.setTrustStoreProvider(convert(in.getTrustStoreProvider(), String.class));
            out.setTrustSelfSignedCertificates(convert(in.isTrustSelfSignedCertificates(), boolean.class));
            out.setSupportedCiphers(convertList(in.getSupportedCiphers(), String.class));
            out.setSupportedProtocols(convertList(in.getSupportedProtocols(), String.class));
            out.setCertAlias(convert(in.getCertAlias(), String.class));
            return out;
        });

        put(io.dropwizard.client.ssl.TlsConfiguration.class, stroom.util.http.HttpTlsConfiguration.class, in ->
                HttpTlsConfiguration
                        .builder()
                        .protocol(convert(in.getProtocol(), String.class))
                        .provider(convert(in.getProvider(), String.class))
                        .keyStorePath(convert(in.getKeyStorePath(), String.class))
                        .keyStorePassword(convert(in.getKeyStorePassword(), String.class))
                        .keyStoreType(convert(in.getKeyStoreType(), String.class))
                        .keyStoreProvider(convert(in.getKeyStoreProvider(), String.class))
                        .trustStorePath(convert(in.getTrustStorePath(), String.class))
                        .trustStorePassword(convert(in.getTrustStorePassword(), String.class))
                        .trustStoreType(convert(in.getTrustStoreType(), String.class))
                        .trustStoreProvider(convert(in.getTrustStoreProvider(), String.class))
                        .trustSelfSignedCertificates(convert(in.isTrustSelfSignedCertificates(), boolean.class))
                        .supportedCiphers(convertList(in.getSupportedCiphers(), String.class))
                        .supportedProtocols(convertList(in.getSupportedProtocols(), String.class))
                        .certAlias(convert(in.getCertAlias(), String.class))
                        .build());

        // Generic mappings
        put(String.class, String.class, in -> in);
        put(Boolean.class, Boolean.class, in -> in);
        put(Byte.class, Byte.class, in -> in);
        put(Short.class, Short.class, in -> in);
        put(Integer.class, Integer.class, in -> in);
        put(Long.class, Long.class, in -> in);
        put(Float.class, Float.class, in -> in);
        put(Double.class, Double.class, in -> in);
        put(boolean.class, boolean.class, in -> in);
        put(byte.class, byte.class, in -> in);
        put(short.class, short.class, in -> in);
        put(int.class, int.class, in -> in);
        put(long.class, long.class, in -> in);
        put(float.class, float.class, in -> in);
        put(double.class, double.class, in -> in);
        put(Boolean.class, boolean.class, in -> in);
        put(Byte.class, byte.class, in -> in);
        put(Short.class, short.class, in -> in);
        put(Integer.class, int.class, in -> in);
        put(Long.class, long.class, in -> in);
        put(Float.class, float.class, in -> in);
        put(Double.class, double.class, in -> in);
        put(boolean.class, Boolean.class, Boolean::valueOf);
        put(byte.class, Byte.class, Byte::valueOf);
        put(short.class, Short.class, Short::valueOf);
        put(int.class, Integer.class, Integer::valueOf);
        put(long.class, Long.class, Long::valueOf);
        put(float.class, Float.class, Float::valueOf);
        put(double.class, Double.class, Double::valueOf);
        put(StroomDuration.class, io.dropwizard.util.Duration.class, in -> {
            try {
                // May fail due to overflow
                return io.dropwizard.util.Duration.nanoseconds(in.toNanos());
            } catch (final ArithmeticException e) {
                // Fall back to conversion using millis with possible loss of precision
                return io.dropwizard.util.Duration.milliseconds(in.toMillis());
            }
        });
        put(io.dropwizard.util.Duration.class, StroomDuration.class, in -> {
            final long nanos = in.toNanoseconds();
            // Conversions from coarser to
            //     * finer granularities with arguments that would numerically
            //     * overflow saturate to {@code Long.MIN_VALUE} if negative or
            //     * {@code Long.MAX_VALUE} if positive.
            if (nanos != Long.MIN_VALUE && nanos != Long.MAX_VALUE) {
                return StroomDuration.ofNanos(nanos);
            }
            return StroomDuration.ofMillis(in.toMilliseconds());
        });
        put(File.class, String.class, in -> FileUtil.getCanonicalPath(in.toPath()));
        put(String.class, File.class, in -> {
            return pathCreator.toAppPath(in).toFile();
        });
    }

    private <T_IN, T_OUT> void put(final Class<T_IN> inClass,
                                   final Class<T_OUT> outClass,
                                   final Function<T_IN, T_OUT> function) {
        mappingFunctionMap.put(new TypeMapping(inClass, outClass), function);
    }


    private <T_OUT, T_IN> Optional<T_OUT> convertOptional(final T_IN in, final Class<T_OUT> outClass) {
        return Optional.ofNullable(convert(in, outClass));
    }

    private <T_OUT, T_IN> List<T_OUT> convertList(final List<T_IN> in, final Class<T_OUT> outClass) {
        if (in == null) {
            return null;
        }
        return in.stream().map(i -> convert(i, outClass)).toList();
    }

    @SuppressWarnings("unchecked")
    public <T_OUT, T_IN> T_OUT convert(final T_IN in, final Class<T_OUT> outClass) {
        if (in == null) {
            return null;
        }

        final Function<?, ?> mapping = mappingFunctionMap.get(new TypeMapping(in.getClass(), outClass));
        if (mapping == null) {
            throw new RuntimeException("No mapping found for " + in.getClass().getName() + " -> " + outClass.getName());
        }
        final Function<T_IN, T_OUT> cast = (Function<T_IN, T_OUT>) mapping;
        return cast.apply(in);
    }

    private record TypeMapping(Class<?> in, Class<?> out) {

    }
}
