package stroom.pipeline.xsltfunctions;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import okhttp3.Response;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.pipeline.PipelineConfig;
import stroom.util.cert.SSLConfig;

import static org.assertj.core.api.Assertions.fail;

@Disabled
class TestHttpCall {
    @Test
    void test() throws JsonProcessingException {
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

        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            HttpCall httpCall = new HttpCall(new HttpClientCache(cacheManager, new PipelineConfig()));
            try (Response response = httpCall.execute("https://localhost:5443/", "", "", "", clientConfig)) {
                System.out.println(response.body().string());

            } catch (final Exception e) {
                fail(e.getMessage());
            }
        }
    }
}
