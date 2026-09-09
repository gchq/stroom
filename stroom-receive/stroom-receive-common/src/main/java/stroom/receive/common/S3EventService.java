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

package stroom.receive.common;


import stroom.aws.s3.client.S3ClientHelper;
import stroom.aws.s3.client.S3ClientHelper.S3ObjectInfo;
import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaKeysMapper;
import stroom.aws.s3.shared.S3ClientConfig;
import stroom.aws.s3.shared.S3ClientConfigService;
import stroom.aws.s3.shared.S3Location;
import stroom.aws.sqs.SqsClientFactory;
import stroom.aws.sqs.SqsConfig;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.security.api.CommonSecurityContext;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.concurrent.UniqueId;
import stroom.util.date.DateUtil;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.shared.string.CIKey;

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.MessageAttributeValue;
import software.amazon.awssdk.services.sqs.model.MessageSystemAttributeName;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;
import software.amazon.awssdk.services.sqs.model.SendMessageRequest;
import software.amazon.awssdk.services.sqs.model.SendMessageResponse;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.regex.Pattern;

/**
 * <p>
 * Service for consuming S3 create events.
 * Files that have been created on S3 will be associated with a new meta record so they
 * can be consumed in-place. The files are not copied from their S3 location.
 * </p>
 * <p>
 * See
 * <a href="https://docs.aws.amazon.com/AmazonS3/latest/userguide/notification-content-structure.html">
 * AWS notification content structure
 * </a> for details.
 * </p>
 */
@Singleton
public class S3EventService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3EventService.class);
    private static final Pattern DOT_DELIMITER_PATTERN = Pattern.compile("\\.");
    private static final String EXPECTED_EVENT_MAJOR_VERSION = "2";
    private static final String SUPPORTED_EVENT_NAME_PREFIX = "ObjectCreated:";
    private static final String EVENT_FIELD = "Event";
    private static final String TEST_EVENT_VALUE = "s3:TestEvent";
    private static final String SERVICE_FIELD = "Service";
    private static final String TIME_FIELD = "Time";
    private static final String BUCKET_FIELD = "Bucket";
    private static final String RECORDS_FIELD = "Records";

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider;
    private final SqsClientFactory sqsClientFactory;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final S3EventConsumer s3EventConsumer;
    private final S3ClientPool s3ClientPool;
    private final S3ClientConfigService s3ClientConfigService;
    private final S3MetaKeysMapper s3MetaKeysMapper;
    private final CommonSecurityContext commonSecurityContext;
    private final AtomicBoolean shuttingDown = new AtomicBoolean(false);

    //    CachedValue<SqsClient, SqsConfig> cachedSqsClient;
    private volatile S3EventConfig lastS3EventConfig = null;
    private volatile ClientState sqsClientState;

    @Inject
    public S3EventService(
            final Provider<ReceiveDataConfig> receiveDataConfigProvider,
            final SqsClientFactory sqsClientFactory,
            final ReceiptIdGenerator receiptIdGenerator,
            final S3EventConsumer s3EventConsumer,
            final S3ClientPool s3ClientPool,
            final S3ClientConfigService s3ClientConfigService,
            final S3MetaKeysMapper s3MetaKeysMapper,
            final SecurityContext commonSecurityContext) {
        this.receiveDataConfigProvider = receiveDataConfigProvider;
        this.sqsClientFactory = sqsClientFactory;
        this.receiptIdGenerator = receiptIdGenerator;
        this.s3EventConsumer = s3EventConsumer;
        this.s3ClientPool = s3ClientPool;
        this.s3ClientConfigService = s3ClientConfigService;
        this.s3MetaKeysMapper = s3MetaKeysMapper;
        this.commonSecurityContext = commonSecurityContext;
    }

//    public void start() {
//    }
//
//    public void stop() {
//        shuttingDown.set(true);
//    }

    /**
     * This is the main entry point for REST based S3 event notifications.
     *
     * @param s3Location The location of the object on s3
     * @param metaData   Any additional metadata to override the meta obtained from the S3 object.
     */
    public void handleEvent(final S3Location s3Location, final String eTag, final Map<String, String> metaData) {
        Objects.requireNonNull(s3Location);
        LOGGER.debug("notify() - s3Location: {}, metaData: {}", s3Location, metaData);
        commonSecurityContext.secure(AppPermission.STROOM_PROXY, () -> {
            commonSecurityContext.asProcessingUser(() -> {
                doNotify(s3Location, eTag, metaData);
            });
        });
    }

    private void doNotify(final S3Location s3Location, final String eTag, final Map<String, String> metaData) {
        final AttributeMap attributeMap = new AttributeMap();
        addReceiptId(attributeMap);

        addS3MetaAttributes(s3Location, eTag, attributeMap);
        // Override the s3 metadata with any supplied meta.
        if (NullSafe.hasEntries(metaData)) {
            attributeMap.putAll(metaData);
        }

        LOGGER.debug("notify() - s3Location: {}, attributeMap: {}", s3Location, attributeMap);
        s3EventConsumer.accept(new S3CreateEvent(s3Location, attributeMap));
    }

    public void addS3MetaAttributes(final S3Location s3Location,
                                    final String eTag,
                                    final AttributeMap attributeMap) {
        LOGGER.debug("addS3MetaAttributes() - s3Location: {}, attributeMap: {}",
                s3Location, attributeMap);
        Objects.requireNonNull(s3Location);
        Objects.requireNonNull(attributeMap);

        final Optional<S3ClientConfig> optS3ClientConfig = s3ClientConfigService.getS3ClientConfig(
                s3Location.getRegionName(),
                s3Location.getBucketName());

        optS3ClientConfig.ifPresentOrElse(
                s3ClientConfig -> {
                    final S3ClientHelper s3ClientHelper = new S3ClientHelper(s3ClientConfig, s3ClientPool);
                    final S3ObjectInfo objectInfo = s3ClientHelper.getObjectInfo(
                            s3Location.getRegionName(),
                            s3Location.getBucketName(),
                            s3Location.getKey(),
                            eTag);

                    // Map any known keys back to their original form as some of our keys may not fit the
                    // key restrictions.
                    objectInfo.s3Metadata().forEach((k, v) -> {
                        final CIKey originalKey = s3MetaKeysMapper.getOriginalKey(k)
                                .orElse(k);
                        attributeMap.put(originalKey.get(), v);
                    });
                    LOGGER.debug("addS3MetaAttributes() - s3Location: {}, modified attributeMap: {}",
                            s3Location, attributeMap);
                },
                () -> LOGGER.warn("No S3 client config found matching region '{}' and bucket '{}'. " +
                                  "Unable to fetch S3 metadata for key '{}'",
                        s3Location.getRegionName(), s3Location.getBucketName(), s3Location.getKey()));
    }

    /// Do a single poll for a batch of messages from the SQS queue.
    /// Errors are logged. Rejected/failed messages are sent to the dead letter queue.
    ///
    /// @param terminationChecker This is used to check if processing should be cleanly terminated mid-batch.
    public void poll(final TerminationChecker terminationChecker) {
        try {
            final ClientState clientState = getSqsClient();
            if (clientState != null) {
                final S3EventConfig s3EventConfig = clientState.config;
                final SqsConfig sqsConfig = s3EventConfig.getSqs();
                final SqsClient sqsClient = clientState.sqsClient;
                final String queueUrl = sqsConfig.getQueueUrl();
                final String deadLetterQueueUrl = sqsConfig.getDeadLetterQueueUrl();
                LOGGER.debug(() -> LogUtil.message("poll() - queueUrl: {}, deadLetterQueueUrl: {}",
                        queueUrl, deadLetterQueueUrl));

                final List<Message> messages;
                // This allows polling to eventually stop if the job is disabled.
                try {
                    // receive messages from the queue
                    LOGGER.debug("Getting messages from queue: {}", queueUrl);
                    // long polling and wait for waitTimeSeconds before timed out
                    final ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                            .queueUrl(queueUrl)
                            .waitTimeSeconds((int) sqsConfig.getPollMaxWaitTime().toSeconds())
                            .messageAttributeNames("All") // Message attribute wildcard.
                            .visibilityTimeout((int) sqsConfig.getVisibilityTimeout().toSeconds())
                            .maxNumberOfMessages(sqsConfig.getMaxNumberOfMessages())
                            .messageSystemAttributeNames(
                                    MessageSystemAttributeName.SENT_TIMESTAMP,
                                    MessageSystemAttributeName.SENDER_ID)
                            .build();

                    final ReceiveMessageResponse receiveMessageResponse = sqsClient.receiveMessage(
                            receiveMessageRequest);
                    messages = receiveMessageResponse.messages();
                    if (LOGGER.isDebugEnabled()) {
                        LOGGER.debug("poll() - messages: {}, queueUrl: {}", messages.size(), queueUrl);
                    }

                    // delete messages from the queue
                    for (final Message message : messages) {
                        handleSqsMessage(message, queueUrl, sqsClient, deadLetterQueueUrl);
                        // Any messages already handled will have been deleted from the queue.
                        // The rest will eventually become visible on the queue again, so available
                        // for polling when we restart.
                        if (NullSafe.test(terminationChecker, TerminationChecker::isTerminated)) {
                            break;
                        }
                    }
                } catch (final Throwable e) {
                    LOGGER.error("Error polling SQS queue {}. Swallowing error - {}",
                            queueUrl, LogUtil.exceptionMessage(e), e);
                }
            } else {
                LOGGER.warn("SQS has not been configured. Nothing to do.");
            }
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void handleSqsMessage(final Message message,
                                  final String queueUrl,
                                  final SqsClient sqsClient,
                                  final String deadLetterQueueUrl) {
        try {
            final AttributeMap attributeMap = new AttributeMap();
            addReceiptId(attributeMap);
            addSqsMessageId(attributeMap, message);

            final S3CreateEvent s3CreateEvent = convertMessage(message.body(), attributeMap);

            // Consumer is responsible for doing attributeMap filtering as they need
            // to deal with accept/drop/reject
            s3EventConsumer.accept(s3CreateEvent);

            // RECEIVE or DROP so delete the message
            deleteSqsMessage(message, queueUrl, sqsClient);
        } catch (final RuntimeException e) {
            handleMessageFailure(message, queueUrl, sqsClient, deadLetterQueueUrl, e);
        }
    }

    private static void handleMessageFailure(final Message message,
                                             final String queueUrl,
                                             final SqsClient sqsClient,
                                             final String deadLetterQueueUrl,
                                             final RuntimeException e) {
        if (NullSafe.isNonBlankString(deadLetterQueueUrl)) {
            LOGGER.error(LogUtil.message("Error processing message - queueUrl: {}, " +
                                         "moving it to deadLetterQueueUrl: {} - {}",
                    queueUrl, deadLetterQueueUrl, LogUtil.exceptionMessage(e)), e);
            try {
                // Manually 'move' the message to the dead letter queue
                reSendSqsMessage(message, deadLetterQueueUrl, sqsClient);
                deleteSqsMessage(message, queueUrl, sqsClient);
            } catch (final Exception ex) {
                // If something goes wrong, the message will remain in the main queue and eventually
                // become visible again, so will get re-received under the redrive policy.
                LOGGER.error(LogUtil.message(
                        "Error moving message to dead letter queue - deadLetterQueueUrl: {} - {}",
                        deadLetterQueueUrl, LogUtil.exceptionMessage(e)), e);
            }
        } else {
            LOGGER.error(LogUtil.message("Error processing message - queueUrl: {} - {}",
                    queueUrl, LogUtil.exceptionMessage(e)), e);
        }
    }

    private static void reSendSqsMessage(final Message message,
                                         final String queueUrl,
                                         final SqsClient sqsClient) {
        try {
            // Capture the original message attributes as they will get lost on re-send
            final Map<String, MessageAttributeValue> messageAttributes = message.messageAttributes();
            final String originalMessageId = message.messageId();
            messageAttributes.put("OriginalMessageId", MessageAttributeValue.builder()
                    .stringValue(originalMessageId)
                    .build());
            if (NullSafe.hasEntries(messageAttributes)) {
                final MessageAttributeValue originalSentTime = messageAttributes.get(
                        MessageSystemAttributeName.SENT_TIMESTAMP.toString());
                final MessageAttributeValue originalSenderId = messageAttributes.get(
                        MessageSystemAttributeName.SENDER_ID.toString());
                messageAttributes.put("Original" + MessageSystemAttributeName.SENT_TIMESTAMP, originalSentTime);
                messageAttributes.put("Original" + MessageSystemAttributeName.SENDER_ID, originalSenderId);
            }

            final SendMessageRequest sendMessageRequest = SendMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .messageBody(message.body())
                    .messageAttributes(messageAttributes)
                    .build();
            final SendMessageResponse sendMessageResponse = sqsClient.sendMessage(sendMessageRequest);
            LOGGER.debug("reSendSqsMessage() - Success - message: {}, sendMessageResponse: {}, queueUrl: {}",
                    message, sendMessageResponse, queueUrl);
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Error sending message to SQS - queueUrl: {} - {}",
                    queueUrl, LogUtil.exceptionMessage(e)), e);
        }
    }

    private static void deleteSqsMessage(final Message message,
                                         final String queueUrl,
                                         final SqsClient sqsClient) {
        final String receiptHandle = message.receiptHandle();
        try {
            final DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                    .queueUrl(queueUrl)
                    .receiptHandle(receiptHandle)
                    .build();
            sqsClient.deleteMessage(deleteMessageRequest);
        } catch (final RuntimeException e) {
            LOGGER.error("Error deleting sqs messageId: {}, receiptHandle: {}, queueUrl: {}",
                    message.messageId(), receiptHandle, queueUrl, e);
            throw new RuntimeException(e);
        }
    }

    private static void addSqsMessageId(final AttributeMap attributeMap, final Message message) {
        final String sqsMessageId = message.messageId();
        if (NullSafe.isNonBlankString(sqsMessageId)) {
            LOGGER.debug("sqsMessageId: {}", sqsMessageId);
            attributeMap.put(StandardHeaderArguments.SQS_MESSAGE_ID, sqsMessageId);
        }
    }

    private void addReceiptId(final AttributeMap attributeMap) {
        final UniqueId receiptId = receiptIdGenerator.generateId();
        final String receiptIdStr = receiptId.toString();
        LOGGER.debug("Adding proxy attribute {}: {}", StandardHeaderArguments.RECEIPT_ID, receiptIdStr);
        attributeMap.put(StandardHeaderArguments.RECEIPT_ID, receiptIdStr);
        attributeMap.appendItem(StandardHeaderArguments.RECEIPT_ID_PATH, receiptIdStr);
    }

    private static void addEventTime(final AttributeMap attributeMap, final String eventTime) {
        try {
            // Parse it to long first so that we know the format is good, then
            // put it using our standard format
            final Instant time = DateUtil.parseNormalDateTimeStringToInstant(eventTime);
            attributeMap.putDateTime(StandardHeaderArguments.RECEIVED_TIME, time);
            attributeMap.appendDateTime(StandardHeaderArguments.RECEIVED_TIME_HISTORY, time);
        } catch (final RuntimeException e) {
            throw new RuntimeException(LogUtil.message("Unable to parse event time {} - {}",
                    eventTime, LogUtil.exceptionMessage(e)), e);
        }
    }

    private S3CreateEvent convertMessage(final String messageBody, final AttributeMap attributeMap) {
        S3CreateEvent s3CreateEvent = null;
        try {
            final JsonNode rootNode = JsonUtil.getMapper().readTree(messageBody);

            // SQS consumers must support a test message that SQS will probably add to the queue
            // Swallow and log the S3 Test Message
            if (TEST_EVENT_VALUE.equals(JsonUtil.getNodeAsString(rootNode, EVENT_FIELD))) {
                LOGGER.debug(() -> LogUtil.message("""
                                processMessage() - Test Message Detected:
                                  Service: {}
                                  Event: {}
                                  Time: {}
                                  Bucket: {}""",
                        JsonUtil.getNodeAsString(rootNode, SERVICE_FIELD),
                        JsonUtil.getNodeAsString(rootNode, EVENT_FIELD),
                        JsonUtil.getNodeAsString(rootNode, TIME_FIELD),
                        JsonUtil.getNodeAsString(rootNode, BUCKET_FIELD)));
            } else if (rootNode.has(RECORDS_FIELD)) {
                // The proper message body
                final JsonNode recordsNode = rootNode.get(RECORDS_FIELD);

                for (final JsonNode recordsItemNode : recordsNode) {
                    LOGGER.debug("processMessage() - Processing record");

                    validateEventVersion(JsonUtil.getNodeAsString(recordsItemNode, "eventVersion"));

                    final String eventName = getNodeAsString(recordsItemNode, "eventName");
                    if (eventName != null && eventName.startsWith(SUPPORTED_EVENT_NAME_PREFIX)) {
                        final String awsRegion = getNodeAsString(recordsItemNode, "awsRegion");
                        final String eventTime = getNodeAsString(recordsItemNode, "eventTime");

                        addEventTime(attributeMap, eventTime);

                        final JsonNode s3Node = getNode(recordsItemNode, "s3");

                        final JsonNode s3BucketNode = getNode(s3Node, "bucket");
                        final String bucketName = getNodeAsString(s3BucketNode, "name");
                        final String bucketArn = getNodeAsString(s3BucketNode, "arn");

                        final JsonNode objectNode = getNode(s3Node, "object");
                        final String objectKey = getNodeAsString(objectNode, "key");
                        final long objectSize = getNodeAsLong(objectNode, "size");
                        final String eTag = getNodeAsString(objectNode, "eTag");

                        attributeMap.put(StandardHeaderArguments.CONTENT_LENGTH, String.valueOf(objectSize));

                        // Print extracted values
                        LOGGER.debug("processMessage() - awsRegion: {}, eventTime: {}, eventName: {}, " +
                                     "bucketName: {}, bucketArn: {}, objectKey: {}, objectSize: {}, attributeMap: {}",
                                awsRegion, eventTime, eventName, bucketName,
                                bucketArn, objectKey, objectSize, attributeMap);

                        final S3Location s3Location = new S3Location(awsRegion, bucketName, objectKey);
                        addS3MetaAttributes(s3Location, eTag, attributeMap);
//                        addS3MetaAttributes(s3Location, attributeMap);
                        s3CreateEvent = new S3CreateEvent(s3Location, attributeMap);
                    } else {
                        LOGGER.debug("processMessage() - Ignoring eventName: {}\n{}", eventName, messageBody);
                    }
                }
            } else {
                throw new IllegalStateException("Unknown messageBody format, messageBody:\n" + messageBody);
            }
        } catch (final Exception e) {
            throw new IllegalStateException(LogUtil.message(
                    "Error parsing SQS message body - {}, messageBody:\n{}",
                    LogUtil.exceptionMessage(e), messageBody), e);
        }
        return s3CreateEvent;
    }

//    /**
//     * Call out to S3 to get the objects metadata.
//     */
//    private void addS3MetaAttributes(final S3Location s3Location,
//                                     final AttributeMap attributeMap) {
//        LOGGER.debug("addS3MetaAttributes() - s3Location: {}, attributeMap: {}", s3Location, attributeMap);
//        final Optional<FsVolume> optS3Volume = s3VolumeService.getS3Volume(
//                s3Location.regionName(),
//                s3Location.bucketName());
//
//        optS3Volume.ifPresentOrElse(
//                s3Volume -> {
//                    final S3ClientConfig s3ClientConfig = s3Volume.getS3ClientConfig();
//                    final S3ClientHelper s3ClientHelper = new S3ClientHelper(s3ClientConfig, s3ClientPool);
//                    final S3ObjectInfo objectInfo = s3ClientHelper.getObjectInfo(
//                            s3Location.bucketName(),
//                            s3Location.key());
//
//                    // Map any known keys back to their original form as some of our keys may not fit the
//                    // key restrictions.
//                    objectInfo.s3Metadata().forEach((k, v) -> {
//                        final CIKey originalKey = s3MetaFieldsMapper.getOriginalKey(k)
//                                .orElse(k);
//                        attributeMap.put(originalKey.get(), v);
//                    });
//                    LOGGER.debug("addS3MetaAttributes() - s3Location: {}, modified attributeMap: {}",
//                            s3Location, attributeMap);
//                },
//                () -> LOGGER.warn("No S3 volume found matching region '{}' and bucket '{}'. " +
//                                  "Unable to fetch S3 metadata for key '{}'",
//                        s3Location.regionName(), s3Location.bucketName(), s3Location.key()));
//    }

    private static String getNodeAsString(final JsonNode baseNode, final String fieldName) {
        final String val = JsonUtil.getNodeAsString(baseNode, fieldName);
        Objects.requireNonNull(val, () -> LogUtil.message("Field '{}' does not exist or has null value on node {}.",
                fieldName, baseNode));
        return val;
    }

    private static long getNodeAsLong(final JsonNode baseNode, final String fieldName) {
        final Long val = JsonUtil.getNodeAsLong(baseNode, fieldName);
        Objects.requireNonNull(val, () -> LogUtil.message("Field '{}' does not exist or has null value on node {}.",
                fieldName, baseNode));
        return val;
    }

    private static JsonNode getNode(final JsonNode baseNode, final String fieldName) {
        Objects.requireNonNull(baseNode, "baseNode must not be null");
        final JsonNode childNode = baseNode.get(fieldName);
        Objects.requireNonNull(childNode, () -> LogUtil.message("Field '{}' does not exist on {}.",
                fieldName, baseNode));
        return childNode;
    }

    private static void validateEventVersion(final String eventVersion) {
        Objects.requireNonNull(eventVersion, "eventVersion must not be null");
        final String[] parts = DOT_DELIMITER_PATTERN.split(eventVersion.trim());
        if (parts.length != 2) {
            throw new RuntimeException("Unexpected eventVersion value: " + eventVersion);
        } else {
            final String majorPart = parts[0];
            if (!EXPECTED_EVENT_MAJOR_VERSION.equals(majorPart)) {
                throw new RuntimeException(LogUtil.message("Unexpected major part ({}) in eventVersion {}",
                        majorPart, eventVersion));
            }
        }
    }

    private ClientState getSqsClient() {
        // Intentionally use instance equality as the object is large, and the provider will
        // return a different instance if the config has changed.
        if (receiveDataConfigProvider.get().getS3Event() != lastS3EventConfig) {
            synchronized (this) {
                final S3EventConfig newS3EventConfig = receiveDataConfigProvider.get().getS3Event();
                if (newS3EventConfig != null && newS3EventConfig.getSqs() != null) {
                    if (newS3EventConfig != lastS3EventConfig) {
                        final ClientState oldClientState = sqsClientState;
                        closeClient(oldClientState);
                        final SqsClient sqsClient = sqsClientFactory.createSqsClient(newS3EventConfig.getSqs());
                        sqsClientState = new ClientState(sqsClient, newS3EventConfig);
                        lastS3EventConfig = newS3EventConfig;
                        LOGGER.debug(() -> LogUtil.message("getClients() - sqsClientState: {}", sqsClientState));
                    }
                } else {
                    sqsClientState = null;
                    lastS3EventConfig = null;
                }
            }
        }
        return sqsClientState;
    }

    private void closeClient(final ClientState clientState) {
        try {
            clientState.sqsClient.close();
        } catch (final Exception e) {
            LOGGER.error(LogUtil.message("Error closing sqsClient {}, config: {},  - {}",
                    clientState.sqsClient, clientState.config, LogUtil.exceptionMessage(e)), e);
            throw new RuntimeException(e);
        }
    }


    // --------------------------------------------------------------------------------


    private record ClientState(SqsClient sqsClient, S3EventConfig config) {

        private ClientState {
            Objects.requireNonNull(sqsClient);
            Objects.requireNonNull(config);
        }
    }


    // --------------------------------------------------------------------------------


    @FunctionalInterface
    public interface TerminationChecker {

        boolean isTerminated();
    }
}
