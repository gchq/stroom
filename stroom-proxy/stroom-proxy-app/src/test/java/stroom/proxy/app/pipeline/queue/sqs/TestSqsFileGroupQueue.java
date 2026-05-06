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

package stroom.proxy.app.pipeline.queue.sqs;

import stroom.proxy.app.pipeline.queue.FileGroupQueueItem;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessage;
import stroom.proxy.app.pipeline.queue.FileGroupQueueMessageCodec;
import stroom.proxy.app.pipeline.queue.QueueType;
import stroom.proxy.app.pipeline.store.FileStoreLocation;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityRequest;
import software.amazon.awssdk.services.sqs.model.ChangeMessageVisibilityResponse;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.DeleteMessageResponse;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
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
    void testNameAndType() {
        assertThat(queue.getName()).isEqualTo(QUEUE_NAME);
        assertThat(queue.getType()).isEqualTo(QueueType.SQS);
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
        assertThat(decoded.fileGroupId()).isEqualTo("fg-1");
        assertThat(decoded.queueName()).isEqualTo(QUEUE_NAME);
    }

    @Test
    void testPublishRejectsWrongQueueName() {
        final FileGroupQueueMessage message = FileGroupQueueMessage.create(
                "wrongQueue",
                "fg-1",
                testLocation(),
                "receive",
                "node-1",
                null,
                Map.of());

        assertThatThrownBy(() -> queue.publish(message))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("wrongQueue");
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
        assertThat(result.get().getMessage().fileGroupId()).isEqualTo("fg-2");
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
    void testItemMetadata() throws IOException {
        final String receiptHandle = "receipt-meta";
        final String sqsMessageId = "sqs-meta-id";
        stubClient.enqueueReceiveMessage(sqsMessageId, receiptHandle,
                codec.toJson(createMessage("fg-7")));

        final Optional<FileGroupQueueItem> result = queue.next();
        assertThat(result).isPresent();

        final Map<String, String> metadata = result.get().getMetadata();
        assertThat(metadata).containsEntry("queueName", QUEUE_NAME);
        assertThat(metadata).containsEntry("queueType", "SQS");
        assertThat(metadata).containsEntry("sqsMessageId", sqsMessageId);
        assertThat(metadata).containsEntry("receiptHandle", receiptHandle);
        assertThat(metadata).containsEntry("state", "in-flight");
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
        assertThat(result.get().getMessage().fileGroupId()).isEqualTo("fg-roundtrip");
        assertThat(result.get().getMessage().queueName()).isEqualTo(QUEUE_NAME);
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
        return FileGroupQueueMessage.create(
                QUEUE_NAME,
                fileGroupId,
                testLocation(),
                "receive",
                "test-node",
                null,
                Map.of());
    }

    private static FileStoreLocation testLocation() {
        return FileStoreLocation.localFileSystem("testStore", Path.of("/tmp/test/store/0000000001"));
    }

    /**
     * A minimal in-memory SQS client stub for unit testing.
     * <p>
     * Only implements the four SQS operations used by {@link SqsFileGroupQueue}:
     * {@code sendMessage}, {@code receiveMessage}, {@code deleteMessage}, and
     * {@code changeMessageVisibility}. All other {@link SqsClient} methods
     * throw {@link UnsupportedOperationException}.
     * </p>
     */
    private static final class StubSqsClient implements SqsClient {

        final List<SendMessageRequest> sentMessages = new ArrayList<>();
        final List<ReceiveMessageRequest> receiveRequests = new ArrayList<>();
        final List<String> deletedReceiptHandles = new ArrayList<>();
        final List<ChangeMessageVisibilityRequest> visibilityChanges = new ArrayList<>();
        final ConcurrentLinkedQueue<Message> messageQueue = new ConcurrentLinkedQueue<>();
        boolean closed = false;

        void enqueueReceiveMessage(final String messageId,
                                   final String receiptHandle,
                                   final String body) {
            messageQueue.add(Message.builder()
                    .messageId(messageId)
                    .receiptHandle(receiptHandle)
                    .body(body)
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
        public String serviceName() {
            return "sqs";
        }

        @Override
        public void close() {
            closed = true;
        }
    }
}
