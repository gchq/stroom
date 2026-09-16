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

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.StroomStatusCode;
import stroom.proxy.app.event.EventStore;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.receive.common.StroomStreamException;
import stroom.security.api.CommonSecurityContext;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;

import software.amazon.awssdk.auth.credentials.DefaultCredentialsProvider;
import software.amazon.awssdk.regions.Region;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;

import java.util.List;
import java.util.Map;

/**
 * Consumes application messages from an external SQS queue into the event store. Not the
 * pipeline's SQS queue: that carries reference messages between stages.
 * <p>
 * A message carries no credentials, so the trust boundary is write access to the queue; what is
 * elevated is this proxy's right to check feed status. A message is deleted once the store has
 * accepted or dropped it. One the policy rejects, or that fails for any other reason, is left on
 * the queue and redelivered; the bound on that is the queue's own redrive policy.
 * </p>
 */
public class EventStoreSqsConnector implements AutoCloseable {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(EventStoreSqsConnector.class);

    /**
     * SQS rejects a longer wait.
     */
    private static final int MAX_SQS_LONG_POLL_SECONDS = 20;

    /**
     * One poll is a tick of work, not a drain: whatever is left stays on SQS for the next tick.
     */
    private static final int BATCHES_PER_POLL = 100;

    /**
     * The default provider is a shared singleton no caller can safely close, so it is built once.
     */
    private static final DefaultCredentialsProvider CREDENTIALS_PROVIDER =
            DefaultCredentialsProvider.builder().build();

    private final EventStore eventStore;
    private final SqsClient sqsClient;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final CommonSecurityContext securityContext;
    private final String queueUrl;
    private final int waitTimeSeconds;

    public EventStoreSqsConnector(final EventStore eventStore,
                                  final SqsConnectorConfig config,
                                  final ReceiptIdGenerator receiptIdGenerator,
                                  final CommonSecurityContext securityContext) {
        this.eventStore = eventStore;
        this.receiptIdGenerator = receiptIdGenerator;
        this.securityContext = securityContext;
        this.sqsClient = SqsClient.builder()
                .region(Region.of(config.getAwsRegionName()))
                .credentialsProvider(CREDENTIALS_PROVIDER)
                .build();
        this.queueUrl = config.getQueueUrl();
        // pollFrequency says how often to poll and only incidentally sets the long-poll window,
        // so a longer one is clamped rather than refused.
        final long pollSeconds = config.getPollFrequency().getDuration().toSeconds();
        this.waitTimeSeconds = (int) Math.clamp(pollSeconds, 0, MAX_SQS_LONG_POLL_SECONDS);
        if (pollSeconds > MAX_SQS_LONG_POLL_SECONDS) {
            LOGGER.info("Poll frequency of {}s is longer than SQS's {}s long-poll maximum, so each "
                        + "receive waits {}s and the connector polls again after that",
                    pollSeconds, MAX_SQS_LONG_POLL_SECONDS, waitTimeSeconds);
        }
    }

    public void poll() {
        try {
            List<Message> messages;
            int batches = 0;
            do {
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.info(() -> "Stopping SQS poll early: the thread has been interrupted");
                    break;
                }
                if (batches++ >= BATCHES_PER_POLL) {
                    LOGGER.debug(() -> LogUtil.message(
                            "Reached the {}-batch limit for one poll of {}; the remainder is left on "
                            + "the queue for the next poll", BATCHES_PER_POLL, queueUrl));
                    break;
                }
                final ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(waitTimeSeconds)
                        .messageAttributeNames("All")
                        .build();
                messages = sqsClient.receiveMessage(receiveMessageRequest).messages();
                for (final Message message : messages) {
                    handle(message);
                }
            } while (!messages.isEmpty());
        } catch (final Exception e) {
            LOGGER.error(() -> "Error polling SQS queue " + queueUrl + ": " + e.getMessage(), e);
        }
    }

    private void handle(final Message message) {
        try {
            final AttributeMap attributeMap = attributeMapOf(message);
            if (!attributeMap.containsKey(StandardHeaderArguments.FEED)) {
                // Left on the queue, where an operator and any redrive policy can see it.
                throw new StroomStreamException(StroomStatusCode.FEED_MUST_BE_SPECIFIED, attributeMap,
                        "SQS message " + message.messageId() + " has no Feed attribute");
            }
            final UniqueId receiptId = receiptIdGenerator.generateId();
            attributeMap.put(StandardHeaderArguments.RECEIPT_ID, receiptId.toString());
            attributeMap.appendItem(StandardHeaderArguments.RECEIPT_ID_PATH, receiptId.toString());

            final boolean accepted = securityContext.asProcessingUserResult(() ->
                    eventStore.accept(attributeMap, receiptId, message.body()));
            if (!accepted) {
                LOGGER.info("SQS message {} dropped by the receipt policy for feed '{}'",
                        message.messageId(), attributeMap.get(StandardHeaderArguments.FEED));
            }
            sqsClient.deleteMessage(DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(message.receiptHandle())
                    .build());
        } catch (final StroomStreamException e) {
            // The answer a sender would have been given. The message stays on the queue.
            LOGGER.warn(() -> LogUtil.message("SQS message {} refused ({}) and left on queue {}: {}",
                    message.messageId(), e.getStroomStatusCode(), queueUrl, e.getMessage()));
        } catch (final RuntimeException e) {
            LOGGER.error(() -> LogUtil.message("SQS message {} could not be consumed and is left on queue {}: {}",
                    message.messageId(), queueUrl, e.getMessage()), e);
        }
    }

    private static AttributeMap attributeMapOf(final Message message) {
        final AttributeMap attributeMap = new AttributeMap();
        if (message.hasAttributes()) {
            attributeMap.putAll(message.attributesAsStrings());
        }
        if (message.hasMessageAttributes()) {
            final Map<String, MessageAttributeValue> messageAttributes = message.messageAttributes();
            messageAttributes.forEach((k, v) -> attributeMap.put(k, v.stringValue()));
        }
        final String sqsMessageId = message.messageId();
        if (NullSafe.isNonBlankString(sqsMessageId)) {
            attributeMap.put(StandardHeaderArguments.SQS_MESSAGE_ID, sqsMessageId);
        }
        return attributeMap;
    }

    @Override
    public void close() {
        sqsClient.close();
    }
}
