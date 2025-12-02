import stroom.search.elastic.ElasticClientFactory;

import org.apache.hc.core5.http.HttpHost;
import org.junit.jupiter.api.Assertions;
import org.junit.jupiter.api.Test;

public class TestElasticClientFactory {

    @Test
    public void testHostFromUrl() {
        // Scheme and hostname
        final String hostName = "elastic.example.com.au";
        String url = "https://" + hostName;
        HttpHost host = ElasticClientFactory.hostFromUrl(url);
        Assertions.assertNotNull(host, "Valid host is returned");
        Assertions.assertEquals("https", host.getSchemeName());
        Assertions.assertEquals(hostName, host.getHostName());

        // Scheme, hostname and port
        final int port = 9200;
        url = "http://" + hostName + ":9200";
        host = ElasticClientFactory.hostFromUrl(url);
        Assertions.assertNotNull(host, "Valid host is returned");
        Assertions.assertEquals(hostName, host.getHostName());
        Assertions.assertEquals(port, host.getPort());

        // Invalid URL
        url = hostName;
        host = ElasticClientFactory.hostFromUrl(url);
        Assertions.assertNull(host, "No host is returned for an invalid URL");
    }
}
