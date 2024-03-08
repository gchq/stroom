package stroom.util.config;

import stroom.util.time.StroomDuration;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.assertj.core.api.Assertions;
import org.junit.jupiter.api.Test;

import java.util.List;

class TestOkHttpClientConfig {

    @Test
    void testDeser_empty() throws JsonProcessingException {

        final String json = "{}";
        OkHttpClientConfig clientConfig = new ObjectMapper()
                .readerFor(OkHttpClientConfig.class)
                .readValue(json);

        Assertions.assertThat(clientConfig)
                .isNotNull();
        Assertions.assertThat(clientConfig.getSslConfig())
                .isNull();
        Assertions.assertThat(clientConfig.getHttpProtocols())
                .isNull();
        Assertions.assertThat(clientConfig.getCallTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getConnectionTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getReadTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getWriteTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.isFollowRedirects())
                .isNull();
        Assertions.assertThat(clientConfig.isFollowSslRedirects())
                .isNull();
        Assertions.assertThat(clientConfig.isRetryOnConnectionFailure())
                .isNull();
    }

    @Test
    void testDeser_withValues() throws JsonProcessingException {

        final String json = """
                {
                    "httpProtocols": [
                        "http/1.1",
                        "http/2"],
                    "callTimeout": "1d",
                    "followRedirects": true
                }
                """;
        OkHttpClientConfig clientConfig = new ObjectMapper()
                .readerFor(OkHttpClientConfig.class)
                .readValue(json);

        Assertions.assertThat(clientConfig)
                .isNotNull();
        Assertions.assertThat(clientConfig.getHttpProtocols())
                .isEqualTo(List.of("http/1.1", "http/2"));
        Assertions.assertThat(clientConfig.getSslConfig())
                .isNull();
        Assertions.assertThat(clientConfig.getCallTimeout())
                .isEqualTo(StroomDuration.ofDays(1));
        Assertions.assertThat(clientConfig.getConnectionTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getReadTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.getWriteTimeout())
                .isNull();
        Assertions.assertThat(clientConfig.isFollowRedirects())
                .isTrue();
        Assertions.assertThat(clientConfig.isFollowSslRedirects())
                .isNull();
        Assertions.assertThat(clientConfig.isRetryOnConnectionFailure())
                .isNull();
    }
}
