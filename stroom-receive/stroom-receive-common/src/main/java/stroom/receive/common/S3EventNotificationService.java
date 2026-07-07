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

package stroom.receive.common;


import stroom.aws.sqs.SqsClientFactory;
import stroom.aws.sqs.SqsConfig;
import stroom.data.store.api.S3Location;
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

import jakarta.inject.Inject;
import jakarta.inject.Provider;
import jakarta.inject.Singleton;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import tools.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;
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
public class S3EventNotificationService {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(S3EventNotificationService.class);
    private static final Pattern DOT_DELIMITER_PATTERN = Pattern.compile("\\.");
    private static final String EXPECTED_EVENT_MAJOR_VERSION = "2";
    private static final String SUPPORTED_EVENT_NAME_PREFIX = "ObjectCreated:";
    private static final String EVENT_FIELD = "Event";
    private static final String TEST_EVENT_VALUE = "s3:TestEvent";
    private static final String SERVICE_FIELD = "Service";
    private static final String TIME_FIELD = "Time";
    private static final String BUCKET_FIELD = "Bucket";
    private static final String RECORDS_FIELD = "Records";

    private final Provider<S3EventNotificationConfig> s3EventNotificationConfigProvider;
    private final SqsClientFactory sqsClientFactory;
    private final ReceiptIdGenerator receiptIdGenerator;
    private final S3EventConsumer s3EventConsumer;
    private final S3ObjectInspector s3ObjectInspector;
    private final CommonSecurityContext commonSecurityContext;

    private volatile S3EventNotificationConfig lastS3EventNotificationConfig = null;
    private volatile List<ClientState> sqsClients;

    @Inject
    public S3EventNotificationService(
            final Provider<S3EventNotificationConfig> s3EventNotificationConfigProvider,
            final SqsClientFactory sqsClientFactory,
            final ReceiptIdGenerator receiptIdGenerator,
            final S3EventConsumer s3EventConsumer,
            final S3ObjectInspector s3ObjectInspector,
            final SecurityContext commonSecurityContext) {
        this.s3EventNotificationConfigProvider = s3EventNotificationConfigProvider;
        this.sqsClientFactory = sqsClientFactory;
        this.receiptIdGenerator = receiptIdGenerator;
        this.s3EventConsumer = s3EventConsumer;
        this.s3ObjectInspector = s3ObjectInspector;
        this.commonSecurityContext = commonSecurityContext;
    }

    public void poll() {
        final List<ClientState> clients = getSqsClients();
        LOGGER.debug("poll() - clients: {}", clients);
        if (NullSafe.hasItems(clients)) {
            for (final ClientState clientState : clients) {
                poll(clientState);
                if (Thread.currentThread().isInterrupted()) {
                    LOGGER.info("Thread interrupted, breaking out of SQS clients loop");
                    break;
                }
            }
        }
    }

    /**
     * @param s3Location The location of the object on s3
     * @param metaData   Any additional metadata to override the meta obtained from the S3 object.
     */
    public void notify(final S3Location s3Location, final Map<String, String> metaData) {
        Objects.requireNonNull(s3Location);
        LOGGER.debug("notify() - s3Location: {}, metaData: {}", s3Location, metaData);
        commonSecurityContext.secure(AppPermission.STROOM_PROXY, () -> {
            commonSecurityContext.asProcessingUser(() -> {
                final AttributeMap attributeMap = new AttributeMap();
                addReceiptId(attributeMap);

                s3ObjectInspector.addS3MetaAttributes(s3Location, attributeMap);
                // Override the s3 metadata with any supplied meta.
                if (NullSafe.hasEntries(metaData)) {
                    attributeMap.putAll(metaData);
                }

                LOGGER.debug("notify() - s3Location: {}, attributeMap: {}", s3Location, attributeMap);
                s3EventConsumer.accept(new S3CreateEvent(s3Location, attributeMap));
            });
        });
    }

    private void poll(final ClientState clientState) {
        try {
//            if (queueUrl == null) {
//                LOGGER.debug(() -> "Getting queue name");
//                final String queueName = config.getQueueName();
//                LOGGER.debug(() -> "Getting queue URL for queue: " + queueName);
//                queueUrl = sqs.getQueueUrl(queueName).getQueueUrl();
//            }
            // TODO do we want the queueName or queueUrl in the config?
            //  We can use sqsClient to derive the latter from the former.
            final SqsConfig sqsConfig = clientState.config;
            final SqsClient sqsClient = clientState.sqsClient;
            final String queueUrl = sqsConfig.getQueueUrl();
            LOGGER.debug(() -> LogUtil.message("poll() - queueName, {}", queueUrl));

            List<Message> messages;
            do {
                // receive messages from the queue
                LOGGER.debug(() -> "Getting messages");
                // long polling and wait for waitTimeSeconds before timed out
                final ReceiveMessageRequest receiveMessageRequest = ReceiveMessageRequest.builder()
                        .queueUrl(queueUrl)
                        .waitTimeSeconds((int) sqsConfig.getPollFrequency().getDuration().toSeconds())
                        .messageAttributeNames("All") // Message attribute wildcard.
                        .build();

                messages = sqsClient.receiveMessage(receiveMessageRequest)
                        .messages();

                // delete messages from the queue
                for (final Message message : messages) {
                    try {
                        final AttributeMap attributeMap = new AttributeMap();
                        addReceiptId(attributeMap);
                        addSqsMessageId(attributeMap, message);

                        // TODO:
                        //  * It is agreed that stroom will not duplicate the data on S3, so will
                        //    just create the meta rec in the db along with s3 location to read from there.
                        //  * Don't need to worry about any auth filtering beyond authenticating to the SQS itself.
                        //  * Add in logging to receive log.
                        //  * S3 is read only from stroom's POV, so we need a isReadOnly method on the Store api.
                        final S3CreateEvent s3CreateEvent = convertMessage(message.body(), attributeMap);

                        // Consumer is responsible for doing attributeMap filtering as they need
                        // to deal with accept/drop/reject
                        s3EventConsumer.accept(s3CreateEvent);

                        // Now delete the msg we have consumed
                        final DeleteMessageRequest deleteMessageRequest = DeleteMessageRequest.builder()
                                .queueUrl(queueUrl)
                                .receiptHandle(message.receiptHandle())
                                .build();
                        sqsClient.deleteMessage(deleteMessageRequest);
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                    if (Thread.currentThread().isInterrupted()) {
                        LOGGER.info("Thread interrupted, stopping polling");
                        break;
                    }
                }
            } while (!messages.isEmpty());
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }

    private void handleEvent(final S3CreateEvent s3CreateEvent,
                             final AttributeMapFilter attributeMapFilter) {

        try {
            final boolean isAllowed;
            try {
                isAllowed = attributeMapFilter.filter(s3CreateEvent.attributeMap());
                LOGGER.debug("handleEvent() - s3CreateEvent: {}, isAllowed: {}", s3CreateEvent, isAllowed);
                if (isAllowed) {
                    s3EventConsumer.accept(s3CreateEvent);
                } else {
                    // TODO log the drop
                }
            } catch (final StroomStreamException e) {
                // TODO log the rejection
                LOGGER.debug("handleEvent() - s3CreateEvent: {}, stroomStreamException: {}",
                        s3CreateEvent, LogUtil.exceptionMessage(e));
            }
        } catch (final Exception e) {
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

                        attributeMap.put(StandardHeaderArguments.CONTENT_LENGTH, String.valueOf(objectSize));

                        // Print extracted values
                        LOGGER.debug("processMessage() - awsRegion: {}, eventTime: {}, eventName: {}, " +
                                     "bucketName: {}, bucketArn: {}, objectKey: {}, objectSize: {}, attributeMap: {}",
                                awsRegion, eventTime, eventName, bucketName,
                                bucketArn, objectKey, objectSize, attributeMap);

                        final S3Location s3Location = new S3Location(awsRegion, bucketName, objectKey);
                        s3ObjectInspector.addS3MetaAttributes(s3Location, attributeMap);
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
                    "Error parsing message body - {}, messageBody:\n{}", LogUtil.exceptionMessage(e), messageBody), e);
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

    private List<ClientState> getSqsClients() {
        // Intentionally use instance equality as the provider will return a different instance
        // if the config has changed.
        if (s3EventNotificationConfigProvider.get() != lastS3EventNotificationConfig) {
            synchronized (this) {
                final S3EventNotificationConfig newS3EventNotificationConfig = s3EventNotificationConfigProvider.get();
                if (newS3EventNotificationConfig != lastS3EventNotificationConfig) {
                    final List<ClientState> oldClients = sqsClients;
                    closeClients(oldClients);
                    sqsClients = NullSafe.stream(newS3EventNotificationConfig.getSqsConnectors())
                            .map(sqsConfig -> {
                                final SqsClient sqsClient = sqsClientFactory.createSqsClient(sqsConfig);
                                return new ClientState(sqsClient, sqsConfig);
                            })
                            .toList();
                    lastS3EventNotificationConfig = newS3EventNotificationConfig;
                    LOGGER.debug(() -> LogUtil.message("getClients() - sqsClients.size: {}", sqsClients.size()));
                }
            }
        }
        return sqsClients;
    }

    private void closeClients(final List<ClientState> clients) {
        for (final ClientState clientState : NullSafe.list(clients)) {
            try {
                clientState.sqsClient.close();
            } catch (final Exception e) {
                LOGGER.error(LogUtil.message("Error closing sqsClient {}, config: {},  - {}",
                        clientState.sqsClient, clientState.config, LogUtil.exceptionMessage(e)), e);
                throw new RuntimeException(e);
            }
        }
    }


    // --------------------------------------------------------------------------------


    private record ClientState(SqsClient sqsClient, SqsConfig config) {

        private ClientState {
            Objects.requireNonNull(sqsClient);
            Objects.requireNonNull(config);
        }
    }
}
