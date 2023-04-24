package stroom.proxy.app;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;
import java.util.concurrent.atomic.LongAdder;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.Entity;
import javax.ws.rs.client.Invocation.Builder;
import javax.ws.rs.core.Response;

public class PostDataHelper {

    private static final Logger LOGGER = LoggerFactory.getLogger(PostDataHelper.class);

    private final Client client;
    private final String url;
    private final LongAdder postToProxyCount = new LongAdder();

    public PostDataHelper(final Client client,
                          final String url) {
        this.client = client;
        this.url = url;
    }

    public int sendData(final String feed,
                        final String system,
                        final String environment,
                        final Map<String, String> extraHeaders,
                        final String data) {
        int status;
        try {
            final Builder builder = client.target(url)
                    .request()
                    .header("Feed", feed)
                    .header("System", system)
                    .header("Environment", environment);

            if (extraHeaders != null) {
                extraHeaders.forEach(builder::header);
            }
            LOGGER.info("Sending POST request to {}", url);
            final Response response = builder.post(Entity.text(data));
            postToProxyCount.increment();
            status = response.getStatus();
            final String responseText = response.readEntity(String.class);
            LOGGER.info("datafeed response ({}):\n{}", status, responseText);

        } catch (final Exception e) {
            throw new RuntimeException("Error sending request to " + url, e);
        }
        return status;
    }

    public long getPostCount() {
        return postToProxyCount.sum();
    }
}
