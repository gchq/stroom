package stroom.connectors.elastic;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.common.base.Strings;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.connectors.ConnectorProperties;
import stroom.connectors.kafka.StroomKafkaProducer;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

public class StroomElasticProducerImpl implements StroomElasticProducer {

    private static final Logger LOGGER = LoggerFactory.getLogger(StroomElasticProducerImpl.class);

    private final ConnectorProperties properties;

    private String elasticHttpUrl;
    private String clusterName;
    private final Map<String, Integer> transportHosts = new HashMap<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    public StroomElasticProducerImpl(final ConnectorProperties properties) {
        this.properties = properties;

        if (this.properties != null) {
            clusterName = properties.getProperty(StroomElasticProducer.CLUSTER_NAME);
            elasticHttpUrl = properties.getProperty(StroomElasticProducer.ELASTIC_HTTP_URL);

            if (Strings.isNullOrEmpty(elasticHttpUrl)) {
                final String msg = String.format(
                        "Stroom is not properly configured to connect to Elastic: %s is required.",
                        StroomElasticProducer.ELASTIC_HTTP_URL);
                LOGGER.error(msg);
            }
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
    public void send(final String idFieldName,
                     final String index,
                     final String type,
                     final Map<String, String> values,
                     final Consumer<Exception> exceptionHandler) {
        HttpURLConnection con = null;

        try {
            // Default to using a random UUID as the field name
            String recordId = UUID.randomUUID().toString();

            // If a field name is specified, and it has a given value in this record, use that instead
            if (null != idFieldName) {
                String idFieldValue = values.get(idFieldName);
                if (null != idFieldValue) {
                    recordId = idFieldValue;
                }
            }

            final String url = String.format("%s/%s/%s/%s", this.elasticHttpUrl, index, type, recordId);
            URL obj = new URL(url);
            con = (HttpURLConnection) obj.openConnection();

            //add reuqest header
            con.setRequestMethod("PUT");
            con.setRequestProperty("Accept-Language", "en-US,en;q=0.5");
            con.setRequestProperty("Content-Type", "application/json");

            String body = jsonMapper.writeValueAsString(values);

            // Send post request
            con.setDoOutput(true);
            DataOutputStream wr = new DataOutputStream(con.getOutputStream());
            wr.writeBytes(body);
            wr.flush();
            wr.close();

            int responseCode = con.getResponseCode();

            BufferedReader in = new BufferedReader(
                    new InputStreamReader(con.getInputStream()));
            String inputLine;
            StringBuilder response = new StringBuilder();

            while ((inputLine = in.readLine()) != null) {
                response.append(inputLine);
            }
            in.close();

            if (responseCode != HttpURLConnection.HTTP_CREATED) {
                final String msg = String.format("Bad Response from Elastic Search: %d - %s",
                        responseCode,
                        response);
                exceptionHandler.accept(new Exception(msg));
            }
        } catch (Exception e) {
            final String msg = String.format("Error initialising elastic producer to %s, (%s)",
                    this.transportHosts,
                    e.getMessage());
            LOGGER.error(msg);
            exceptionHandler.accept(e);
        } finally {
            if (null != con) {
                con.disconnect();
            }
        }
    }

    @Override
    public void shutdown() {
    }
}
