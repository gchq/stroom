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

package stroom.proxy.app;

import stroom.proxy.app.handler.DestinationType;
import stroom.proxy.app.handler.DirNames;
import stroom.proxy.app.handler.DirUtil;
import stroom.proxy.app.handler.FailureDestinationConfig;
import stroom.proxy.app.pipeline.config.ForwardDestinationFacts;
import stroom.proxy.app.pipeline.config.PipelineValidationResult;
import stroom.proxy.app.pipeline.config.ProxyPipelineConfigValidator;
import stroom.proxy.app.pipeline.stage.forward.ForwardBounds;

import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.io.IOException;
import java.nio.file.Path;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The deployment examples under {@code designs/deployments} are what an operator copies, so every
 * one parses against the current configuration tree and, unless it says it is a fragment, passes the
 * pipeline validator with no errors.
 */
class TestDeploymentExamples {

    private static final Path DEPLOYMENTS = Path.of("..", "designs", "deployments");

    @ParameterizedTest
    @ValueSource(strings = {"single-process.yml", "sqs-s3-distributed.yml", "kafka-distributed.yml"})
    void testACompleteExampleParsesAndValidates(final String fileName) throws IOException {
        final ProxyConfig proxyConfig = read(fileName);

        final PipelineValidationResult result = new ProxyPipelineConfigValidator().validate(
                proxyConfig.getPipelineConfig(), facts(proxyConfig), null);

        assertThat(result.getErrors())
                .as("%s must start as written", fileName)
                .isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {"split-stage-workers.yml"})
    void testAFragmentExampleParses(final String fileName) throws IOException {
        final ProxyConfig proxyConfig = read(fileName);
        assertThat(proxyConfig.getPipelineConfig().getStages().getConfiguredStages())
                .as("the fragment states every stage")
                .hasSize(4);
    }

    private static ProxyConfig read(final String fileName) throws IOException {
        final Path path = DEPLOYMENTS.resolve(fileName);
        assertThat(path).as("run from the module directory").exists();
        return ProxyYamlUtil.readProxyConfig(path);
    }

    /**
     * What the assembler tells the validator about each destination, derived from configuration
     * alone: the give-up directory is the configured one, or the default under the data directory.
     */
    private static List<ForwardDestinationFacts> facts(final ProxyConfig proxyConfig) {
        final Path dataDir = Path.of("data").toAbsolutePath();
        return proxyConfig.streamAllEnabledForwarders()
                .map(config -> {
                    final ForwardBounds bounds = config.getRetry().toBounds();
                    final FailureDestinationConfig giveUp = config.getFailureDestination();
                    final Path giveUpDirectory;
                    if (giveUp != null && giveUp.getType() == DestinationType.S3) {
                        giveUpDirectory = null;
                    } else if (giveUp != null && giveUp.getPath() != null) {
                        giveUpDirectory = Path.of(giveUp.getPath());
                    } else {
                        giveUpDirectory = dataDir.resolve(DirNames.FORWARDING)
                                .resolve(DirUtil.makeSafeName(config.getName()))
                                .resolve("03_failure");
                    }
                    return new ForwardDestinationFacts(config.getName(), bounds.maxRetryAge(),
                            bounds.retryDelay(), bounds.longestDelay(), giveUpDirectory);
                })
                .toList();
    }
}
