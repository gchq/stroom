package stroom.proxy.app.event;

import stroom.meta.api.AttributeMap;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.header.Header;
import org.apache.kafka.common.header.internals.RecordHeader;
import org.apache.kafka.common.serialization.ByteArraySerializer;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.ByteBuffer;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Future;
import java.util.function.Consumer;

public class KafkaEventConsumer implements EventConsumer {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(KafkaEventConsumer.class);

    private final KafkaEventConsumerConfig config;
    private final KafkaProducer<byte[], byte[]> kafkaProducer;

    public KafkaEventConsumer(final KafkaEventConsumerConfig config) {
        this.config = config;
        final Properties properties = new KafkaProducerProperties(config.getProducerConfig()).getProperties();

        try {
            LOGGER.debug(() -> "Creating KafkaProducer");
            kafkaProducer = new KafkaProducer<>(properties, new ByteArraySerializer(), new ByteArraySerializer());
        } catch (Exception e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException("Error creating KafkaProducer " + e.getMessage(), e);
        }
    }

    private void add(final Properties properties, final Object key, final Object value) {
        if (value != null) {
            properties.put(key, value);
        }
    }

    private byte[] getBytes(final String string) {
        if (string == null) {
            return new byte[0];
        }
        return string.getBytes(StandardCharsets.UTF_8);
    }

    private byte[] createKey(final FeedKey feedKey) {
        final byte[] feedBytes = getBytes(feedKey.feed());
        final byte[] typeBytes = getBytes(feedKey.type());
        final byte[] arr = new byte[feedBytes.length + typeBytes.length + Integer.BYTES + Integer.BYTES];
        final ByteBuffer byteBuffer = ByteBuffer.wrap(arr);
        byteBuffer.put(feedBytes);
        byteBuffer.put(typeBytes);
        byteBuffer.putInt(feedBytes.length);
        byteBuffer.putInt(typeBytes.length);
        return arr;
    }

    @Override
    public void consume(final AttributeMap attributeMap, final Consumer<OutputStream> consumer) {
        try {
            // Get value.
            final ByteArrayOutputStream byteArrayOutputStream = new ByteArrayOutputStream();
            consumer.accept(byteArrayOutputStream);
            byteArrayOutputStream.flush();

            final List<Header> headers = new ArrayList<>(attributeMap.size());
            attributeMap.forEach((k, v) -> {
                final Header header = new RecordHeader(k, v.getBytes(StandardCharsets.UTF_8));
                headers.add(header);
            });

            final String topic = config.getTopic();
            final Integer partition = null;
            final Long timestamp = System.currentTimeMillis();
            final byte[] key = createKey(new FeedKey(attributeMap.get("Feed"), attributeMap.get("Type")));
            final byte[] value = byteArrayOutputStream.toByteArray();

            final ProducerRecord<byte[], byte[]> record = new ProducerRecord<>(
                    topic,
                    partition,
                    timestamp,
                    key,
                    value,
                    headers);

            final Future<RecordMetadata> sendFuture = kafkaProducer.send(record);
            final RecordMetadata recordMetadata = sendFuture.get();
            LOGGER.debug(() -> "Sent data: " + recordMetadata);
        } catch (final IOException | InterruptedException | ExecutionException e) {
            LOGGER.error(e::getMessage, e);
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
