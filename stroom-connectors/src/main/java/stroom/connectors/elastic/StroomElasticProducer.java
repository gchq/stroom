package stroom.connectors.elastic;

import stroom.connectors.StroomConnector;

import java.util.Map;
import java.util.function.Consumer;

/**
 * A Stroom abstraction over the Elastic client library
 */
public interface StroomElasticProducer extends StroomConnector {

    String TRANSPORT_HOSTS = "transport.hosts";
    String CLUSTER_NAME = "cluster.name";

    void send(String index, String type,
              Map<String, String> values,
              Consumer<Exception> exceptionHandler);
}
