package stroom.connectors.kafka;

import org.slf4j.Logger;
import stroom.connectors.StroomConnector;

import java.util.Collections;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * A Stroom abstraction over the Kafka Producer library.
 * Allows us to only bind to Kafka libraries at runtime. This is proving desirable because those libraries
 * come with a fair bit of heft in transitive dependencies.
 */
public interface StroomKafkaProducer extends StroomConnector {
    /**
     * This is the one config item that must be present, everything else can be default
     */
    String BOOTSTRAP_SERVERS_CONFIG = "bootstrap.servers";

    /**
     * Given a list of Records, sends to the Kafka broker asynchronously. The record will be sent at some point and tis method will
     * return as soon as the record is received by the producer. The exceptionHandler will be called in the event
     * of an exception sending the message to the broker. An exception may be thrown by this method in the event of
     * a problem handing the records over to the broker.
     *
     * @param stroomRecords    The record, contains the data and partition information.
     * @param exceptionHandler A handler function if the exceptions are thrown. Allows custom exceptions (Runtime only)
     * @return A list of CompletableFuture for the record metadata that will be complete when the broker has acknowledged the
     * records
     */
    List<CompletableFuture<StroomKafkaRecordMetaData>> sendAsync(final List<StroomKafkaProducerRecord<String, byte[]>> stroomRecords,
                                                                 final Consumer<Throwable> exceptionHandler);

    default CompletableFuture<StroomKafkaRecordMetaData> sendAsync(final StroomKafkaProducerRecord<String, byte[]> stroomRecord,
                                                                 final Consumer<Throwable> exceptionHandler) {
        List<CompletableFuture<StroomKafkaRecordMetaData>> futures = sendAsync(Collections.singletonList(stroomRecord), exceptionHandler);

        if (futures == null || futures.isEmpty()) {
            return null;
        } else {
            return futures.get(0);
        }
    }

    /**
     * Given a list of Records, sends to the Kafka broker synchronously. This method will block until all the records
     * have been acknowledged by the broker or an exception is thrown.
     *
     * @param stroomRecords The record, contains the data and partition information.
     * @return A list of record metadata objects
     */
    List<StroomKafkaRecordMetaData> sendSync(final List<StroomKafkaProducerRecord<String, byte[]>> stroomRecords);

    default StroomKafkaRecordMetaData sendSync(final StroomKafkaProducerRecord<String, byte[]> stroomRecord) {
        List<StroomKafkaRecordMetaData> metaDataList = sendSync(Collections.singletonList(stroomRecord));

        if (metaDataList == null || metaDataList.isEmpty()) {
            return null;
        } else {
            return metaDataList.get(0);
        }
    }

    /**
     * Allow manual flushing of the producer by the client. Be aware that the producer
     * is typically shared by many threads so a flush may involve waiting for the messages
     * of other threads to be acknowledged by the broker. USE WITH CAUTION!
     */
    void flush();

    /**
     * Create an exception handler that will just log the error and not raise it. The error message will be logged
     * at ERROR and the full stacktrace at DEBUG
     * @param logger The Logger instance to log with
     * @param topic The name of the topic involved
     * @param key The name of the key involved
     * @return An exception handler
     */
    static Consumer<Throwable> createLogOnlyExceptionHandler(final Logger logger,
                                                             final String topic,
                                                             final String key) {
        final String baseMsg = "Unable to send record to Kafka with topic/key: {}/{}";
        return e -> {
            logger.error(
                    baseMsg + ", due to: {} (enable DEBUG for full stacktrace)",
                    topic,
                    key,
                    e.getMessage());
            logger.debug(baseMsg, e);
        };
    }
}
