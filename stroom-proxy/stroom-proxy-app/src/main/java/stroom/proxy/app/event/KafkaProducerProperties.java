package stroom.proxy.app.event;

import org.apache.kafka.clients.producer.ProducerConfig;

import java.util.Properties;

public class KafkaProducerProperties {

    private final Properties properties;
    private int count;

    public KafkaProducerProperties(final KafkaProducerConfig config) {
        properties = new Properties();
        add(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, config.getBootstrapServers());
        add(ProducerConfig.CLIENT_DNS_LOOKUP_CONFIG, config.getClientDnsLookup());
        add(ProducerConfig.METADATA_MAX_AGE_CONFIG, config.getMetadataMaxAgeMs());
        add(ProducerConfig.BATCH_SIZE_CONFIG, config.getBatchSize());
        add(ProducerConfig.ACKS_CONFIG, config.getAcks());
        add(ProducerConfig.LINGER_MS_CONFIG, config.getLingerMs());
        add(ProducerConfig.REQUEST_TIMEOUT_MS_CONFIG, config.getRequestTimeoutMs());
        add(ProducerConfig.DELIVERY_TIMEOUT_MS_CONFIG, config.getDeliveryTimeoutMs());
        add(ProducerConfig.CLIENT_ID_CONFIG, config.getClientId());
        add(ProducerConfig.SEND_BUFFER_CONFIG, config.getSendBufferBytes());
        add(ProducerConfig.RECEIVE_BUFFER_CONFIG, config.getReceiveBufferBytes());
        add(ProducerConfig.MAX_REQUEST_SIZE_CONFIG, config.getMaxRequestSize());
        add(ProducerConfig.RECONNECT_BACKOFF_MS_CONFIG, config.getReconnectBackoffMs());
        add(ProducerConfig.RECONNECT_BACKOFF_MAX_MS_CONFIG, config.getReconnectBackoffMaxMs());
        add(ProducerConfig.MAX_BLOCK_MS_CONFIG, config.getMaxBlockMs());
        add(ProducerConfig.BUFFER_MEMORY_CONFIG, config.getBufferMemory());
        add(ProducerConfig.RETRY_BACKOFF_MS_CONFIG, config.getRetryBackoffMs());
        add(ProducerConfig.COMPRESSION_TYPE_CONFIG, config.getCompressionType());
        add(ProducerConfig.METRICS_SAMPLE_WINDOW_MS_CONFIG, config.getMetricsSampleWindowMs());
        add(ProducerConfig.METRICS_NUM_SAMPLES_CONFIG, config.getMetricsNumSamples());
        add(ProducerConfig.METRICS_RECORDING_LEVEL_CONFIG, config.getMetricsRecordingLevel());
        add(ProducerConfig.METRIC_REPORTER_CLASSES_CONFIG, config.getMetricReporters());
        add(ProducerConfig.MAX_IN_FLIGHT_REQUESTS_PER_CONNECTION, config.getMaxInFlightRequestsPerConnection());
        add(ProducerConfig.RETRIES_CONFIG, config.getRetries());
        add(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, config.getKeySerializer());
        add(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, config.getValueSerializer());
        add(ProducerConfig.CONNECTIONS_MAX_IDLE_MS_CONFIG, config.getConnectionsMaxIdleMs());
        add(ProducerConfig.PARTITIONER_CLASS_CONFIG, config.getPartitionerClass());
        add(ProducerConfig.INTERCEPTOR_CLASSES_CONFIG, config.getInterceptorClasses());
        add(ProducerConfig.ENABLE_IDEMPOTENCE_CONFIG, config.getEnableIdempotence());
        add(ProducerConfig.TRANSACTION_TIMEOUT_CONFIG, config.getTransactionTimeoutMs());
        add(ProducerConfig.TRANSACTIONAL_ID_CONFIG, config.getTransactionalId());

        if (count != ProducerConfig.configNames().size()) {
            throw new RuntimeException("Not all properties are mapped");
        }
    }

    public Properties getProperties() {
        return properties;
    }

    private void add(final Object key, final Object value) {
        count++;
        if (value != null) {
            properties.put(key, value);
        }
    }

}
