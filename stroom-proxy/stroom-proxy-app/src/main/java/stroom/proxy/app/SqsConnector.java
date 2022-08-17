package stroom.proxy.app;

import stroom.meta.api.AttributeMap;
import stroom.meta.api.StandardHeaderArguments;
import stroom.proxy.app.event.EventStore;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import com.amazonaws.auth.EC2ContainerCredentialsProviderWrapper;
import com.amazonaws.auth.InstanceProfileCredentialsProvider;
import com.amazonaws.auth.profile.ProfileCredentialsProvider;
import com.amazonaws.services.sqs.AmazonSQS;
import com.amazonaws.services.sqs.AmazonSQSClientBuilder;
import com.amazonaws.services.sqs.model.AmazonSQSException;
import com.amazonaws.services.sqs.model.CreateQueueResult;
import com.amazonaws.services.sqs.model.Message;
import com.amazonaws.services.sqs.model.SendMessageBatchRequest;
import com.amazonaws.services.sqs.model.SendMessageBatchRequestEntry;
import com.amazonaws.services.sqs.model.SendMessageRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Date;
import java.util.List;

public class SqsConnector {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(SqsConnector.class);

    private final EventStore eventStore;

    public SqsConnector(final EventStore eventStore) {
        this.eventStore = eventStore;
    }

    public void poll(final SqsConnectorConfig config) {
        try {
            LOGGER.debug(() -> "Getting sqs client");
            final AmazonSQS sqs = AmazonSQSClientBuilder.standard()
                    .withRegion(config.getAwsRegionName())
                    .withCredentials(new InstanceProfileCredentialsProvider(false))
                    .build();

//            try {
//                CreateQueueResult create_result = sqs.createQueue(QUEUE_NAME);
//            } catch (AmazonSQSException e) {
//                if (!e.getErrorCode().equals("QueueAlreadyExists")) {
//                    throw e;
//                }
//            }

            String queueUrl = config.getQueueUrl();
            if (queueUrl == null) {
                LOGGER.debug(() -> "Getting queue name");
                final String queueName = config.getQueueName();
                LOGGER.debug(() -> "Getting queue URL for queue: " + queueName);
                queueUrl = sqs.getQueueUrl(queueName).getQueueUrl();
            }
            LOGGER.debug("Got queue URL: " + queueUrl);

//    SendMessageRequest send_msg_request = new SendMessageRequest()
//            .withQueueUrl(queueUrl)
//            .withMessageBody("hello world")
//            .withDelaySeconds(5);
//    sqs.sendMessage(send_msg_request);
//
//
//    // Send multiple messages to the queue
//    SendMessageBatchRequest send_batch_request = new SendMessageBatchRequest()
//            .withQueueUrl(queueUrl)
//            .withEntries(
//                    new SendMessageBatchRequestEntry(
//                            "msg_1", "Hello from message 1"),
//                    new SendMessageBatchRequestEntry(
//                            "msg_2", "Hello from message 2")
//                            .withDelaySeconds(10));
//    sqs.sendMessageBatch(send_batch_request);

            List<Message> messages;
            do {
                // receive messages from the queue
                LOGGER.debug(() -> "Getting messages");
                messages = sqs.receiveMessage(queueUrl).getMessages();

                // delete messages from the queue
                for (final Message message : messages) {
                    try {
                        final AttributeMap attributeMap = new AttributeMap();
                        if (message.getAttributes() != null) {
                            attributeMap.putAll(message.getAttributes());
                        }

                        attributeMap.putIfAbsent(StandardHeaderArguments.FEED, "TEST");

                        eventStore.consume(attributeMap, message.getMessageId(), message.getBody());
                        sqs.deleteMessage(queueUrl, message.getReceiptHandle());
                    } catch (final RuntimeException e) {
                        LOGGER.error(e::getMessage, e);
                    }
                }
            } while (messages.size() > 0);
        } catch (final Exception e) {
            LOGGER.error(e::getMessage, e);
        }
    }
}
