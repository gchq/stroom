package stroom.elastic.impl.http;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import stroom.elastic.impl.ElasticIndexConfigDoc;
import stroom.elastic.api.ElasticIndexWriter;

import java.io.BufferedReader;
import java.io.DataOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;
import java.util.UUID;
import java.util.function.Consumer;

class HttpElasticIndexWriter implements ElasticIndexWriter {
    private static final Logger LOGGER = LoggerFactory.getLogger(HttpElasticIndexWriter.class);

    private String elasticHttpUrl;
    //    private final Map<String, Integer> transportHosts = new HashMap<>();
    private static final ObjectMapper jsonMapper = new ObjectMapper();

    HttpElasticIndexWriter(final ElasticIndexConfigDoc elasticIndexConfigDoc) {
//        clusterName = elasticConfigDoc.getClusterName();
        elasticHttpUrl = elasticIndexConfigDoc.getElasticHttpUrl();

        if (elasticHttpUrl == null || elasticHttpUrl.isEmpty()) {
            throw new RuntimeException("Stroom is not properly configured to connect to Elastic: HTTP URL is required.");
        }
//        if (clusterName == null || clusterName.isEmpty()) {
//            throw new RuntimeException("Stroom is not properly configured to connect to Elastic: Cluster name is required.");
//        }
//        final String transportHostsStr = elasticIndexConfigDoc.getTransportHosts();
//        if (transportHostsStr == null || transportHostsStr.isEmpty()) {
//            throw new RuntimeException("Stroom is not properly configured to connect to Elastic: Transport Hosts is required.");
//        }
//        final String[] transportHostsList = transportHostsStr.split(",");
//        for (final String transportHost : transportHostsList) {
//            final String[] transportHostParts = transportHost.split(":");
//            if (transportHostParts.length == 2) {
//                transportHosts.put(transportHostParts[0], Integer.parseInt(transportHostParts[1]));
//            } else {
//                final String msg = String.format("Unexpected Transport Host, should be a colon delimited pair host:port %s", transportHost);
//                throw new RuntimeException(msg);
//            }
//        }
        LOGGER.info("Elastic Producer successfully created for {}", elasticHttpUrl);
    }

    @Override
    public void write(final String idFieldName,
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

            switch (responseCode) {
                case HttpURLConnection.HTTP_CREATED:
                case HttpURLConnection.HTTP_ACCEPTED:
                case HttpURLConnection.HTTP_OK:
                    break;
                default:
                    final String msg = String.format("Bad Response from Elastic Search: %d - %s",
                            responseCode,
                            response);
                    exceptionHandler.accept(new Exception(msg));
                    break;
            }
        } catch (final IOException e) {
            final String msg = String.format("Error initialising elastic producer to %s, (%s)",
                    this.elasticHttpUrl,
                    e.getMessage());
            LOGGER.error(msg);
            exceptionHandler.accept(e);
        } finally {
            if (null != con) {
                con.disconnect();
            }
        }
    }
}
