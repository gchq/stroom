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

package stroom.aws.sqs;


import stroom.aws.common.AwsAssumeRole;
import stroom.aws.common.AwsCredentials;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsAtomicConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class SqsConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig, IsAtomicConfig {

    public static final StroomDuration DEFAULT_POLL_FREQUENCY = StroomDuration.ofSeconds(10);

    @JsonProperty
    private final AwsCredentials credentials;
    @JsonProperty
    private final AwsAssumeRole assumeRole;
    @JsonProperty
    private final String awsRegionName;
    @JsonProperty
    private final String awsProfileName;
    @JsonProperty
    private final String queueName;
    @JsonProperty
    private final StroomDuration pollFrequency;

    public SqsConfig() {
        credentials = null;
        assumeRole = null;
        awsRegionName = null;
        awsProfileName = null;
        queueName = null;
        pollFrequency = DEFAULT_POLL_FREQUENCY;
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SqsConfig(@JsonProperty("credentials") final AwsCredentials credentials,
                     @JsonProperty("assumeRole") final AwsAssumeRole assumeRole,
                     @JsonProperty("awsRegionName") final String awsRegionName,
                     @JsonProperty("awsProfileName") final String awsProfileName,
                     @JsonProperty("queueName") final String queueName,
                     @JsonProperty("pollFrequency") final StroomDuration pollFrequency) {
        this.credentials = credentials;
        this.assumeRole = assumeRole;
        this.awsRegionName = awsRegionName;
        this.awsProfileName = awsProfileName;
        this.queueName = queueName;
        this.pollFrequency = Objects.requireNonNullElse(pollFrequency, DEFAULT_POLL_FREQUENCY);
    }

    @JsonProperty
    public AwsCredentials getCredentials() {
        return credentials;
    }

    @JsonProperty
    public AwsAssumeRole getAssumeRole() {
        return assumeRole;
    }

    @NotBlank
    @JsonProperty
    public String getAwsRegionName() {
        return awsRegionName;
    }

    @JsonProperty
    public String getAwsProfileName() {
        return awsProfileName;
    }

    @NotBlank
    @JsonProperty
    public String getQueueName() {
        return queueName;
    }

    @JsonProperty
    public StroomDuration getPollFrequency() {
        return pollFrequency;
    }

    public static Builder builder() {
        return new Builder();
    }

    public static Builder builder(final SqsConfig sqsConfig) {
        return new Builder(sqsConfig);
    }

    public Builder copy() {
        return new Builder(this);
    }


    // --------------------------------------------------------------------------------


    public static class Builder {

        private AwsCredentials credentials;
        private AwsAssumeRole assumeRole;
        private String awsRegionName;
        private String awsProfileName;
        private String queueName;
        private StroomDuration pollFrequency = DEFAULT_POLL_FREQUENCY;

        public Builder() {
        }

        public Builder(final SqsConfig sqsConfig) {
            this.credentials = sqsConfig.credentials;
            this.assumeRole = sqsConfig.assumeRole;
            this.awsRegionName = sqsConfig.awsRegionName;
            this.awsProfileName = sqsConfig.awsProfileName;
            this.queueName = sqsConfig.queueName;
            this.pollFrequency = sqsConfig.pollFrequency;
        }

        public Builder credentials(final AwsCredentials credentials) {
            this.credentials = credentials;
            return this;
        }

        public Builder assumeRole(final AwsAssumeRole assumeRole) {
            this.assumeRole = assumeRole;
            return this;
        }

        public Builder awsRegionName(final String awsRegionName) {
            this.awsRegionName = awsRegionName;
            return this;
        }

        public Builder awsProfileName(final String awsProfileName) {
            this.awsProfileName = awsProfileName;
            return this;
        }

        public Builder queueName(final String queueName) {
            this.queueName = queueName;
            return this;
        }

        public Builder pollFrequency(final StroomDuration pollFrequency) {
            this.pollFrequency = pollFrequency;
            return this;
        }

        public SqsConfig build() {
            return new SqsConfig(
                    credentials,
                    assumeRole,
                    awsRegionName,
                    awsProfileName,
                    queueName,
                    pollFrequency
            );
        }
    }
}
