package stroom.pipeline.xsltfunctions;

import stroom.cache.api.CacheManager;
import stroom.cache.impl.CacheManagerImpl;
import stroom.pipeline.PipelineConfig;
import stroom.util.cert.SSLConfig;
import stroom.util.http.BasicHttpClientFactory;
import stroom.util.http.HttpClientConfigConverter;
import stroom.util.http.HttpClientFactory;
import stroom.util.io.PathCreator;
import stroom.util.io.SimplePathCreator;
import stroom.util.io.StreamUtil;
import stroom.util.json.JsonUtil;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Disabled;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.InputStream;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.fail;

@Disabled
class TestHttpCall {

    @Test
    void test(@TempDir final Path tempDir) throws JsonProcessingException {
        final SSLConfig sslConfig = SSLConfig.builder()
                .withKeyStorePath("/Users/stroomdev66/work/stroom-6.0/stroom-ssl-test/client.jks")
                .withKeyStorePassword("password")
                .withTrustStorePath("/Users/stroomdev66/work/stroom-6.0/stroom-ssl-test/ca.jks")
                .withTrustStorePassword("password")
                .withHostnameVerificationEnabled(false)
                .build();

        final ObjectMapper mapper = JsonUtil.getMapper();
        final String clientConfig = mapper.writeValueAsString(sslConfig);

        final PathCreator pathCreator = new SimplePathCreator(
                () -> tempDir.resolve("home"),
                () -> tempDir);

        try (final CacheManager cacheManager = new CacheManagerImpl()) {
            final HttpClientFactory httpClientFactory =
                    new BasicHttpClientFactory(new HttpClientConfigConverter(pathCreator));
            HttpCall httpCall = new HttpCall(new HttpClientCache(cacheManager, PipelineConfig::new, httpClientFactory));
            httpCall.execute("https://localhost:5443/", "", "", "", clientConfig,
                    response -> {
                        try (final InputStream inputStream = response.getEntity().getContent()) {
                            System.out.println(StreamUtil.streamToString(inputStream));
                            return response.getCode();
                        }
                    });
        } catch (final Exception e) {
            fail(e.getMessage());
        }
    }
}
