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

package stroom.proxy.app.pipeline.config;

import stroom.proxy.app.ProxyConfig;
import stroom.proxy.app.pipeline.runtime.PipelineStageName;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.dataformat.yaml.YAMLFactory;
import io.dropwizard.jackson.Jackson;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A partially specified {@code stages} block must not silently disable the stages
 * it does not mention.
 * <p>
 * {@code enabled} previously defaulted to {@code false} in every stage config, and
 * omitted stages were filled with bare disabled configs. Naming one stage in order
 * to tune it therefore disabled the rest of the pipeline, and configuration
 * validation reported no error and no warning - the proxy started, did nothing, and
 * looked healthy.
 * </p>
 */
class TestPartialStagesConfig {

    private static final ObjectMapper MAPPER = Jackson.newObjectMapper(new YAMLFactory());

    private static PipelineStagesConfig stagesFrom(final String yaml) throws Exception {
        final ProxyConfig proxyConfig = MAPPER.treeToValue(
                MAPPER.readTree(yaml).get("proxyConfig"), ProxyConfig.class);
        return proxyConfig.getPipelineConfig().getStages();
    }

    private static void assertAllStagesEnabled(final PipelineStagesConfig stages) {
        assertThat(stages.getReceive().isEnabled()).as("receive").isTrue();
        assertThat(stages.getSplitZip().isEnabled()).as("splitZip").isTrue();
        assertThat(stages.getPreAggregate().isEnabled()).as("preAggregate").isTrue();
        assertThat(stages.getAggregate().isEnabled()).as("aggregate").isTrue();
        assertThat(stages.getForward().isEnabled()).as("forward").isTrue();
    }

    @Test
    void testNoPipelineBlockEnablesEverything() throws Exception {
        assertAllStagesEnabled(stagesFrom("""
                proxyConfig: {}
                """));
    }

    /**
     * An empty {@code pipeline} block states nothing about stages, and nothing is no longer read as
     * everything. The merge used to fill this in from the compile-time default, which is why an
     * operator who wrote a partial block silently got all five stages.
     */
    @Test
    void testEmptyPipelineBlockConfiguresNoStages() throws Exception {
        final PipelineStagesConfig stages = stagesFrom("""
                proxyConfig:
                  pipeline: {}
                """);

        assertThat(stages.getConfiguredStages()).isEmpty();
        assertThat(stages.getReceive().isEnabled()).as("receive").isFalse();
        assertThat(stages.getForward().isEnabled()).as("forward").isFalse();
    }

    @Test
    void testIncompleteStagesBlockIsRejected() throws Exception {
        final PipelineStagesConfig stages = stagesFrom("""
                proxyConfig:
                  pipeline:
                    stages:
                      forward:
                        enabled: true
                        inputQueue: forwardingInput
                        threads:
                          consumerThreads: 8
                """);

        // Omission is ambiguous, so it is neither silently enabled nor silently
        // disabled - it is refused, naming each missing stage.
        final PipelineValidationResult result =
                new ProxyPipelineConfigValidator().validate(new ProxyPipelineConfig(null, stages, null));

        assertThat(result.isValid()).isFalse();
        assertThat(result.getErrors())
                .filteredOn(issue -> ProxyPipelineConfigValidator
                        .CODE_STAGE_NOT_CONFIGURED.equals(issue.code()))
                .extracting(PipelineValidationIssue::stageName)
                .containsExactlyInAnyOrder(
                        PipelineStageName.RECEIVE,
                        PipelineStageName.SPLIT_ZIP,
                        PipelineStageName.PRE_AGGREGATE,
                        PipelineStageName.AGGREGATE);
    }

    @Test
    void testUnconfiguredStagesFallBackToDisabledNotEnabled() throws Exception {
        final PipelineStagesConfig stages = stagesFrom("""
                proxyConfig:
                  pipeline:
                    stages:
                      forward:
                        enabled: true
                        inputQueue: forwardingInput
                """);

        // Validation rejects this, but if it were ever bypassed the fallback must
        // be an idle process, never one silently doing work it was not asked to do.
        assertThat(stages.getReceive().isEnabled()).isFalse();
        assertThat(stages.getSplitZip().isEnabled()).isFalse();
        assertThat(stages.getPreAggregate().isEnabled()).isFalse();
        assertThat(stages.getAggregate().isEnabled()).isFalse();
        assertThat(stages.getForward().isEnabled()).isTrue();
    }

    @Test
    void testSinglePurposeNodeIsValidWhenAllStagesAreListed() throws Exception {
        // The distributed deployment shape - one stage per process, everything
        // else explicitly off.
        final PipelineStagesConfig stages = stagesFrom("""
                proxyConfig:
                  pipeline:
                    stages:
                      receive:
                        enabled: false
                      splitZip:
                        enabled: false
                      preAggregate:
                        enabled: false
                      aggregate:
                        enabled: false
                      forward:
                        enabled: true
                        inputQueue: forwardingInput
                        threads:
                          consumerThreads: 16
                """);

        final PipelineValidationResult result =
                new ProxyPipelineConfigValidator().validate(new ProxyPipelineConfig(null, stages, null));

        assertThat(result.isValid()).isTrue();
        assertThat(stages.getForward().getThreads().getConsumerThreads()).isEqualTo(16);
        assertThat(stages.getReceive().isEnabled()).isFalse();
    }

    @Test
    void testListedStageWithoutEnabledIsRecordedAsUnstated() throws Exception {
        final PipelineStagesConfig stages = stagesFrom("""
                proxyConfig:
                  pipeline:
                    stages:
                      receive:
                        outputQueue: preAggregateInput
                        splitZipQueue: splitZipInput
                        fileStore: receiveStore
                        threads:
                          maxConcurrentReceives: 20
                      splitZip:
                        enabled: false
                      preAggregate:
                        enabled: false
                      aggregate:
                        enabled: false
                      forward:
                        enabled: false
                """);

        // Naming a stage no longer means you want it: a block written to tune one setting used to
        // switch the stage on as a side effect. Absence of `enabled` is now recorded as unstated, and
        // the validator rejects it; the fallback is disabled so a bypassed validation idles.
        assertThat(stages.getReceive().isEnabledSpecified())
                .as("receive did not say whether it is enabled")
                .isFalse();
        assertThat(stages.getReceive().isEnabled()).isFalse();
        // The rest of the stage's settings are still read.
        assertThat(stages.getReceive().getThreads().getMaxConcurrentReceives()).isEqualTo(20);
    }

    @Test
    void testDisabledStagesAreReportedAsWarnings() throws Exception {
        final PipelineStagesConfig stages = stagesFrom("""
                proxyConfig:
                  pipeline:
                    stages:
                      receive:
                        enabled: false
                      splitZip:
                        enabled: false
                      preAggregate:
                        enabled: false
                      aggregate:
                        enabled: false
                      forward:
                        enabled: true
                        inputQueue: forwardingInput
                """);

        assertThat(stages.getReceive().isEnabled()).isFalse();
        assertThat(stages.getForward().isEnabled()).isTrue();

        final ProxyPipelineConfig pipelineConfig = new ProxyPipelineConfig(null, stages, null);
        final PipelineValidationResult result =
                new ProxyPipelineConfigValidator().validate(pipelineConfig);

        // Deliberate, so still valid - but every disabled stage is surfaced.
        assertThat(result.isValid()).isTrue();
        assertThat(result.getWarnings())
                .extracting(PipelineValidationIssue::code)
                .filteredOn(ProxyPipelineConfigValidator.CODE_STAGE_DISABLED::equals)
                .hasSize(4);
    }
}
