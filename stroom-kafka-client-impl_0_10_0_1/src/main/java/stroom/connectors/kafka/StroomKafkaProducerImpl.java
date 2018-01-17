package stroom.connectors.kafka;

import com.google.common.base.Strings;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ConnectorProperties;

import java.util.ArrayList;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.function.Consumer;
import java.util.stream.Collectors;

/**
 * A singleton responsible for sending records to Kafka.
 * <p>
 * Requires configuration:
 * <<code>
 * stroom.kafka.bootstrap.servers=<host1>:<port>,<host2>:<port>,etc
 * </code>
 */
class StroomKafkaProducerImpl implements StroomKafkaProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomKafkaProducerImpl.class);

    private final ConnectorProperties properties;
    private final String bootstrapServers;
    //instance of a kafka producer that will be shared by all threads
    private final Producer<String, byte[]> producer;

    public StroomKafkaProducerImpl(final ConnectorProperties properties) {
        this.properties = properties;
        this.bootstrapServers = (properties != null)
                ? properties.getProperty(StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG)
                : null;

        if (this.properties != null) {
            if (Strings.isNullOrEmpty(bootstrapServers)) {
                final String msg = String.format(
                        "Stroom is not properly configured to connect to Kafka: %s is required.",
                        StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG);
                throw new RuntimeException(msg);
            }
        } else {
            final String msg = String.format(
                    "Stroom is not properly configured to connect to Kafka: properties containing at least %s are required.",
                    StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG);
            throw new RuntimeException(msg);
        }

        LOGGER.info("Initialising kafka producer for {}", bootstrapServers);
        Properties props = getProperties();

        LOGGER.info("Creating Kafka Producer with Props: " + props);

        final ClassLoader classLoader = Thread.currentThread().getContextClassLoader();
        try {
            // This bizzarness has to be done to force the Kafka config libraries to use the 'Kafka Classloader'
            // instead of the current thread content class loader.
            // https://issues.apache.org/jira/browse/KAFKA-3218
            Thread.currentThread().setContextClassLoader(null);
            this.producer = new KafkaProducer<>(props);
        } catch (Exception e) {
            throw new RuntimeException(String.format("Error initialising kafka producer for %s, due to %s",
                    bootstrapServers, e.getMessage()), e);
        } finally {
            Thread.currentThread().setContextClassLoader(classLoader);
        }
        LOGGER.info("Kafka Producer successfully created");
        LOGGER.info(String.format("With Properties: %s", props));
    }


    @Override
    public List<CompletableFuture<StroomKafkaRecordMetaData>> sendAsync(
            final List<StroomKafkaProducerRecord<String, byte[]>> stroomRecords,
            final Consumer<Exception> exceptionHandler) {

        Callback callback = (recordMetadata, exception) -> {
            if (exception != null) {
                exceptionHandler.accept(exception);
            }
            LOGGER.trace("Record sent to Kafka");
        };

//        send(stroomRecords, false, exceptionHandler);
        List<CompletableFuture<StroomKafkaRecordMetaData>> futures = send(stroomRecords, exceptionHandler);
//                stroomRecords.stream()
//                .map(this::mapStroomRecord)
//                .map(producerRecord -> {
//                    CompletableFuture<StroomKafkaRecordMetaData> future = new CompletableFuture<>();
//                    producer.send(producerRecord, (recordMetadata, exception) -> {
//                        if (exception != null) {
//                            future.completeExceptionally(exception);
//                            exceptionHandler.accept(exception);
//                        } else {
//                            future.complete(WrappedRecordMetaData.wrap(recordMetadata));
//                        }
//                        LOGGER.trace("Record sent to Kafka");
//                    });
//                    return future;
//                })
//                .collect(Collectors.toList());
        return futures;
    }

    @Override
    public List<StroomKafkaRecordMetaData> sendSync(final List<StroomKafkaProducerRecord<String, byte[]>> stroomRecords) {

//        send(stroomRecords, true, null);
        List<CompletableFuture<StroomKafkaRecordMetaData>> futures = send(stroomRecords, null);
//                stroomRecords.stream()
//                .map(this::mapStroomRecord)
//                .map(producerRecord -> {
//                    CompletableFuture<StroomKafkaRecordMetaData> future = new CompletableFuture<>();
//                    producer.send(producerRecord, (recordMetadata, exception) -> {
//                        if (exception != null) {
//                            future.completeExceptionally(exception);
//                        } else {
//                            future.complete(WrappedRecordMetaData.wrap(recordMetadata));
//                        }
//                        LOGGER.trace("Record sent to Kafka");
//                    });
//                    return future;
//                })
//                .collect(Collectors.toList());

        List<StroomKafkaRecordMetaData> metaDataList = new ArrayList<>();
        //Now wait for them all to complete
        futures.forEach(future -> {
            try {
                metaDataList.add(future.get());
            } catch (InterruptedException e) {
                LOGGER.warn("Thread {} interrupted", Thread.currentThread().getName());
                Thread.currentThread().interrupt();
            } catch (ExecutionException e) {
                //this is sync so throw rather than using the callback
                throw new RuntimeException(
                        String.format("Error sending %s records to kafka", futures.size()), e);
            }
        });
        return metaDataList;
    }

    private List<CompletableFuture<StroomKafkaRecordMetaData>> send(
            final List<StroomKafkaProducerRecord<String, byte[]>> stroomRecords,
            final Consumer<Exception> exceptionHandler) {

        List<CompletableFuture<StroomKafkaRecordMetaData>> futures = stroomRecords.stream()
                .map(this::mapStroomRecord)
                .map(producerRecord -> {
                    CompletableFuture<StroomKafkaRecordMetaData> future = new CompletableFuture<>();
                    producer.send(producerRecord, (recordMetadata, exception) -> {
                        if (exception != null) {
                            future.completeExceptionally(exception);
                            if (exceptionHandler != null) {
                                exceptionHandler.accept(exception);
                            }
                        } else {
                            future.complete(WrappedRecordMetaData.wrap(recordMetadata));
                        }
                        LOGGER.trace("Record sent to Kafka");
                    });
                    return future;
                })
                .collect(Collectors.toList());

        return futures;
    }

    private ProducerRecord<String, byte[]> mapStroomRecord(final StroomKafkaProducerRecord<String, byte[]> stroomRecord) {
        return new ProducerRecord<>(
                stroomRecord.topic(),
                stroomRecord.partition(),
                stroomRecord.timestamp(),
                stroomRecord.key(),
                stroomRecord.value());
    }

    public void flush() {
        producer.flush();
    }

    private Properties getProperties() {
        Properties props = new Properties();

        //The following props are configurable within stroom
        properties.copyProp(props, ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, null);
        properties.copyProp(props, ProducerConfig.ACKS_CONFIG, "all");
        properties.copyProp(props, ProducerConfig.RETRIES_CONFIG, 0);
        properties.copyProp(props, ProducerConfig.BATCH_SIZE_CONFIG, 16384);
        properties.copyProp(props, ProducerConfig.LINGER_MS_CONFIG, 1);
        properties.copyProp(props, ProducerConfig.BUFFER_MEMORY_CONFIG, 33554432);

        //serializers are hard coded as we have to specify the types when creating the
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.StringSerializer.class.getName());
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG,
                org.apache.kafka.common.serialization.ByteArraySerializer.class.getName());

        return props;
    }

    @Override
    public void shutdown() {
        try {
            LOGGER.info("Closing down Kafka Producer");
            producer.close();
        } catch (Exception e) {
            LOGGER.error("Error closing kafka producer", e);
        }
    }
}
