/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.aws.common;


import stroom.aws.common.shared.AwsAnonymousCredentials;
import stroom.aws.common.shared.AwsAssumeRole;
import stroom.aws.common.shared.AwsAssumeRoleClientConfig;
import stroom.aws.common.shared.AwsAssumeRoleRequest;
import stroom.aws.common.shared.AwsDefaultCredentials;
import stroom.aws.common.shared.AwsEnvironmentVariableCredentials;
import stroom.aws.common.shared.AwsPolicyDescriptorType;
import stroom.aws.common.shared.AwsProfileCredentials;
import stroom.aws.common.shared.AwsProvidedContext;
import stroom.aws.common.shared.AwsSystemPropertyCredentials;
import stroom.aws.common.shared.AwsTag;
import stroom.aws.common.shared.AwsWebCredentials;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.NullSafe;

import org.jspecify.annotations.NonNull;
import software.amazon.awssdk.auth.credentials.AnonymousCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsBasicCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentials;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProvider;
import software.amazon.awssdk.auth.credentials.AwsCredentialsProviderChain;
import software.amazon.awssdk.auth.credentials.AwsSessionCredentials;
import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.auth.credentials.EnvironmentVariableCredentialsProvider;
import software.amazon.awssdk.auth.credentials.ProfileCredentialsProvider;
import software.amazon.awssdk.auth.credentials.StaticCredentialsProvider;
import software.amazon.awssdk.auth.credentials.SystemPropertyCredentialsProvider;
import software.amazon.awssdk.auth.credentials.WebIdentityTokenFileCredentialsProvider;
import software.amazon.awssdk.profiles.ProfileFile;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sts.StsAsyncClient;
import software.amazon.awssdk.services.sts.StsAsyncClientBuilder;
import software.amazon.awssdk.services.sts.model.AssumeRoleRequest;
import software.amazon.awssdk.services.sts.model.AssumeRoleResponse;
import software.amazon.awssdk.services.sts.model.Credentials;
import software.amazon.awssdk.services.sts.model.PolicyDescriptorType;
import software.amazon.awssdk.services.sts.model.ProvidedContext;

import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.time.Duration;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;

public class AwsCredentialsHelper {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(AwsCredentialsHelper.class);

    private AwsCredentialsHelper() {
        // Static util methods only
    }

    public static AwsCredentialsProvider createCredentialsProvider(
            final stroom.aws.common.shared.AwsCredentials awsCredentials,
            final AwsAssumeRole awsAssumeRole,
            final String awsRegion) {

        LOGGER.debug("credentialsProvider() - awsCredentials: {}, awsAssumeRole: {}, awsRegion: {}",
                awsCredentials, awsAssumeRole, awsRegion);

        if (NullSafe.nonNull(awsAssumeRole, AwsAssumeRole::getRequest)) {
            // If the config asks the client to assume a role then get assumed role credentials.
            try (final StsAsyncClient stsAsyncClient =
                    createStsAsyncClient(awsCredentials, awsAssumeRole, awsRegion)) {
                final AssumeRoleRequest assumeRoleRequest = createAssumeRoleRequest(awsAssumeRole.getRequest());
                final Future<AssumeRoleResponse> responseFuture = stsAsyncClient.assumeRole(assumeRoleRequest);
                final AssumeRoleResponse response = responseFuture.get();
                final Credentials credentials = response.credentials();
                final AwsSessionCredentials sessionCredentials = AwsSessionCredentials.create(
                        credentials.accessKeyId(),
                        credentials.secretAccessKey(),
                        credentials.sessionToken());
                return AwsCredentialsProviderChain.builder()
                        .credentialsProviders(StaticCredentialsProvider.create(sessionCredentials))
                        .build();
            } catch (final InterruptedException | ExecutionException | RuntimeException e) {
                LOGGER.error(e::getMessage, e);
                throw new RuntimeException(e.getMessage());
            }
        } else {
            return createCredentialsProvider(awsCredentials);
        }
    }

    private static AwsCredentialsProvider createCredentialsProvider(
            final stroom.aws.common.shared.AwsCredentials awsCredentials) {

        LOGGER.debug("credentialsProvider() - awsCredentials: {}", awsCredentials);

        final AwsCredentialsProvider credentialsProvider = switch (awsCredentials) {
            case null -> getDefaultCredentialsProvider();
            case final AwsAnonymousCredentials ignored -> getAnonCredentialsProvider();
            case final stroom.aws.common.shared.AwsBasicCredentials awsBasicCredentials ->
                    getBasicCredentialsProvider(awsBasicCredentials);
            case final AwsDefaultCredentials ignored -> getDefaultCredentialsProvider();
            case final AwsEnvironmentVariableCredentials ignored -> getEnvironmentVariableCredentialsProvider();
            case final AwsProfileCredentials awsProfileCredentials ->
                    getProfileCredentialsProvider(awsProfileCredentials);
            case final stroom.aws.common.shared.AwsSessionCredentials awsSessionCredentials ->
                    getSessionCredentialsProvider(awsSessionCredentials);
            case final AwsSystemPropertyCredentials ignored -> getSystemPropertyCredentialsProvider();
            case final AwsWebCredentials awsWebCredentials ->
                    getWebIdentityTokenFileCredentialsProvider(awsWebCredentials);
        };
        LOGGER.debug("credentialsProvider() - returning: {}", credentialsProvider);
        return credentialsProvider;
    }

    private static WebIdentityTokenFileCredentialsProvider getWebIdentityTokenFileCredentialsProvider(
            final AwsWebCredentials awsWebCredentials) {
        LOGGER.debug("Using AWS web identity credentials");
        return WebIdentityTokenFileCredentialsProvider
                .builder()
                .roleArn(awsWebCredentials.getRoleArn())
                .roleSessionName(awsWebCredentials.getRoleSessionName())
                .webIdentityTokenFile(Paths.get(awsWebCredentials.getWebIdentityTokenFile()))
                .asyncCredentialUpdateEnabled(awsWebCredentials.getAsyncCredentialUpdateEnabled())
                .prefetchTime(Duration.parse(awsWebCredentials.getPrefetchTime()))
                .staleTime(Duration.parse(awsWebCredentials.getStaleTime()))
                .roleSessionDuration(Duration.parse(awsWebCredentials.getSessionDuration()))
                .build();
    }

    private static @NonNull SystemPropertyCredentialsProvider getSystemPropertyCredentialsProvider() {
        LOGGER.debug("Using AWS system property credentials");
        return SystemPropertyCredentialsProvider.create();
    }

    private static @NonNull StaticCredentialsProvider getSessionCredentialsProvider(
            final stroom.aws.common.shared.AwsSessionCredentials awsSessionCredentials) {
        LOGGER.debug("Using AWS session credentials");
        final AwsSessionCredentials credentials = AwsSessionCredentials
                .builder()
                .accessKeyId(awsSessionCredentials.getAccessKeyId())
                .secretAccessKey(awsSessionCredentials.getSecretAccessKey())
                .sessionToken(awsSessionCredentials.getSessionToken())
                .build();
        return StaticCredentialsProvider.create(credentials);
    }

    private static ProfileCredentialsProvider getProfileCredentialsProvider(
            final AwsProfileCredentials awsProfileCredentials) {
        LOGGER.debug("Using AWS profile credentials");
        if (!NullSafe.isBlankString(awsProfileCredentials.getProfileFilePath())) {
            final Path path = Paths.get(awsProfileCredentials.getProfileFilePath());
            return ProfileCredentialsProvider
                    .builder()
                    .profileFile(ProfileFile.builder().content(path).build())
                    .build();
        } else {
            return ProfileCredentialsProvider
                    .builder()
                    .profileName(awsProfileCredentials.getProfileName())
                    .build();
        }
    }

    private static @NonNull EnvironmentVariableCredentialsProvider getEnvironmentVariableCredentialsProvider() {
        LOGGER.debug("Using AWS environment variable credentials");
        return EnvironmentVariableCredentialsProvider.create();
    }

    private static @NonNull DefaultCredentialsProvider getDefaultCredentialsProvider() {
        LOGGER.debug("Using AWS default credentials");
        return DefaultCredentialsProvider.builder().build();
    }

    private static @NonNull StaticCredentialsProvider getBasicCredentialsProvider(
            final stroom.aws.common.shared.AwsBasicCredentials awsBasicCredentials) {
        LOGGER.debug("Using AWS basic credentials");
        final AwsCredentials credentials = AwsBasicCredentials
                .create(awsBasicCredentials.getAccessKeyId(), awsBasicCredentials.getSecretAccessKey());
        return StaticCredentialsProvider.create(credentials);
    }

    private static @NonNull AnonymousCredentialsProvider getAnonCredentialsProvider() {
        return getAnonymousCredentialsProvider();
    }

    private static @NonNull AnonymousCredentialsProvider getAnonymousCredentialsProvider() {
        LOGGER.debug("Using AWS anonymous credentials");
        return AnonymousCredentialsProvider.create();
    }

    private static StsAsyncClient createStsAsyncClient(
            final stroom.aws.common.shared.AwsCredentials awsCredentials,
            final AwsAssumeRole awsAssumeRole,
            final String awsRegion) {

        final StsAsyncClientBuilder builder = StsAsyncClient.builder();

        final AwsAssumeRoleClientConfig awsAssumeRoleClientConfig = awsAssumeRole.getClientConfig();
        if (awsAssumeRoleClientConfig != null && awsAssumeRoleClientConfig.getCredentials() != null) {
            builder.credentialsProvider(createCredentialsProvider(awsAssumeRoleClientConfig.getCredentials()));
        } else if (awsCredentials != null) {
            builder.credentialsProvider(createCredentialsProvider(awsCredentials));
        }

        if (awsAssumeRoleClientConfig != null && awsAssumeRoleClientConfig.getRegion() != null) {
            builder.region(createRegion(awsAssumeRoleClientConfig.getRegion()));
        } else if (awsRegion != null) {
            builder.region(createRegion(awsRegion));
        }

        NullSafe.consume(awsAssumeRoleClientConfig,
                AwsAssumeRoleClientConfig::getEndpointOverride,
                endpointOverride ->
                        builder.endpointOverride(createUri(endpointOverride)));

        return builder.build();
    }

    private static AssumeRoleRequest createAssumeRoleRequest(final AwsAssumeRoleRequest config) {
        final AssumeRoleRequest.Builder builder = AssumeRoleRequest.builder();
        NullSafe.consume(config.getRoleArn(), builder::roleArn);
        NullSafe.consume(config.getRoleSessionName(), builder::roleSessionName);
        NullSafe.consume(config.getPolicyArns(), policyArns ->
                builder.policyArns(policyArns.stream()
                        .map(AwsCredentialsHelper::createPolicyDescriptorType)
                        .toList()));
        NullSafe.consume(config.getPolicy(), builder::policy);
        NullSafe.consume(config.getDurationSeconds(), builder::durationSeconds);
        NullSafe.consume(config.getTags(), tags ->
                builder.tags(tags.stream()
                        .map(AwsCredentialsHelper::createStsTag)
                        .toList()));
        NullSafe.consume(config.getTransitiveTagKeys(), builder::transitiveTagKeys);
        NullSafe.consume(config.getExternalId(), builder::externalId);
        NullSafe.consume(config.getSerialNumber(), builder::serialNumber);
        NullSafe.consume(config.getTokenCode(), builder::tokenCode);
        NullSafe.consume(config.getSourceIdentity(), builder::sourceIdentity);
        NullSafe.consume(config.getProvidedContexts(), providedContexts ->
                builder.providedContexts(providedContexts.stream()
                        .map(AwsCredentialsHelper::createProvidedContext)
                        .toList()));
        return builder.build();
    }

    private static PolicyDescriptorType createPolicyDescriptorType(
            final AwsPolicyDescriptorType awsPolicyDescriptorType) {

        return PolicyDescriptorType.builder()
                .arn(awsPolicyDescriptorType.getArn())
                .build();
    }

    private static software.amazon.awssdk.services.sts.model.Tag createStsTag(final AwsTag awsTag) {
        return software.amazon.awssdk.services.sts.model.Tag
                .builder()
                .key(awsTag.getKey())
                .value(awsTag.getValue())
                .build();
    }

    private static ProvidedContext createProvidedContext(final AwsProvidedContext awsProvidedContext) {
        return ProvidedContext
                .builder()
                .contextAssertion(awsProvidedContext.getContextAssertion())
                .providerArn(awsProvidedContext.getProviderArn())
                .build();
    }

    private static Region createRegion(final String region) {
        return NullSafe.mapNonBlankString(region, Region::of);
    }

    private static URI createUri(final String uri) {
        return NullSafe.mapNonBlankString(uri, URI::create);
    }
}
