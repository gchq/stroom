package stroom.pipeline.server.xsltfunctions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.Response;
import org.junit.Assert;
import org.junit.Ignore;
import org.junit.Test;
import stroom.util.cache.CacheManager;
import stroom.util.cert.SSLConfig;

@Ignore
public class TestHttpCall {
    @Test
    public void test() throws JsonProcessingException {
        final SSLConfig sslConfig = new SSLConfig();
        sslConfig.setKeyStorePath("/Users/stroomdev66/work/stroom-6.0/stroom-ssl-test/client.jks");
        sslConfig.setKeyStorePassword("password");
        sslConfig.setTrustStorePath("/Users/stroomdev66/work/stroom-6.0/stroom-ssl-test/ca.jks");
        sslConfig.setTrustStorePassword("password");
        sslConfig.setHostnameVerificationEnabled(false);

        final ObjectMapper mapper = new ObjectMapper();
        mapper.configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        mapper.configure(SerializationFeature.INDENT_OUTPUT, true);

        final String clientConfig = mapper.writeValueAsString(sslConfig);

        try (final CacheManager cacheManager = new CacheManager()) {
            HttpCall httpCall = new HttpCall(new HttpClientCache(cacheManager));
            try (Response response = httpCall.execute("https://localhost:5443/", "", "", "", clientConfig)) {
                System.out.println(response.body().string());

            } catch (final Exception e) {
                Assert.fail(e.getMessage());
            }
        }
    }
}
