/*
 * Copyright 2022 Crown Copyright
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


import stroom.aws.common.shared.AwsAssumeRole;
import stroom.aws.common.shared.AwsCredentials;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.shared.AbstractConfig;
import stroom.util.shared.IsAtomicConfig;
import stroom.util.shared.IsProxyConfig;
import stroom.util.shared.IsStroomConfig;
import stroom.util.shared.NotInjectableConfig;
import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonPropertyDescription;
import com.fasterxml.jackson.annotation.JsonPropertyOrder;
import jakarta.validation.constraints.NotBlank;

import java.util.Objects;

@NotInjectableConfig
@JsonPropertyOrder(alphabetic = true)
public class SqsConfig
        extends AbstractConfig
        implements IsStroomConfig, IsProxyConfig, IsAtomicConfig {

    public static final StroomDuration DEFAULT_POLL_MAX_WAIT_TIME = StroomDuration.ofSeconds(10);
    public static final StroomDuration DEFAULT_VISIBILITY_TIMEOUT = StroomDuration.ofMinutes(3);
    public static final int DEFAULT_MAX_NUMBER_OF_MESSAGES = 1_000;

    @JsonProperty
    private final AwsCredentials credentials;
    @JsonProperty
    private final AwsAssumeRole assumeRole;
    @JsonProperty
    private final String awsRegionName;
    @JsonProperty
    private final String queueUrl;
    @JsonProperty
    private final String deadLetterQueueUrl;
    @JsonProperty
    private final StroomDuration pollMaxWaitTime;
    @JsonProperty
    private final StroomDuration visibilityTimeout;
    @JsonProperty
    private final int maxNumberOfMessages;
    @JsonProperty
    private final HttpClientConfiguration httpClient;

    public SqsConfig() {
        credentials = null;
        assumeRole = null;
        awsRegionName = null;
        queueUrl = null;
        deadLetterQueueUrl = null;
        pollMaxWaitTime = DEFAULT_POLL_MAX_WAIT_TIME;
        visibilityTimeout = DEFAULT_VISIBILITY_TIMEOUT;
        maxNumberOfMessages = DEFAULT_MAX_NUMBER_OF_MESSAGES;
        httpClient = createDefaultHttpClientConfiguration();
    }

    @SuppressWarnings("unused")
    @JsonCreator
    public SqsConfig(@JsonProperty("credentials") final AwsCredentials credentials,
                     @JsonProperty("assumeRole") final AwsAssumeRole assumeRole,
                     @JsonProperty("awsRegionName") final String awsRegionName,
                     @JsonProperty("queueUrl") final String queueUrl,
                     @JsonProperty("deadLetterQueueUrl") final String deadLetterQueueUrl,
                     @JsonProperty("pollMaxWaitTime") final StroomDuration pollMaxWaitTime,
                     @JsonProperty("visibilityTimeout") final StroomDuration visibilityTimeout,
                     @JsonProperty("maxNumberOfMessages") final Integer maxNumberOfMessages,
                     @JsonProperty("httpClient") final HttpClientConfiguration httpClient) {
        this.credentials = credentials;
        this.assumeRole = assumeRole;
        this.awsRegionName = awsRegionName;
        this.queueUrl = queueUrl;
        this.deadLetterQueueUrl = deadLetterQueueUrl;
        this.pollMaxWaitTime = Objects.requireNonNullElse(pollMaxWaitTime, DEFAULT_POLL_MAX_WAIT_TIME);
        this.visibilityTimeout = Objects.requireNonNullElse(visibilityTimeout, DEFAULT_VISIBILITY_TIMEOUT);
        this.maxNumberOfMessages = Objects.requireNonNullElse(maxNumberOfMessages, DEFAULT_MAX_NUMBER_OF_MESSAGES);
        this.httpClient = Objects.requireNonNullElseGet(httpClient, this::createDefaultHttpClientConfiguration);
    }

    private HttpClientConfiguration createDefaultHttpClientConfiguration() {
        return HttpClientConfiguration
                .builder()
                .timeout(HttpClientConfiguration.DEFAULT_TIMEOUT)
                .connectionTimeout(HttpClientConfiguration.DEFAULT_CONNECTION_TIMEOUT)
                .connectionRequestTimeout(HttpClientConfiguration.DEFAULT_CONNECTION_REQUEST_TIMEOUT)
                .timeToLive(HttpClientConfiguration.DEFAULT_TIME_TO_LIVE)
                .build();
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

    @NotBlank
    @JsonPropertyDescription("The URL of the SQS queue to poll.")
    @JsonProperty
    public String getQueueUrl() {
        return queueUrl;
    }

    @NotBlank
    @JsonPropertyDescription("The URL of the SQS queue to send messages that have failed to be processed to. " +
                             "If not set then messages that fail to be processed will be deleted from the " +
                             "main queue.")
    @JsonProperty
    public String getDeadLetterQueueUrl() {
        return deadLetterQueueUrl;
    }

    @JsonPropertyDescription(
            "This is the maximum time for the SQS client to wait for a message to be " +
            "available on the queue. This should be shorter than the HTTP connection timeout.")
    @JsonProperty
    public StroomDuration getPollMaxWaitTime() {
        return pollMaxWaitTime;
    }

    @JsonPropertyDescription(
            "The time that a received message will be made invisible on the queue to prevent it from being " +
            "processed by another node. Processed messages are deleted from the queue, so this duration should " +
            "be longer than the time it takes to process a message.")
    @JsonProperty
    public StroomDuration getVisibilityTimeout() {
        return visibilityTimeout;
    }

    @JsonPropertyDescription(
            "The maximum number of messages to receive from the queue in a single call to receiveMessage.")
    @JsonProperty
    public int getMaxNumberOfMessages() {
        return maxNumberOfMessages;
    }

    @JsonProperty
    public HttpClientConfiguration getHttpClient() {
        return httpClient;
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

    @Override
    public boolean equals(final Object o) {
        if (o == null || getClass() != o.getClass()) {
            return false;
        }
        final SqsConfig sqsConfig = (SqsConfig) o;
        return maxNumberOfMessages == sqsConfig.maxNumberOfMessages && Objects.equals(credentials,
                sqsConfig.credentials) && Objects.equals(assumeRole,
                sqsConfig.assumeRole) && Objects.equals(awsRegionName,
                sqsConfig.awsRegionName) && Objects.equals(queueUrl,
                sqsConfig.queueUrl) && Objects.equals(deadLetterQueueUrl,
                sqsConfig.deadLetterQueueUrl) && Objects.equals(pollMaxWaitTime,
                sqsConfig.pollMaxWaitTime)
               && Objects.equals(visibilityTimeout, sqsConfig.visibilityTimeout)
               && Objects.equals(httpClient, sqsConfig.httpClient);
    }

    @Override
    public int hashCode() {
        return Objects.hash(
                credentials,
                assumeRole,
                awsRegionName,
                queueUrl,
                deadLetterQueueUrl,
                pollMaxWaitTime,
                visibilityTimeout,
                maxNumberOfMessages,
                httpClient);
    }

    @Override
    public String toString() {
        return "SqsConfig{" +
               "credentials=" + credentials +
               ", assumeRole=" + assumeRole +
               ", awsRegionName='" + awsRegionName + '\'' +
               ", queueUrl='" + queueUrl + '\'' +
               ", deadLetterQueueUrl='" + deadLetterQueueUrl + '\'' +
               ", pollMaxWaitTime=" + pollMaxWaitTime +
               ", visibilityTimeout=" + visibilityTimeout +
               ", maxNumberOfMessages=" + maxNumberOfMessages +
               ", httpClient=" + httpClient +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class Builder {


        private AwsCredentials credentials;
        private AwsAssumeRole assumeRole;
        private String awsRegionName;
        private String queueUrl;
        private String deadLetterQueueUrl;
        private StroomDuration pollMaxWaitTime = DEFAULT_POLL_MAX_WAIT_TIME;
        private StroomDuration visibilityTimeout = DEFAULT_VISIBILITY_TIMEOUT;
        private int maxNumberOfMessages = DEFAULT_MAX_NUMBER_OF_MESSAGES;
        private HttpClientConfiguration httpClient;

        public Builder() {
        }

        public Builder(final SqsConfig sqsConfig) {
            this.credentials = sqsConfig.credentials;
            this.assumeRole = sqsConfig.assumeRole;
            this.awsRegionName = sqsConfig.awsRegionName;
            this.queueUrl = sqsConfig.queueUrl;
            this.deadLetterQueueUrl = sqsConfig.deadLetterQueueUrl;
            this.pollMaxWaitTime = sqsConfig.pollMaxWaitTime;
            this.visibilityTimeout = sqsConfig.visibilityTimeout;
            this.maxNumberOfMessages = sqsConfig.maxNumberOfMessages;
            this.httpClient = sqsConfig.httpClient;
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

        public Builder queueUrl(final String queueUrl) {
            this.queueUrl = queueUrl;
            return this;
        }

        public Builder deadLetterQueueUrl(final String deadLetterQueueUrl) {
            this.deadLetterQueueUrl = deadLetterQueueUrl;
            return this;
        }

        public Builder pollMaxWaitTime(final StroomDuration pollMaxWaitTime) {
            this.pollMaxWaitTime = pollMaxWaitTime;
            return this;
        }

        public Builder visibilityTimeout(final StroomDuration visibilityTimeout) {
            this.visibilityTimeout = visibilityTimeout;
            return this;
        }

        public Builder maxNumberOfMessages(final int maxNumberOfMessages) {
            this.maxNumberOfMessages = maxNumberOfMessages;
            return this;
        }

        public Builder httpClient(final HttpClientConfiguration httpClient) {
            this.httpClient = httpClient;
            return this;
        }

        public SqsConfig build() {
            return new SqsConfig(
                    credentials,
                    assumeRole,
                    awsRegionName,
                    queueUrl,
                    deadLetterQueueUrl,
                    pollMaxWaitTime,
                    visibilityTimeout,
                    maxNumberOfMessages,
                    httpClient);
        }
    }
}
