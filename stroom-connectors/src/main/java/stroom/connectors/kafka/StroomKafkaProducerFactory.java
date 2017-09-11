package stroom.connectors.kafka;

import stroom.connectors.StroomConnectorFactory;

/** *
 * This interface simply defines which producer class stroom Kafka libraries will use.
 */
public interface StroomKafkaProducerFactory extends StroomConnectorFactory<StroomKafkaProducer> {

}
