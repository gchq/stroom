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

package stroom.proxy.app.pipeline;

import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;

/**
 * AWS SQS-backed implementation of {@link FileGroupQueue}.
 * <p>
 * Each logical queue maps to an SQS queue identified by URL. Messages are
 * serialised as JSON strings using the shared {@link FileGroupQueueMessageCodec}.
 * </p>
 * <p>
 * This implementation uses SQS visibility timeout as the lease mechanism.
 * {@link SqsFileGroupQueueItem#acknowledge()} deletes the SQS message.
 * {@link SqsFileGroupQueueItem#fail(Throwable)} changes the visibility timeout
 * to zero, making the message immediately available for retry (at-least-once
 * semantics).
 * </p>
 */
public class SqsFileGroupQueue implements FileGroupQueue {

    static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 1800; // 30 minutes
    static final int DEFAULT_WAIT_TIME_SECONDS = 20; // SQS long-poll maximum

    private final String name;
    private final String queueUrl;
    private final int visibilityTimeoutSeconds;
    private final int waitTimeSeconds;
    private final SqsClient sqsClient;
    private final FileGroupQueueMessageCodec codec;

    /**
     * Create an SQS queue from a {@link QueueDefinition}.
     *
     * @param name       The logical queue name.
     * @param definition The queue definition containing SQS config.
     * @param codec      The message codec for JSON serialisation.
     */
    public SqsFileGroupQueue(final String name,
                             final QueueDefinition definition,
                             final FileGroupQueueMessageCodec codec) {
        this(
                name,
                requireNonBlank(definition.getQueueUrl(), "definition.queueUrl"),
                resolveVisibilityTimeout(definition),
                resolveWaitTime(definition),
                SqsClient.create(),
                codec);
    }

    /**
     * Test-friendly constructor that accepts a pre-built SQS client.
     */
    SqsFileGroupQueue(final String name,
                      final String queueUrl,
                      final int visibilityTimeoutSeconds,
                      final int waitTimeSeconds,
                      final SqsClient sqsClient,
                      final FileGroupQueueMessageCodec codec) {
        this.name = requireNonBlank(name, "name");
        this.queueUrl = requireNonBlank(queueUrl, "queueUrl");
        this.visibilityTimeoutSeconds = visibilityTimeoutSeconds;
        this.waitTimeSeconds = waitTimeSeconds;
        this.sqsClient = Objects.requireNonNull(sqsClient, "sqsClient");
        this.codec = Objects.requireNonNull(codec, "codec");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public QueueType getType() {
        return QueueType.SQS;
    }

    @Override
    public void publish(final FileGroupQueueMessage message) throws IOException {
        Objects.requireNonNull(message, "message");

        if (!name.equals(message.queueName())) {
            throw new IllegalArgumentException("Message queueName '" + message.queueName()
                                               + "' does not match queue '" + name + "'");
        }

        final String json = codec.toJson(message);

        try {
            sqsClient.sendMessage(SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(json)
                    .build());
        } catch (final Exception e) {
            throw new IOException("Failed to send message to SQS queue " + queueUrl, e);
        }
    }

    @Override
    public Optional<FileGroupQueueItem> next() throws IOException {
        final ReceiveMessageResponse response;
        try {
            response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .visibilityTimeout(visibilityTimeoutSeconds)
                    .waitTimeSeconds(waitTimeSeconds)
                    .build());
        } catch (final Exception e) {
            throw new IOException("Failed to receive message from SQS queue " + queueUrl, e);
        }

        if (response.messages().isEmpty()) {
            return Optional.empty();
        }

        final Message sqsMessage = response.messages().get(0);
        final FileGroupQueueMessage message = codec.fromJson(sqsMessage.body());

        return Optional.of(new SqsFileGroupQueueItem(sqsMessage, message));
    }

    @Override
    public void close() {
        sqsClient.close();
    }

    private static int resolveVisibilityTimeout(final QueueDefinition definition) {
        if (definition.getVisibilityTimeout() != null) {
            return (int) definition.getVisibilityTimeout().getDuration().toSeconds();
        }
        return DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
    }

    private static int resolveWaitTime(final QueueDefinition definition) {
        if (definition.getWaitTime() != null) {
            return (int) definition.getWaitTime().getDuration().toSeconds();
        }
        return DEFAULT_WAIT_TIME_SECONDS;
    }

    private static String requireNonBlank(final String value,
                                          final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }

    /**
     * A leased queue item backed by an SQS {@link Message}.
     */
    private final class SqsFileGroupQueueItem implements FileGroupQueueItem {

        private final Message sqsMessage;
        private final FileGroupQueueMessage message;
        private boolean completed;

        private SqsFileGroupQueueItem(final Message sqsMessage,
                                      final FileGroupQueueMessage message) {
            this.sqsMessage = Objects.requireNonNull(sqsMessage, "sqsMessage");
            this.message = Objects.requireNonNull(message, "message");
        }

        @Override
        public String getId() {
            return sqsMessage.messageId();
        }

        @Override
        public FileGroupQueueMessage getMessage() {
            return message;
        }

        @Override
        public Map<String, String> getMetadata() {
            final Map<String, String> metadata = new LinkedHashMap<>();
            metadata.put("queueName", name);
            metadata.put("queueType", QueueType.SQS.name());
            metadata.put("sqsMessageId", sqsMessage.messageId());
            metadata.put("receiptHandle", sqsMessage.receiptHandle());
            metadata.put("state", completed ? "completed" : "in-flight");
            return Map.copyOf(metadata);
        }

        @Override
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }

            try {
                sqsClient.deleteMessage(DeleteMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(sqsMessage.receiptHandle())
                        .build());
            } catch (final Exception e) {
                throw new IOException("Failed to delete SQS message " + sqsMessage.messageId(), e);
            }

            completed = true;
        }

        @Override
        public void fail(final Throwable error) throws IOException {
            if (completed) {
                return;
            }

            try {
                // Set visibility timeout to 0 to make the message immediately
                // available for retry.
                sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(sqsMessage.receiptHandle())
                        .visibilityTimeout(0)
                        .build());
            } catch (final Exception e) {
                throw new IOException("Failed to change visibility for SQS message "
                                      + sqsMessage.messageId(), e);
            }

            completed = true;
        }

        @Override
        public void close() {
            // No per-item resources to release.
        }
    }
}
