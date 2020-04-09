package stroom.kafka.api;

import stroom.docref.DocRef;

public interface KafkaProducerFactory {

    /**
     * Gets or creates a shared KafkaProducer for the supplied config docref. The
     * {@link org.apache.kafka.clients.producer.KafkaProducer} will be wrapped in a
     * {@link KafkaProducerSupplier}. If no config can be found an empty {@link KafkaProducerSupplier}
     * will be returned.
     *
     * The {@link KafkaProducerSupplier} should either be used with a try-with-resources block
     * or  {@link KafkaProducerFactory#returnSupplier} should be called when it is finished with.
     */
    KafkaProducerSupplier getSupplier(final DocRef kafkaConfigDocRef);

    /**
     * 'Returns' the {@link KafkaProducerSupplier} to the factory so it can close the wrapped producer
     * if required. This is for use when a try-with-resources block is not applicable.
     * Should only be called once and the {@link KafkaProducerSupplier} should not be
     * used after it is called.
     */
    void returnSupplier(final KafkaProducerSupplier kafkaProducerSupplier);
}
