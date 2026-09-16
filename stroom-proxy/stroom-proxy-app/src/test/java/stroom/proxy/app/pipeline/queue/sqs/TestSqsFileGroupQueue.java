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

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreLocation;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesRequest;
import software.amazon.awssdk.services.sqs.model.GetQueueAttributesResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.QueueAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import java.io.IOException;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSqsFileGroupQueue {

    private static final String QUEUE_NAME = "testSqsQueue";
    private static final String QUEUE_URL = "https://sqs.eu-west-2.amazonaws.com/123456789012/test-queue";

    private StubSqsClient stubClient;
    private FileGroupQueueMessageCodec codec;
    private SqsFileGroupQueue queue;

    @BeforeEach
    void setUp() {
        stubClient = new StubSqsClient();
        codec = new FileGroupQueueMessageCodec();

        queue = new SqsFileGroupQueue(
                QUEUE_NAME,
                QUEUE_URL,
                SqsFileGroupQueue.DEFAULT_VISIBILITY_TIMEOUT_SECONDS,
                0, // No wait time for tests.
                stubClient,
                codec);
    }

    @AfterEach
    void tearDown() {
        if (queue != null) {
            queue.close();
        }
    }

    @Test
    void testPublishSendsCorrectBody() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-1");
        queue.publish(message);

        assertThat(stubClient.sentMessages).hasSize(1);
        final SendMessageRequest sent = stubClient.sentMessages.get(0);
        assertThat(sent.queueUrl()).isEqualTo(QUEUE_URL);

        // Verify the message body round-trips through the codec.
        final FileGroupQueueMessage decoded = codec.fromJson(sent.messageBody());
        assertThat(decoded.messageId()).isEqualTo("fg-1");
    }


    @Test
    void testNextReturnsEmptyWhenNoMessages() throws IOException {
        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isEmpty();
    }

    @Test
    void testNextReturnsItemWhenMessageAvailable() throws IOException {
        final FileGroupQueueMessage message = createMessage("fg-2");
        final String json = codec.toJson(message);
        final String receiptHandle = "receipt-" + UUID.randomUUID();
        final String sqsMessageId = "sqs-msg-" + UUID.randomUUID();

        stubClient.enqueueReceiveMessage(sqsMessageId, receiptHandle, json);

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        assertThat(result.get().getMessage().messageId()).isEqualTo("fg-2");
        assertThat(result.get().getId()).isEqualTo(sqsMessageId);
    }

    @Test
    void testAcknowledgeDeletesMessage() throws IOException {
        final String receiptHandle = "receipt-ack-test";
        stubClient.enqueueReceiveMessage("msg-1", receiptHandle,
                codec.toJson(createMessage("fg-3")));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        result.get().acknowledge();

        assertThat(stubClient.deletedReceiptHandles).hasSize(1);
        assertThat(stubClient.deletedReceiptHandles.get(0)).isEqualTo(receiptHandle);
    }

    @Test
    void testAcknowledgeIsIdempotent() throws IOException {
        stubClient.enqueueReceiveMessage("msg-2", "receipt-idem",
                codec.toJson(createMessage("fg-4")));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        final FileGroupQueueItem item = result.get();
        item.acknowledge();
        // Second acknowledge should be a no-op.
        item.acknowledge();

        assertThat(stubClient.deletedReceiptHandles).hasSize(1);
    }

    @Test
    void testFailChangesVisibilityToZero() throws IOException {
        final String receiptHandle = "receipt-fail-test";
        stubClient.enqueueReceiveMessage("msg-3", receiptHandle,
                codec.toJson(createMessage("fg-5")));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        result.get().fail(new RuntimeException("test error"));

        assertThat(stubClient.visibilityChanges).hasSize(1);
        final ChangeMessageVisibilityRequest change = stubClient.visibilityChanges.get(0);
        assertThat(change.receiptHandle()).isEqualTo(receiptHandle);
        assertThat(change.visibilityTimeout()).isEqualTo(0);

        // No delete should have been called.
        assertThat(stubClient.deletedReceiptHandles).isEmpty();
    }

    @Test
    void testFailIsIdempotent() throws IOException {
        stubClient.enqueueReceiveMessage("msg-4", "receipt-fail-idem",
                codec.toJson(createMessage("fg-6")));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        final FileGroupQueueItem item = result.get();
        item.fail(new RuntimeException("first"));
        // Second fail should be a no-op.
        item.fail(new RuntimeException("second"));

        assertThat(stubClient.visibilityChanges).hasSize(1);
    }

    @Test
    void testCloseClosesClient() {
        queue.close();
        assertThat(stubClient.closed).isTrue();
        queue = null; // Prevent double-close in tearDown.
    }

    @Test
    void testPublishAndConsumeRoundTrip() throws IOException {
        // Publish a message through the queue.
        final FileGroupQueueMessage message = createMessage("fg-roundtrip");
        queue.publish(message);

        // Feed the published body back through the consumer.
        final String publishedBody = stubClient.sentMessages.get(0).messageBody();
        stubClient.enqueueReceiveMessage("rt-msg", "rt-receipt", publishedBody);

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();
        assertThat(result.get().getMessage().messageId()).isEqualTo("fg-roundtrip");
    }

    @Test
    void testReceiveRequestParameters() throws IOException {
        stubClient.enqueueReceiveMessage("msg-params", "receipt-params",
                codec.toJson(createMessage("fg-params")));

        queue.next();

        assertThat(stubClient.receiveRequests).hasSize(1);
        final ReceiveMessageRequest request = stubClient.receiveRequests.get(0);
        assertThat(request.queueUrl()).isEqualTo(QUEUE_URL);
        assertThat(request.maxNumberOfMessages()).isEqualTo(1);
        assertThat(request.visibilityTimeout())
                .isEqualTo(SqsFileGroupQueue.DEFAULT_VISIBILITY_TIMEOUT_SECONDS);
    }

    private FileGroupQueueMessage createMessage(final String fileGroupId) {
        return new FileGroupQueueMessage(
                FileGroupQueueMessage.CURRENT_SCHEMA_VERSION,
                fileGroupId,
                testLocation(),
                null,
                null,
                "receive",
                "test-node",
                Instant.now(),
                null,
                Map.of());
    }

    private static FileStoreLocation testLocation() {
        return FileStoreLocation.filesystem("testStore", Path.of("/tmp/test/store/0000000001"));
    }

    /**
     * The receive count is the discriminator §2.2 needs and the input to §2.4's bound, and SQS
     * does not return it unless the receive asks for it - which this queue did not, so the count was
     * simply unavailable and {@code maxDeliveryAttempts} was documented as "LOCAL_FILESYSTEM only"
     * as a description of that gap rather than as a decision.
     */
    @Test
    void testTheReceiveCountIsRequestedAndReported() throws IOException {
        stubClient.enqueueReceiveMessage("m1", "rh1", codec.toJson(createMessage("fg-1")), 3);

        final FileGroupQueueItem item = queue.next().orElseThrow();

        assertThat(stubClient.receiveRequests.getFirst().messageSystemAttributeNames())
                .as("the count is not returned unless it is asked for")
                .contains(MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT);
        assertThat(item.getDeliveryAttempt()).isEqualTo(3);
    }


    /**
     * Give-up is the queue's redrive policy: the proxy never deletes a failed message however many
     * times it has been received. Deleting it here would hide it from the dead-letter queue.
     */
    @Test
    void testFailMakesTheMessageVisibleAgainWhateverTheReceiveCount() throws IOException {
        stubClient.enqueueReceiveMessage("m1", "rh1", codec.toJson(createMessage("fg-1")), 500);
        final FileGroupQueueItem item = queue.next().orElseThrow();
        assertThat(item.getDeliveryAttempt()).isEqualTo(500);

        item.fail(new RuntimeException("still failing"));

        assertThat(stubClient.deletedReceiptHandles).isEmpty();
        assertThat(stubClient.visibilityChanges).hasSize(1);
        assertThat(stubClient.visibilityChanges.getFirst().visibilityTimeout()).isZero();
    }

    /**
     * The visibility heartbeat is an optimisation - it extends the lease so a long stage does
     * not lose it - and failing to start one must not cost ownership of a message already taken from
     * SQS. It used to propagate {@code RejectedExecutionException} out of {@code next()} after the
     * receive, so no caller ever held the item, nobody could acknowledge or fail it, and the message
     * sat invisible for the whole visibility timeout before SQS handed it back.
     */
    @Test
    void testAMessageIsStillReturnedWhenNoHeartbeatCanBeStarted() throws IOException {
        stubClient.enqueueReceiveMessage("m1", "rh1", codec.toJson(createMessage("fg-1")));

        // Shut the scheduler down under it, which is what close() does.
        queue.close();

        final Optional<FileGroupQueueItem> item = queue.next();

        assertThat(item)
                .as("the message has already left SQS, so the caller must still receive it")
                .isPresent();
        assertThat(item.get().getMessage().messageId()).isEqualTo("fg-1");

        // And it is a real item: the caller can still complete it.
        item.get().acknowledge();
        assertThat(stubClient.deletedReceiptHandles).containsExactly("rh1");
    }

    /**
     * Give-up and retention are the broker's for an SQS queue (queues.md §5.2), so both are checked
     * once at start-up. Retention is the one that loses data: SQS deletes a message
     * {@code MessageRetentionPeriod} after it was sent, a visibility change does not restart that
     * clock, and {@code fail()} here is a visibility change - so a retention shorter than the forward
     * retry window let SQS delete a message still being retried, silently, with nothing in the proxy
     * to say so. AWS's default of four days is shorter than the default maxRetryAge of seven.
     */
    @Test
    void testARetentionNotLongerThanTheForwardRetryWindowRefusesToStart() {
        stubClient.retentionSeconds = Duration.ofDays(4).toSeconds();
        stubClient.redrivePolicy = "{\"deadLetterTargetArn\":\"arn:dlq\",\"maxReceiveCount\":\"10080\"}";

        assertThatThrownBy(() -> queue.requireQueueFitsPipeline(Duration.ofDays(7)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("MessageRetentionPeriod")
                .hasMessageContaining("PT96H")
                .hasMessageContaining("maxRetryAge");

        // Equal is not enough either: the last attempt is made at maxRetryAge.
        stubClient.retentionSeconds = Duration.ofDays(7).toSeconds();
        assertThatThrownBy(() -> queue.requireQueueFitsPipeline(Duration.ofDays(7)))
                .isInstanceOf(IOException.class);
    }

    @Test
    void testAQueueWithoutARedrivePolicyRefusesToStart() {
        stubClient.retentionSeconds = Duration.ofDays(14).toSeconds();
        stubClient.redrivePolicy = null;

        assertThatThrownBy(() -> queue.requireQueueFitsPipeline(Duration.ofDays(7)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("redrive policy");
    }

    @Test
    void testAQueueThatFitsThePipelineStarts() throws IOException {
        stubClient.retentionSeconds = Duration.ofDays(14).toSeconds();
        stubClient.redrivePolicy = "{\"deadLetterTargetArn\":\"arn:dlq\",\"maxReceiveCount\":\"10080\"}";

        queue.requireQueueFitsPipeline(Duration.ofDays(7));
        // With no forward destination configured there is no window to check, only the policy.
        queue.requireQueueFitsPipeline(null);

        assertThat(stubClient.attributeRequests)
                .allSatisfy(request -> assertThat(request.attributeNames()).containsExactlyInAnyOrder(
                        QueueAttributeName.MESSAGE_RETENTION_PERIOD, QueueAttributeName.REDRIVE_POLICY));
    }

    @Test
    void testAnUnreadableRetentionRefusesToStartRatherThanAssumingOne() {
        stubClient.retentionSeconds = null;
        stubClient.redrivePolicy = "{\"maxReceiveCount\":\"10\"}";

        assertThatThrownBy(() -> queue.requireQueueFitsPipeline(Duration.ofDays(7)))
                .isInstanceOf(IOException.class)
                .hasMessageContaining("cannot be read");
    }

    /**
     * A minimal in-memory SQS client stub for unit testing.
     * <p>
     * Only implements the five SQS operations used by {@link SqsFileGroupQueue}:
     * {@code sendMessage}, {@code receiveMessage}, {@code deleteMessage},
     * {@code changeMessageVisibility} and {@code getQueueAttributes}. All other {@link SqsClient} methods
     * throw {@link UnsupportedOperationException}.
     * </p>
     */
    private static final class StubSqsClient implements SqsClient {

        final List<SendMessageRequest> sentMessages = new ArrayList<>();
        final List<ReceiveMessageRequest> receiveRequests = new ArrayList<>();
        final List<String> deletedReceiptHandles = new ArrayList<>();
        final List<ChangeMessageVisibilityRequest> visibilityChanges = new ArrayList<>();
        final ConcurrentLinkedQueue<Message> messageQueue = new ConcurrentLinkedQueue<>();
        final List<GetQueueAttributesRequest> attributeRequests = new ArrayList<>();
        /**
         * What getQueueAttributes reports; null leaves the attribute out, as SQS does when unset.
         */
        Long retentionSeconds = Duration.ofDays(14).toSeconds();
        String redrivePolicy = "{\"deadLetterTargetArn\":\"arn:dlq\",\"maxReceiveCount\":\"10\"}";
        boolean closed = false;

        void enqueueReceiveMessage(final String messageId,
                                   final String receiptHandle,
                                   final String body) {
            enqueueReceiveMessage(messageId, receiptHandle, body, 1);
        }

        /**
         * Enqueue with an explicit ApproximateReceiveCount, as SQS supplies on a redelivery.
         */
        void enqueueReceiveMessage(final String messageId,
                                   final String receiptHandle,
                                   final String body,
                                   final int approximateReceiveCount) {
            messageQueue.add(Message.builder()
                    .messageId(messageId)
                    .receiptHandle(receiptHandle)
                    .body(body)
                    .attributesWithStrings(Map.of(
                            MessageSystemAttributeName.APPROXIMATE_RECEIVE_COUNT.toString(),
                            Integer.toString(approximateReceiveCount)))
                    .build());
        }

        @Override
        public SendMessageResponse sendMessage(final SendMessageRequest request) {
            sentMessages.add(request);
            return SendMessageResponse.builder()
                    .messageId(UUID.randomUUID().toString())
                    .build();
        }

        @Override
        public ReceiveMessageResponse receiveMessage(final ReceiveMessageRequest request) {
            receiveRequests.add(request);
            final Message msg = messageQueue.poll();
            if (msg == null) {
                return ReceiveMessageResponse.builder()
                        .messages(List.of())
                        .build();
            }
            return ReceiveMessageResponse.builder()
                    .messages(List.of(msg))
                    .build();
        }

        @Override
        public DeleteMessageResponse deleteMessage(final DeleteMessageRequest request) {
            deletedReceiptHandles.add(request.receiptHandle());
            return DeleteMessageResponse.builder().build();
        }

        @Override
        public ChangeMessageVisibilityResponse changeMessageVisibility(
                final ChangeMessageVisibilityRequest request) {
            visibilityChanges.add(request);
            return ChangeMessageVisibilityResponse.builder().build();
        }

        @Override
        public GetQueueAttributesResponse getQueueAttributes(final GetQueueAttributesRequest request) {
            attributeRequests.add(request);
            final Map<QueueAttributeName, String> attributes = new HashMap<>();
            if (retentionSeconds != null) {
                attributes.put(QueueAttributeName.MESSAGE_RETENTION_PERIOD, Long.toString(retentionSeconds));
            }
            if (redrivePolicy != null) {
                attributes.put(QueueAttributeName.REDRIVE_POLICY, redrivePolicy);
            }
            return GetQueueAttributesResponse.builder().attributes(attributes).build();
        }

        @Override
        public String serviceName() {
            return "sqs";
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
