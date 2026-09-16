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

import stroom.util.time.StroomDuration;

import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import org.junit.jupiter.api.Test;

import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@link SqsConnectorConfig} round-tripping and constraint enforcement.
 * <p>
 * Two defects motivated these. {@code Builder.awsProfileName()} assigned
 * {@code this.queueName}, so setting the profile name silently overwrote the queue
 * name and the profile name could not be set at all - and neither property was read
 * by {@code SqsConnector} anyway, so both have been removed. Separately, the
 * {@code @NotBlank} constraints here were never enforced: bean validation does not
 * cascade into a collection without {@code @Valid}, and the recursive config walker
 * deliberately skips collections, so a connector with no {@code queueUrl} started
 * cleanly and failed later at runtime.
 * </p>
 */
class TestSqsConnectorConfig {

    private final Validator validator = Validation.buildDefaultValidatorFactory().getValidator();

    private Set<String> violatedProperties(final Object target) {
        return validator.validate(target)
                .stream()
                .map(ConstraintViolation::getPropertyPath)
                .map(Object::toString)
                .collect(Collectors.toSet());
    }

    @Test
    void testBuilderRoundTripsEveryProperty() {
        final SqsConnectorConfig config = SqsConnectorConfig.builder()
                .awsRegionName("eu-west-2")
                .queueUrl("https://sqs.eu-west-2.amazonaws.com/123456789012/inbound")
                .pollFrequency(StroomDuration.ofSeconds(30))
                .build();

        assertThat(config.getAwsRegionName()).isEqualTo("eu-west-2");
        assertThat(config.getQueueUrl())
                .isEqualTo("https://sqs.eu-west-2.amazonaws.com/123456789012/inbound");
        assertThat(config.getPollFrequency()).isEqualTo(StroomDuration.ofSeconds(30));
    }

    @Test
    void testRegionAndQueueUrlAreRequired() {
        assertThat(violatedProperties(SqsConnectorConfig.builder().build()))
                .containsExactlyInAnyOrder("awsRegionName", "queueUrl");
    }

    @Test
    void testAFullyPopulatedConnectorIsValid() {
        final SqsConnectorConfig config = SqsConnectorConfig.builder()
                .awsRegionName("eu-west-2")
                .queueUrl("https://sqs.eu-west-2.amazonaws.com/123456789012/inbound")
                .build();

        assertThat(violatedProperties(config)).isEmpty();
    }

    @Test
    void testConstraintsAreReachedThroughProxyConfig() {
        // Without @Valid on ProxyConfig.getSqsConnectors() these violations are
        // invisible - validation simply does not descend into the list.
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addSqsConnector(SqsConnectorConfig.builder().build())
                .build();

        assertThat(violatedProperties(proxyConfig))
                .contains("sqsConnectors[0].awsRegionName", "sqsConnectors[0].queueUrl");
    }

    @Test
    void testValidConnectorsProduceNoViolationsThroughProxyConfig() {
        final ProxyConfig proxyConfig = ProxyConfig.builder()
                .addSqsConnector(SqsConnectorConfig.builder()
                        .awsRegionName("eu-west-2")
                        .queueUrl("https://sqs.eu-west-2.amazonaws.com/123456789012/inbound")
                        .build())
                .build();

        assertThat(violatedProperties(proxyConfig))
                .noneMatch(p -> p.startsWith("sqsConnectors"));
    }
}
