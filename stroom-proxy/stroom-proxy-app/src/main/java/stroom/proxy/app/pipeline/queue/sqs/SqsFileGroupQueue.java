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

package stroom.proxy.app.pipeline.queue.sqs;

import stroom.proxy.app.pipeline.queue.FileGroupQueue;
import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueDefinition;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import com.codahale.metrics.health.HealthCheck.Result;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.ScheduledFuture;
import java.util.concurrent.TimeUnit;

/**
 * A {@link FileGroupQueue} on SQS. Shared mode only; the broker owns the claim.
 * <p>
 * {@code next()} receives one message with the configured visibility timeout and starts a heartbeat
 * that extends it while the item is held, so a slow stage is not redelivered mid-work.
 * {@code acknowledge()} deletes the message; {@code fail()} makes it visible again at once and lets
 * SQS count the receive; {@code close()} without completing lets the timeout lapse.
 * </p>
 * <p>
 * Give-up is SQS's: the queue's redrive policy sets the bound and its dead-letter queue is where a
 * human acts. This class neither counts attempts nor writes anything to local disk. So is retention:
 * at start-up the queue is checked for a redrive policy and for a {@code MessageRetentionPeriod}
 * longer than the forward retry window, and the proxy refuses to start without either
 * ({@link #requireQueueFitsPipeline}).
 * </p>
 */
public class SqsFileGroupQueue implements FileGroupQueue {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqsFileGroupQueue.class);

    static final int DEFAULT_VISIBILITY_TIMEOUT_SECONDS = 1800;
    static final int DEFAULT_WAIT_TIME_SECONDS = 20; // the SQS long-poll maximum
    private static final int HEARTBEAT_THREADS = 4;

    private final String name;
    private final String queueUrl;
    private final int visibilityTimeoutSeconds;
    private final int waitTimeSeconds;
    private final SqsClient sqsClient;
    private final FileGroupQueueMessageCodec codec;
    private final ScheduledExecutorService heartbeatScheduler;
    private final Map<String, ScheduledFuture<?>> heartbeatTasks = new ConcurrentHashMap<>();
    private final SqsHeartbeatCounters heartbeatCounters = new SqsHeartbeatCounters();

    /**
     * @param longestForwardRetryAge The longest {@code maxRetryAge} among the forward destinations,
     *                               which the queue's retention must exceed; null when none is
     *                               configured, in which case only the redrive policy is checked.
     * @throws IOException If the queue's attributes cannot be read, it has no redrive policy, or its
     *                     retention is not longer than the retry window. The proxy does not start.
     */
    public SqsFileGroupQueue(final String name,
                             final QueueDefinition definition,
                             final FileGroupQueueMessageCodec codec,
                             final Duration longestForwardRetryAge) throws IOException {
        this(name,
                requireNonBlank(definition.getQueueUrl(), "definition.queueUrl"),
                resolveVisibilityTimeout(definition),
                resolveWaitTime(definition),
                SqsClient.create(),
                codec);
        try {
            requireQueueFitsPipeline(longestForwardRetryAge);
        } catch (final IOException | RuntimeException e) {
            close();
            throw e;
        }
    }

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
        this.heartbeatScheduler = Executors.newScheduledThreadPool(HEARTBEAT_THREADS, runnable -> {
            final Thread thread = new Thread(runnable, "sqs-heartbeat-" + name);
            thread.setDaemon(true);
            return thread;
        });
    }

    /**
     * Give-up and retention are both the broker's for an SQS queue, so both are checked once at
     * start-up, as Kafka's dead-letter topic is. The queue must have a redrive policy, or a message
     * that cannot be processed is redelivered for ever with no bound and nowhere a human can find it
     * (Q7). And its {@code MessageRetentionPeriod} must exceed the longest forward retry window: SQS
     * deletes a message that age after it was <em>sent</em>, a visibility change does not reset that
     * clock, and {@code fail()} here is a visibility change - so a shorter retention lets SQS delete a
     * message still being retried, silently, and the group it names becomes an orphan the store's
     * sweep removes. R9: a control that cannot function stops the proxy rather than degrading quietly.
     *
     * @param longestForwardRetryAge The bound the retention must exceed; null skips that half.
     */
    void requireQueueFitsPipeline(final Duration longestForwardRetryAge) throws IOException {
        final Map<QueueAttributeName, String> attributes;
        try {
            attributes = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                            .queueUrl(queueUrl)
                            .attributeNames(QueueAttributeName.MESSAGE_RETENTION_PERIOD,
                                    QueueAttributeName.REDRIVE_POLICY)
                            .build())
                    .attributes();
        } catch (final RuntimeException e) {
            throw new IOException(LogUtil.message(
                    "Queue '{}' could not read the attributes of {}, so its redrive policy and retention "
                    + "cannot be checked", name, queueUrl), e);
        }

        final String redrivePolicy = attributes.get(QueueAttributeName.REDRIVE_POLICY);
        if (redrivePolicy == null || redrivePolicy.isBlank()) {
            throw new IOException(LogUtil.message(
                    "Queue '{}' at {} has no redrive policy. Give-up on an SQS queue is the queue's own: "
                    + "configure a redrive policy whose maxReceiveCount is the bound and whose dead-letter "
                    + "queue is where a human acts, or a message that cannot be processed is redelivered "
                    + "for ever.", name, queueUrl));
        }

        final String rawRetention = attributes.get(QueueAttributeName.MESSAGE_RETENTION_PERIOD);
        final Duration retention;
        try {
            retention = Duration.ofSeconds(Long.parseLong(Objects.requireNonNull(rawRetention).trim()));
        } catch (final NullPointerException | NumberFormatException e) {
            throw new IOException(LogUtil.message(
                    "Queue '{}' at {} reported a MessageRetentionPeriod of '{}', which cannot be read, so "
                    + "its retention cannot be checked against the forward retry window",
                    name, queueUrl, rawRetention), e);
        }
        if (longestForwardRetryAge != null && retention.compareTo(longestForwardRetryAge) <= 0) {
            throw new IOException(LogUtil.message(
                    "Queue '{}' at {} has a MessageRetentionPeriod of {}, which does not exceed the longest "
                    + "forward maxRetryAge of {}. SQS would delete a message still being retried - silently, "
                    + "since a visibility change does not restart retention - and the file group it names "
                    + "would be left for the store's sweep. Raise the queue's MessageRetentionPeriod (the "
                    + "maximum is 14 days) or lower maxRetryAge.",
                    name, queueUrl, retention, longestForwardRetryAge));
        }
        LOGGER.info(() -> LogUtil.message(
                "Queue '{}' at {}: MessageRetentionPeriod {}, redrive policy {}",
                name, queueUrl, retention, redrivePolicy));
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
        final String json = codec.toJson(message);
        try {
            sqsClient.sendMessage(SendMessageRequest.builder().queueUrl(queueUrl).messageBody(json).build());
        } catch (final Exception e) {
            throw new IOException("Failed to send message to SQS queue " + queueUrl, e);
        }
    }

    /**
     * SQS long-polls for up to {@code waitTime} itself; {@code maxWait} adds nothing to that and is
     * not used.
     */
    @Override
    public Optional<FileGroupQueueItem> next(final Duration maxWait) throws IOException {
        final ReceiveMessageResponse response;
        try {
            response = sqsClient.receiveMessage(ReceiveMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .maxNumberOfMessages(1)
                    .visibilityTimeout(visibilityTimeoutSeconds)
                    .waitTimeSeconds(waitTimeSeconds)
                    .messageSystemAttributeNames(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT)
                    .build());
        } catch (final Exception e) {
            throw new IOException("Failed to receive message from SQS queue " + queueUrl, e);
        }
        if (response.messages().isEmpty()) {
            return Optional.empty();
        }
        final Message sqsMessage = response.messages().get(0);
        final Item item = new Item(sqsMessage, codec.fromJson(sqsMessage.body()));
        startHeartbeat(item);
        return Optional.of(item);
    }

    @Override
    public void close() {
        heartbeatScheduler.shutdownNow();
        heartbeatTasks.clear();
        sqsClient.close();
    }

    int getActiveHeartbeatCount() {
        return heartbeatTasks.size();
    }

    public SqsHeartbeatCounters getHeartbeatCounters() {
        return heartbeatCounters;
    }

    @Override
    public Result healthCheck() {
        try {
            final GetQueueAttributesResponse response = sqsClient.getQueueAttributes(GetQueueAttributesRequest.builder()
                    .queueUrl(queueUrl)
                    .attributeNames(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES,
                            QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE)
                    .build());
            return Result.builder()
                    .healthy()
                    .withDetail("queueUrl", queueUrl)
                    .withDetail("approximateMessages", Long.parseLong(
                            response.attributes().getOrDefault(QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES, "0")))
                    .withDetail("approximateInFlight", Long.parseLong(
                            response.attributes().getOrDefault(
                                    QueueAttributeName.APPROXIMATE_NUMBER_OF_MESSAGES_NOT_VISIBLE, "0")))
                    .withDetail("activeHeartbeats", getActiveHeartbeatCount())
                    .build();
        } catch (final Exception e) {
            return Result.builder()
                    .unhealthy()
                    .withMessage("SQS queue health check failed for %s: %s", queueUrl, e.getMessage())
                    .build();
        }
    }

    private void startHeartbeat(final Item item) {
        final long intervalSeconds = Math.max(1, (visibilityTimeoutSeconds * 2L) / 3);
        try {
            heartbeatTasks.put(item.receiptHandle(), heartbeatScheduler.scheduleAtFixedRate(
                    () -> extendVisibility(item), intervalSeconds, intervalSeconds, TimeUnit.SECONDS));
        } catch (final RejectedExecutionException e) {
            // The scheduler is shut down. The item is still the caller's, with only the initial
            // visibility timeout rather than an extended one.
            LOGGER.warn(() -> LogUtil.message(
                    "Queue '{}' could not start a visibility heartbeat for message {}; it has the {}s timeout only",
                    name, item.getId(), visibilityTimeoutSeconds));
        }
    }

    private void stopHeartbeat(final Item item) {
        final ScheduledFuture<?> future = heartbeatTasks.remove(item.receiptHandle());
        if (future != null) {
            future.cancel(false);
            heartbeatCounters.incrementCancelledCount();
        }
    }

    private void extendVisibility(final Item item) {
        heartbeatCounters.incrementAttemptCount();
        try {
            sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(item.receiptHandle())
                    .visibilityTimeout(visibilityTimeoutSeconds)
                    .build());
            heartbeatCounters.incrementSuccessCount();
        } catch (final Exception e) {
            heartbeatCounters.incrementFailureCount();
            LOGGER.warn(() -> LogUtil.message(
                    "Failed to extend visibility for SQS message {} on queue {}", item.getId(), name), e);
        }
    }

    private static int resolveVisibilityTimeout(final QueueDefinition definition) {
        return definition.getVisibilityTimeout() != null
                ? (int) definition.getVisibilityTimeout().getDuration().toSeconds()
                : DEFAULT_VISIBILITY_TIMEOUT_SECONDS;
    }

    private static int resolveWaitTime(final QueueDefinition definition) {
        return definition.getWaitTime() != null
                ? (int) definition.getWaitTime().getDuration().toSeconds()
                : DEFAULT_WAIT_TIME_SECONDS;
    }

    private static String requireNonBlank(final String value, final String fieldName) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(fieldName + " must not be blank");
        }
        return value;
    }


    // --------------------------------------------------------------------------------


    private final class Item implements FileGroupQueueItem {

        private final Message sqsMessage;
        private final FileGroupQueueMessage message;
        private boolean completed;

        private Item(final Message sqsMessage, final FileGroupQueueMessage message) {
            this.sqsMessage = sqsMessage;
            this.message = message;
        }

        String receiptHandle() {
            return sqsMessage.receiptHandle();
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
        public int getDeliveryAttempt() {
            final String raw = sqsMessage.attributesAsStrings()
                    .get(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT.toString());
            if (raw == null) {
                return 1;
            }
            try {
                return Math.max(1, Integer.parseInt(raw.trim()));
            } catch (final NumberFormatException e) {
                return 1;
            }
        }

        @Override
        public void acknowledge() throws IOException {
            if (completed) {
                return;
            }
            stopHeartbeat(this);
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
            stopHeartbeat(this);
            try {
                sqsClient.changeMessageVisibility(ChangeMessageVisibilityRequest.builder()
                        .queueUrl(queueUrl)
                        .receiptHandle(sqsMessage.receiptHandle())
                        .visibilityTimeout(0)
                        .build());
            } catch (final Exception e) {
                throw new IOException("Failed to change visibility for SQS message " + sqsMessage.messageId(), e);
            }
            completed = true;
        }

        @Override
        public void close() {
            // The visibility timeout releases an uncompleted claim; nothing to do but stop extending it.
            stopHeartbeat(this);
        }
    }
}
