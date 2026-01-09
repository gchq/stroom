package stroom.langchain.impl;

public class SSLContextFactory {
//
//    private void applySslConfig(final Builder builder,
//                                final OkHttpClientConfig clientConfig) {
//        final SSLConfig sslConfig = clientConfig.getSslConfig();
//
//        if (sslConfig != null) {
//            final KeyManager[] keyManagers = createKeyManagers(sslConfig);
//            final TrustManager[] trustManagers = createTrustManagers(sslConfig);
//
//            final SSLContext sslContext;
//            try {
//                sslContext = SSLContext.getInstance(sslConfig.getSslProtocol());
//                sslContext.init(keyManagers, trustManagers, new SecureRandom());
//                builder.sslSocketFactory(sslContext.getSocketFactory(),
//                        (X509TrustManager) trustManagers[0]);
//                if (!sslConfig.isHostnameVerificationEnabled()) {
//                    builder.hostnameVerifier(SSLUtil.PERMISSIVE_HOSTNAME_VERIFIER);
//                }
//            } catch (NoSuchAlgorithmException | KeyManagementException e) {
//                throw ProcessException.create(
//                        "Error initialising SSL context, is the http client configuration valid?. "
//                                + e.getMessage(), e);
//            }
//        }
//    }
//
//    private TrustManager[] createTrustManagers(final SSLConfig sslConfig) {
//        try {
//            return SSLUtil.createTrustManagers(sslConfig, pathCreator);
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid client trustStore configuration: " + e.getMessage(), e);
//        }
//    }
//
//    private KeyManager[] createKeyManagers(final SSLConfig sslConfig) {
//        try {
//            final KeyManager[] keyManagers = SSLUtil.createKeyManagers(sslConfig, pathCreator);
//            return keyManagers;
//        } catch (Exception e) {
//            throw new RuntimeException("Invalid client keyStore configuration: " + e.getMessage(), e);
//        }
//    }
//
//    private <T> void addOptionalConfigurationValue(final Function<T, Builder> builderFunc,
//                                                   final T configValue) {
//        if (configValue != null) {
//            builderFunc.apply(configValue);
//        }
//    }
//
//    private void addOptionalConfigurationDuration(final Function<Duration, Builder> builderFunc,
//                                                  final StroomDuration configValue) {
//        if (configValue != null) {
//            builderFunc.apply(configValue.getDuration());
//        }
//    }
//
//    private void configureHttpProtocolVersions(final Builder builder, OkHttpClientConfig clientConfig) {
//        if (!NullSafe.isEmptyCollection(clientConfig.getHttpProtocols())) {
//            final List<Protocol> protocols = clientConfig.getHttpProtocols()
//                    .stream()
//                    .map(String::toLowerCase)
//                    .map(protocolStr -> {
//                        // No idea why okhttp uses "h2" for http 2.0, so cater for it manually
//                        if ("http/2".equals(protocolStr)) {
//                            return Protocol.HTTP_2;
//                        } else {
//                            try {
//                                return Protocol.get(protocolStr);
//                            } catch (IOException e) {
//                                throw ProcessException.create(LogUtil.message(
//                                        "Invalid http protocol [{}] in client configuration", protocolStr), e);
//                            }
//                        }
//
//                    })
//                    .collect(Collectors.toList());
//            builder.protocols(protocols);
//        }
//    }

}
