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

package stroom.proxy.app;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.event.EventStore;
import stroom.receive.common.ReceiptIdGenerator;
import stroom.util.concurrent.UniqueId;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
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

public class SqsConnector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqsConnector.class);

    private final EventStore eventStore;
    private final SqsClient sqsClient;
    private final ReceiptIdGenerator receiptIdGenerator;

    private final String queueUrl;
    private final int waitTimeSeconds;

    public SqsConnector(final EventStore eventStore,
                        final SqsConnectorConfig config,
                        final ReceiptIdGenerator receiptIdGenerator) {
        this.eventStore = eventStore;
        this.receiptIdGenerator = receiptIdGenerator;
        try {
            LOGGER.debug(() -> "Creating SQS client");
            sqsClient = SqsClient.builder()
                    .region(Region.of(config.getAwsRegionName()))
                    .credentialsProvider(DefaultCredentialsProvider.create())
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.error(e::getMessage, e);
            throw e;
        }
        queueUrl = config.getQueueUrl();
        waitTimeSeconds = (int) config.getPollFrequency().getDuration().toSeconds();
    }

    public void poll() {
        try {
//            if (queueUrl == null) {
//                LOGGER.debug(() -> "Getting queue name");
//                final String queueName = config.getQueueName();
//                LOGGER.debug(() -> "Getting queue URL for queue: " + queueName);
//                queueUrl = sqs.getQueueUrl(queueName).getQueueUrl();
//            }
            LOGGER.debug(() -> "Got queue URL: " + queueUrl);

            List<Message> messages;
            do {
                // receive messages from the queue
                LOGGER.debug(() -> "Getting messages");
                // long polling and wait for waitTimeSeconds before timed out
                final ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .waitTimeSeconds(waitTimeSeconds)  // forces long polling
                        .messageAttributeNames("All") // Message attribute wildcard.
                        .build();

                messages = sqsClient.receiveMessage(receiveMessageRequest).messages();

                // delete messages from the queue
                for (final Message message : messages) {
                    try {
                        final AttributeMap attributeMap = new AttributeMap();

                        LOGGER.debug(() -> "Has Attributes: " + message.hasAttributes());
                        if (message.hasAttributes()) {
                            final Map<String, String> attributesAsStrings = message.attributesAsStrings();
                            LOGGER.debug(() -> "Attributes: " + attributesAsStrings);
                            attributeMap.putAll(attributesAsStrings);
                        }

                        LOGGER.debug(() -> "Has Message Attributes: " + message.hasMessageAttributes());
                        if (message.hasMessageAttributes()) {
                            final Map<String, MessageAttributeValue> messageAttributes = message.messageAttributes();
                            LOGGER.debug(() -> "Message Attributes: " + messageAttributes);
                            messageAttributes.forEach((k, v) -> attributeMap.put(k, v.stringValue()));
                        }

                        // FALLBACK
                        if (!attributeMap.containsKey(StandardHeaderArguments.FEED)) {
                            LOGGER.debug(() -> "Adding fallback feed TEST");
                            attributeMap.putIfAbsent(StandardHeaderArguments.FEED, "TEST");
                        }

                        final String sqsMessageId = message.messageId();
                        if (NullSafe.isNonBlankString(sqsMessageId)) {
                            LOGGER.debug("sqsMessageId: {}", sqsMessageId);
                            attributeMap.put(StandardHeaderArguments.SQS_MESSAGE_ID, sqsMessageId);
                        }
                        final UniqueId receiptId = receiptIdGenerator.generateId();
                        final String receiptIdStr = receiptId.toString();
                        LOGGER.debug("Adding proxy attribute {}: {}", StandardHeaderArguments.RECEIPT_ID, receiptIdStr);
                        attributeMap.put(StandardHeaderArguments.RECEIPT_ID, receiptIdStr);
                        attributeMap.appendItem(StandardHeaderArguments.RECEIPT_ID_PATH, receiptIdStr);

                        eventStore.consume(attributeMap, receiptId, message.body());

                        final DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build();
                        sqsClient.deleteMessage(deleteMessageRequest);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
            } while (!messages.isEmpty());
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
