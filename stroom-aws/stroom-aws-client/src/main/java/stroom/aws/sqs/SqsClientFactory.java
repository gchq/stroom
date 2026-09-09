/*
 * Copyright 2025 Crown Copyright
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

package stroom.aws.sqs;


import stroom.aws.common.AwsCredentialsHelper;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.io.PathCreator;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import jakarta.inject.Inject;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.http.FileStoreTlsKeyManagersProvider;
import software.amazon.awssdk.http.apache5.Apache5HttpClient;
import software.amazon.awssdk.http.apache5.Apache5HttpClient.Builder;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.SqsClientBuilder;

import java.io.BufferedInputStream;
import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Path;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.cert.CertificateException;
import java.util.Objects;
import javax.net.ssl.TrustManager;
import javax.net.ssl.TrustManagerFactory;

public class SqsClientFactory {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqsClientFactory.class);

    private final PathCreator pathCreator;

    @Inject
    public SqsClientFactory(final PathCreator pathCreator) {
        this.pathCreator = pathCreator;
    }

    public SqsClient createSqsClient(final SqsConfig sqsConfig) {
        Objects.requireNonNull(sqsConfig);

        final AwsCredentialsProvider awsCredentialsProvider = createCredentialsProvider(sqsConfig);

        SqsClientBuilder sqsClientBuilder = SqsClient.builder()
                .credentialsProvider(awsCredentialsProvider)
                .region(Region.of(sqsConfig.getAwsRegionName()));

        final HttpClientConfiguration httpClientConfig = sqsConfig.getHttpClient();
        if (httpClientConfig != null) {

            // TODO might be missing a few things here, but the mapping between
            //  HttpClientConfiguration and Apache5HttpClient.Builder is non-obvious
            Builder httpClientBuilder = Apache5HttpClient.builder()
                    .maxConnections(httpClientConfig.getMaxConnections())
                    .connectionTimeToLive(httpClientConfig.getTimeToLive().getDuration())
                    .socketTimeout(httpClientConfig.getTimeout().getDuration())
                    .connectionTimeout(httpClientConfig.getConnectionTimeout().getDuration());

            if (httpClientConfig.getTlsConfiguration() != null) {
                final HttpTlsConfiguration tlsConfiguration = httpClientConfig.getTlsConfiguration();

                if (NullSafe.isNonBlankString(tlsConfiguration.getKeyStorePath())) {
                    httpClientBuilder = httpClientBuilder.tlsKeyManagersProvider(
                            FileStoreTlsKeyManagersProvider.create(
                                    pathCreator.toAppPath(tlsConfiguration.getKeyStorePath()),
                                    tlsConfiguration.getKeyStoreType(),
                                    tlsConfiguration.getKeyStorePassword()));
                }

                if (NullSafe.isNonBlankString(tlsConfiguration.getTrustStorePath())) {
                    httpClientBuilder = httpClientBuilder.tlsTrustManagersProvider(() ->
                            createTrustManagers(tlsConfiguration, pathCreator));
                }
            }
            sqsClientBuilder = sqsClientBuilder.httpClientBuilder(httpClientBuilder);
        }

        final SqsClient sqsClient = sqsClientBuilder.build();

        LOGGER.debug("createSqsClient() - sqsConfig: {}, sqsClient: {}", sqsConfig, sqsClient);
        return sqsClient;
    }

    public static TrustManager[] createTrustManagers(
            final HttpTlsConfiguration tlsConfiguration,
            final PathCreator pathCreator) {
        Objects.requireNonNull(tlsConfiguration);

        KeyStore trustStore = null;
        final TrustManagerFactory trustManagerFactory;

        // Load the truststore
        if (tlsConfiguration.getTrustStorePath() != null) {
            final Path trustStorePath = pathCreator.toAppPath(tlsConfiguration.getTrustStorePath());

            try (final InputStream inputStream = new BufferedInputStream(
                    new FileInputStream(trustStorePath.toFile()))) {
                trustStore = KeyStore.getInstance(tlsConfiguration.getTrustStoreType());
                LOGGER.info(() ->
                        "Loading truststore " + tlsConfiguration.getTrustStorePath() +
                        " of type " + tlsConfiguration.getTrustStoreType());
                trustStore.load(inputStream, tlsConfiguration.getTrustStorePassword().toCharArray());
            } catch (KeyStoreException | IOException | NoSuchAlgorithmException | CertificateException e) {
                throw new RuntimeException(LogUtil.message("Error locating and loading truststore {} with type {}: {}",
                        tlsConfiguration.getTrustStorePath(), tlsConfiguration.getTrustStoreType(), e.getMessage()), e);
            }
        }

        try {
            trustManagerFactory = TrustManagerFactory.getInstance(TrustManagerFactory.getDefaultAlgorithm());
            if (trustStore != null) {
                trustManagerFactory.init(trustStore);
            }
        } catch (NoSuchAlgorithmException | KeyStoreException e) {
            throw new RuntimeException(LogUtil.message("Error initialising TrustManagerFactory for truststore {}: {}",
                    tlsConfiguration.getTrustStorePath(), e.getMessage()), e);
        }

        return trustManagerFactory.getTrustManagers();
    }

    private AwsCredentialsProvider createCredentialsProvider(final SqsConfig sqsConfig) {
        return AwsCredentialsHelper.createCredentialsProvider(
                sqsConfig.getCredentials(),
                sqsConfig.getAssumeRole(),
                sqsConfig.getAwsRegionName());
    }
}
