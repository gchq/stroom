import stroom.search.elastic.ElasticClientFactory;
import stroom.util.test.StroomJUnit4ClassRunner;

import org.apache.http.HttpHost;
import org.junit.Assert;
import org.junit.Test;
import org.junit.runner.RunWith;

@RunWith(StroomJUnit4ClassRunner.class)
public class TestElasticClientFactory {
    @Test
    public void TestHostFromUrl() {
        // Scheme and hostname
        final String hostName = "elastic.example.com.au";
        String url = "https://" + hostName;
        HttpHost host = ElasticClientFactory.hostFromUrl(url);
        Assert.assertNotNull("Valid host is returned", host);
        Assert.assertEquals("https", host.getSchemeName());
        Assert.assertEquals(hostName, host.getHostName());

        // Scheme, hostname and port
        final int port = 9200;
        url = "http://" + hostName + ":9200";
        host = ElasticClientFactory.hostFromUrl(url);
        Assert.assertNotNull("Valid host is returned", host);
        Assert.assertEquals(hostName, host.getHostName());
        Assert.assertEquals(port, host.getPort());

        // Invalid URL
        url = hostName;
        host = ElasticClientFactory.hostFromUrl(url);
        Assert.assertNull("No host is returned for an invalid URL", host);
    }
}
