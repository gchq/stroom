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

import stroom.aws.s3.client.S3ClientPool;
import stroom.aws.s3.client.S3MetaKeysMapper;
import stroom.aws.s3.shared.S3ClientConfigService;
import stroom.aws.s3.shared.S3Location;
import stroom.aws.sqs.SqsClientFactory;
import stroom.aws.sqs.SqsConfig;
import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.util.concurrent.UniqueId;
import stroom.util.time.StroomDuration;

import jakarta.inject.Provider;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import software.amazon.awssdk.services.sqs.SqsClient;
import software.amazon.awssdk.services.sqs.model.DeleteMessageRequest;
import software.amazon.awssdk.services.sqs.model.Message;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageRequest;
import software.amazon.awssdk.services.sqs.model.ReceiveMessageResponse;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class TestS3EventService {

    private final Provider<ReceiveDataConfig> receiveDataConfigProvider = mock(Provider.class);
    private final SqsClientFactory sqsClientFactory = mock(SqsClientFactory.class);
    private final ReceiptIdGenerator receiptIdGenerator = mock(ReceiptIdGenerator.class);
    private final S3EventConsumer s3EventConsumer = mock(S3EventConsumer.class);
    private final S3ClientPool s3ClientPool = mock(S3ClientPool.class);
    private final S3ClientConfigService s3ClientConfigService = mock(S3ClientConfigService.class);
    private final S3MetaKeysMapper s3MetaKeysMapper = mock(S3MetaKeysMapper.class);
    private final SecurityContext commonSecurityContext = mock(SecurityContext.class);

    @Test
    void testHandleEventSecuresAndConsumesEvent() throws Exception {
        final S3EventService service = createService();
        final S3Location s3Location = new S3Location("eu-west-2", "bucket", "path/file.txt");
        final AtomicReference<S3CreateEvent> consumedEvent = new AtomicReference<>();
        final Map<String, String> metadata = Map.of("Custom", "value");

        when(receiptIdGenerator.generateId()).thenReturn(
                new UniqueId(0, 0, UniqueId.NodeType.PROXY, "TEST"));
        when(s3ClientConfigService.getS3ClientConfig("eu-west-2", "bucket"))
                .thenReturn(Optional.empty());
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(1)).run();
            return null;
        }).when(commonSecurityContext).secure(eq(AppPermission.STROOM_PROXY), any(Runnable.class));
        doAnswer(invocation -> {
            ((Runnable) invocation.getArgument(0)).run();
            return null;
        }).when(commonSecurityContext).asProcessingUser(any(Runnable.class));
        doAnswer(invocation -> {
            consumedEvent.set(invocation.getArgument(0));
            return null;
        }).when(s3EventConsumer).accept(any());

        service.handleEvent(s3Location, "etag", metadata);

        assertThat(consumedEvent.get()).isNotNull();
        assertThat(consumedEvent.get().s3Location()).isEqualTo(s3Location);
        assertThat(consumedEvent.get().attributeMap()).containsEntry("Custom", "value");
        assertThat(consumedEvent.get().attributeMap())
                .containsKey(StandardHeaderArguments.RECEIPT_ID);
        verify(commonSecurityContext).secure(eq(AppPermission.STROOM_PROXY), any(Runnable.class));
    }

    @Test
    void testAddS3MetaAttributesDoesNothingWithoutClientConfig() {
        final S3EventService service = createService();
        final S3Location s3Location = new S3Location("eu-west-2", "bucket", "path/file.txt");
        final AttributeMap attributeMap = new AttributeMap();

        when(s3ClientConfigService.getS3ClientConfig("eu-west-2", "bucket"))
                .thenReturn(Optional.empty());

        service.addS3MetaAttributes(s3Location, "etag", attributeMap);

        assertThat(attributeMap).isEmpty();
    }

    @Test
    void testPollBuildsReceiveRequestAndHandlesEmptyResponse() {
        final S3EventConfig s3EventConfig = mock(S3EventConfig.class);
        final SqsConfig sqsConfig = mock(SqsConfig.class);
        final ReceiveDataConfig receiveDataConfig = mock(ReceiveDataConfig.class);
        final SqsClient sqsClient = mock(SqsClient.class);
        final S3EventService service = createService();

        when(receiveDataConfigProvider.get()).thenReturn(receiveDataConfig);
        when(receiveDataConfig.getS3Event()).thenReturn(s3EventConfig);
        when(s3EventConfig.getSqs()).thenReturn(sqsConfig);
        when(s3EventConfig.getSleepDurationPostFailure()).thenReturn(StroomDuration.ZERO);
        when(sqsConfig.getAwsRegionName()).thenReturn("eu-west-2");
        when(sqsConfig.getQueueUrl()).thenReturn("https://sqs.example/queue");
        when(sqsConfig.getDeadLetterQueueUrl()).thenReturn("https://sqs.example/dead-letter");
        when(sqsConfig.getPollMaxWaitTime()).thenReturn(StroomDuration.ofSeconds(10));
        when(sqsConfig.getVisibilityTimeout()).thenReturn(StroomDuration.ofMinutes(3));
        when(sqsConfig.getMaxNumberOfMessages()).thenReturn(10);
        when(sqsClientFactory.createSqsClient(sqsConfig)).thenReturn(sqsClient);
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class)))
                .thenReturn(ReceiveMessageResponse.builder().messages(List.of()).build());

        service.poll(() -> false);

        final ArgumentCaptor<ReceiveMessageRequest> requestCaptor = ArgumentCaptor.forClass(
                ReceiveMessageRequest.class);
        verify(sqsClient).receiveMessage(requestCaptor.capture());
        assertThat(requestCaptor.getValue().queueUrl()).isEqualTo("https://sqs.example/queue");
        assertThat(requestCaptor.getValue().waitTimeSeconds()).isEqualTo(10);
        assertThat(requestCaptor.getValue().visibilityTimeout()).isEqualTo(180);
        assertThat(requestCaptor.getValue().maxNumberOfMessages()).isEqualTo(10);
        verify(s3EventConsumer, org.mockito.Mockito.never()).accept(any());
    }

    @Test
    void testPollParsesS3EventMessage() {
        final S3EventConfig s3EventConfig = mock(S3EventConfig.class);
        final SqsConfig sqsConfig = mock(SqsConfig.class);
        final ReceiveDataConfig receiveDataConfig = mock(ReceiveDataConfig.class);
        final SqsClient sqsClient = mock(SqsClient.class);
        final S3EventService service = createService();
        final AtomicReference<S3CreateEvent> consumedEvent = new AtomicReference<>();
        final String messageBody = """
                {
                  "Records": [
                    {
                      "eventVersion": "2.6",
                      "eventSource": "aws:s3",
                      "awsRegion": "us-west-2",
                      "eventTime": "2025-01-02T03:04:05.000Z",
                      "eventName": "ObjectCreated:Put",
                      "userIdentity": {
                        "principalId": "principal-id"
                      },
                      "requestParameters": {
                        "sourceIPAddress": "192.0.2.1"
                      },
                      "responseElements": {
                        "x-amz-request-id": "request-id",
                        "x-amz-id-2": "host-id"
                      },
                      "s3": {
                        "s3SchemaVersion": "1.0",
                        "configurationId": "configuration-id",
                        "bucket": {
                          "name": "example-bucket",
                          "ownerIdentity": {
                            "principalId": "owner-id"
                          },
                          "arn": "arn:aws:s3:::example-bucket"
                        },
                        "object": {
                          "key": "path/object.txt",
                          "size": 123,
                          "eTag": "etag"
                        }
                      }
                    }
                  ]
                }
                """;

        when(receiveDataConfigProvider.get()).thenReturn(receiveDataConfig);
        when(receiveDataConfig.getS3Event()).thenReturn(s3EventConfig);
        when(s3EventConfig.getSqs()).thenReturn(sqsConfig);
        when(sqsConfig.getAwsRegionName()).thenReturn("eu-west-2");
        when(sqsConfig.getQueueUrl()).thenReturn("https://sqs.example/queue");
        when(sqsConfig.getDeadLetterQueueUrl()).thenReturn("");
        when(sqsConfig.getPollMaxWaitTime()).thenReturn(StroomDuration.ofSeconds(10));
        when(sqsConfig.getVisibilityTimeout()).thenReturn(StroomDuration.ofMinutes(3));
        when(sqsConfig.getMaxNumberOfMessages()).thenReturn(10);
        when(sqsClientFactory.createSqsClient(sqsConfig)).thenReturn(sqsClient);
        when(s3ClientConfigService.getS3ClientConfig("us-west-2", "example-bucket"))
                .thenReturn(Optional.empty());
        when(receiptIdGenerator.generateId()).thenReturn(
                new UniqueId(0, 0, UniqueId.NodeType.PROXY, "TEST"));
        when(sqsClient.receiveMessage(any(ReceiveMessageRequest.class))).thenReturn(
                ReceiveMessageResponse.builder()
                        .messages(Message.builder()
                                .messageId("message-id")
                                .receiptHandle("receipt-handle")
                                .body(messageBody)
                                .build())
                        .build());
        doAnswer(invocation -> {
            consumedEvent.set(invocation.getArgument(0));
            return null;
        }).when(s3EventConsumer).accept(any());

        service.poll(() -> false);

        assertThat(consumedEvent.get()).isNotNull();
        assertThat(consumedEvent.get().s3Location())
                .isEqualTo(new S3Location("us-west-2", "example-bucket", "path/object.txt"));
        assertThat(consumedEvent.get().attributeMap())
                .containsEntry(StandardHeaderArguments.CONTENT_LENGTH, "123")
                .containsEntry(StandardHeaderArguments.SQS_MESSAGE_ID, "message-id")
                .containsEntry(StandardHeaderArguments.RECEIPT_ID, "0000000000000_0000_P_TEST");
        verify(sqsClient).deleteMessage(any(DeleteMessageRequest.class));
    }

    private S3EventService createService() {
        return new S3EventService(
                receiveDataConfigProvider,
                sqsClientFactory,
                receiptIdGenerator,
                s3EventConsumer,
                s3ClientPool,
                s3ClientConfigService,
                s3MetaKeysMapper,
                commonSecurityContext);
    }
}
