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

package stroom.proxy.app.pipeline.store.s3;

import stroom.proxy.app.pipeline.config.PipelineValidationIssue;
import stroom.proxy.app.pipeline.config.PipelineValidationResult;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfig;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.store.FileStoreDefinition;
import stroom.proxy.app.pipeline.store.FileStoreType;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Recognised values for an S3 store's {@code credentialsType}.
 * <p>
 * Identity is expected to belong to the workload rather than to configuration: a
 * pod, task or instance carries an IAM role scoped to the stages it runs, and
 * {@code default} picks that up through the SDK chain. {@code basic} remains for
 * S3-compatible endpoints such as MinIO, which have no instance identity.
 * </p>
 * <p>
 * {@code profile} was removed. The SDK resolves a profile from {@code AWS_PROFILE}
 * or the {@code aws.profile} system property rather than from an argument, so a
 * per-store setting could never select a profile - it resolved exactly what
 * {@code default} resolves while skipping the rest of the chain, which made it look
 * like a per-store choice it was not. An unrecognised type is now rejected rather
 * than silently falling back to the default chain, which is what previously absorbed
 * it.
 * </p>
 */
class TestS3CredentialsType {

    private final ProxyPipelineConfigValidator validator = new ProxyPipelineConfigValidator();

    private static FileStoreDefinition s3Store(final String credentialsType) {
        return new FileStoreDefinition(
                FileStoreType.S3,
                null,
                "eu-west-2",
                "example-bucket",
                "prefix/",
                null,
                credentialsType,
                "AKIAEXAMPLE",
                "secret",
                null);
    }

    private List<String> credentialsErrors(final String credentialsType) {
        final ProxyPipelineConfig config = new ProxyPipelineConfig(
                null,
                ProxyPipelineConfig.defaultFullPipelineStages(),
                Map.of(ProxyPipelineConfig.RECEIVE_STORE, s3Store(credentialsType)));

        final PipelineValidationResult result = validator.validate(config);
        return result.getErrors()
                .stream()
                .filter(i -> ProxyPipelineConfigValidator
                        .CODE_S3_UNSUPPORTED_CREDENTIALS_TYPE.equals(i.code()))
                .map(PipelineValidationIssue::message)
                .toList();
    }

    @Test
    void testProfileIsRejected() {
        final List<String> errors = credentialsErrors("profile");

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0))
                .contains("profile")
                .contains("AWS_PROFILE");
    }

    @Test
    void testUnknownTypeIsRejectedRatherThanFallingBackToDefault() {
        assertThat(credentialsErrors("instance-role")).hasSize(1);
    }

    @Test
    void testSupportedTypesAreAccepted() {
        assertThat(credentialsErrors("default")).isEmpty();
        assertThat(credentialsErrors("basic")).isEmpty();
        assertThat(credentialsErrors("environment")).isEmpty();
    }

    @Test
    void testTypeIsCaseInsensitive() {
        assertThat(credentialsErrors("DEFAULT")).isEmpty();
        assertThat(credentialsErrors("Basic")).isEmpty();
    }

    @Test
    void testOmittingTheTypeDefaultsToTheSdkChain() {
        assertThat(credentialsErrors(null)).isEmpty();
    }

    @Test
    void testSupportedSetIsExactly() {
        assertThat(S3FileStore.SUPPORTED_CREDENTIALS_TYPES)
                .containsExactlyInAnyOrder("default", "basic", "environment");
    }
}
