package stroom.connectors.kafka;

import stroom.connectors.StroomConnectorProducerFactory;

/** *
 * This interface simply defines which producer class stroom Kafka libraries will use.
 */
public interface StroomKafkaProducerFactory extends StroomConnectorProducerFactory<StroomKafkaProducer> {

}
