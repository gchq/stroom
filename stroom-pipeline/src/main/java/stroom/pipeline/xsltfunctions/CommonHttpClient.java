package stroom.pipeline.xsltfunctions;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.config.OkHttpClientConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpClientUtil;
import stroom.util.http.HttpTlsConfiguration;
import stroom.util.json.JsonUtil;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.NullSafe;
import stroom.util.time.StroomDuration;

import org.apache.hc.client5.http.classic.HttpClient;

import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

public class CommonHttpClient {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(CommonHttpClient.class);

    private final HttpClientCache httpClientCache;
    private final Map<String, HttpClientConfiguration> configCache = new HashMap<>();

    CommonHttpClient(final HttpClientCache httpClientCache) {
        this.httpClientCache = httpClientCache;
    }

    public HttpClient createClient(final String clientConfigStr) {
        final HttpClientConfiguration configuration = configCache.computeIfAbsent(clientConfigStr, k -> {
            RuntimeException exception = null;
            HttpClientConfiguration httpClientConfiguration = null;
            try {
                // Try deprecated OKHttp config.
                httpClientConfiguration = getOKHttpClientConfiguration(clientConfigStr);
            } catch (final RuntimeException e) {
                exception = e;
            }

            if (httpClientConfiguration == null) {
                try {
                    httpClientConfiguration = getHttpClientConfiguration(clientConfigStr);
                } catch (final RuntimeException e) {
                    if (exception != null) {
                        exception = e;
                    }
                }
            }

            if (exception != null) {
                throw exception;
            }

            if (httpClientConfiguration == null) {
                httpClientConfiguration = HttpClientConfiguration.builder().build();
            }

            return httpClientConfiguration;
        });

        LOGGER.debug(() -> "Creating client");
        return httpClientCache.get(configuration);
    }

    @Deprecated // Moving to Apache HttpClientConfiguration
    private HttpClientConfiguration getOKHttpClientConfiguration(final String string) {
        if (NullSafe.isBlankString(string)) {
            return null;
        }
        try {
            // Try deprecated OKHttp config.
            final OkHttpClientConfig clientConfig = JsonUtil.readValue(string, OkHttpClientConfig.class);
            final HttpTlsConfiguration httpTlsConfiguration = HttpClientUtil
                    .getHttpTlsConfiguration(clientConfig.getSslConfig());
            return HttpClientConfiguration
                    .builder()
                    .connectionRequestTimeout(Objects
                            .requireNonNullElse(clientConfig.getCallTimeout(), StroomDuration.ofMillis(500)))
                    .connectionTimeout(Objects
                            .requireNonNullElse(clientConfig.getConnectionTimeout(), StroomDuration.ofMillis(500)))
                    .tlsConfiguration(httpTlsConfiguration)
                    .build();
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Error parsing OkHTTP client configuration \"{}\". {}", string, e.getMessage()));
            throw ProcessException.create(LogUtil.message(
                    "Error parsing OkHTTP client configuration \"{}\". {}", string, e.getMessage()), e);
        }
    }

    private HttpClientConfiguration getHttpClientConfiguration(final String string) {
        if (NullSafe.isBlankString(string)) {
            return null;
        }
        try {
            return JsonUtil.readValue(string, HttpClientConfiguration.class);
        } catch (final RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message(
                    "Error parsing Apache HTTP client configuration \"{}\". {}", string, e.getMessage()));
            throw ProcessException.create(LogUtil.message(
                    "Error parsing Apache HTTP client configuration \"{}\". {}", string, e.getMessage()), e);
        }
    }
}
