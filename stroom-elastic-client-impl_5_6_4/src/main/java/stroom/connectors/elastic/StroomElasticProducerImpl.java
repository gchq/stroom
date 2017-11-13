package stroom.connectors.elastic;

import com.google.common.base.Strings;
import org.elasticsearch.client.transport.TransportClient;
import org.elasticsearch.common.settings.Settings;
import org.elasticsearch.common.transport.InetSocketTransportAddress;
import org.elasticsearch.transport.client.PreBuiltTransportClient;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.kafka.StroomKafkaProducer;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.HashMap;
import java.util.Map;
import java.util.function.Consumer;

public class StroomElasticProducerImpl implements StroomElasticProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomElasticProducerImpl.class);

    private final ConnectorProperties properties;

    private TransportClient client;

    private String clusterName;
    private final Map<String, Integer> transportHosts = new HashMap<>();

    public StroomElasticProducerImpl(final ConnectorProperties properties) {
        this.properties = properties;

        if (this.properties != null) {
            clusterName = properties.getProperty(StroomElasticProducer.CLUSTER_NAME);

            if (Strings.isNullOrEmpty(clusterName)) {
                final String msg = String.format(
                        "Stroom is not properly configured to connect to Elastic: %s is required.",
                        StroomElasticProducer.CLUSTER_NAME);
                LOGGER.error(msg);
            }
            final String transportHostsStr = properties.getProperty(StroomElasticProducer.TRANSPORT_HOSTS);
            if (Strings.isNullOrEmpty(transportHostsStr)) {
                final String msg = String.format(
                        "Stroom is not properly configured to connect to Elastic: %s is required.",
                        StroomElasticProducer.TRANSPORT_HOSTS);
                LOGGER.error(msg);
            }
            final String[] transportHostsList = transportHostsStr.split(",");
            for (final String transportHost : transportHostsList) {
                final String[] transportHostParts = transportHost.split(":");
                if (transportHostParts.length == 2) {
                    transportHosts.put(transportHostParts[0], Integer.parseInt(transportHostParts[1]));
                } else {
                    final String msg = String.format("Unexpected Transport Host, should be a colon delimited pair host:port %s", transportHost);
                    LOGGER.error(msg);
                }

            }
        } else {
            final String msg = String.format(
                    "Stroom is not properly configured to connect to Kafka: properties containing at least %s are required.",
                    StroomKafkaProducer.BOOTSTRAP_SERVERS_CONFIG);
            LOGGER.error(msg);
        }
    }

    @Override
    public void send(final String index,
                     final String type,
                     final Map<String, String> values,
                     final Consumer<Exception> exceptionHandler) {
        try {
            initClient();
        } catch (Exception e) {
            final String msg = String.format("Error initialising elastic producer to %s, (%s)",
                    this.transportHosts,
                    e.getMessage());
            LOGGER.error(msg);
            exceptionHandler.accept(e);
            return;
        }

        client.prepareIndex(index, type).setSource(values).get();
    }

    @Override
    public void shutdown() {
        if (client != null) {
            try {
                LOGGER.info("Closing down Kafka Producer");
                client.close();
            } catch (Exception e) {
                LOGGER.error("Error closing kafka producer", e);
            }
        }
    }

    void initClient() throws Exception {
        if (null == client) {
            final Settings settings = Settings.builder()
                    .put("cluster.name", this.clusterName).build();
            client = new PreBuiltTransportClient(settings);
            transportHosts.forEach((host, port) -> {
                try {
                    client.addTransportAddress(new InetSocketTransportAddress(InetAddress.getByName(host), port));
                } catch (UnknownHostException e) {
                    final String msg = String.format("Could not connect to Elastic Host %s - %s", host, e.getLocalizedMessage());
                    LOGGER.error(msg);
                }
            });
        }
    }
}
