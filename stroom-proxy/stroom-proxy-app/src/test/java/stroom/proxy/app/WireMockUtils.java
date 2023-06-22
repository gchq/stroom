package stroom.proxy.app;

import com.github.tomakehurst.wiremock.verification.LoggedRequest;
import org.assertj.core.api.Assertions;

public class WireMockUtils {
    /**
     * Assert that a http header is present and has this value
     */
    public static void assertHeaderValue(final LoggedRequest loggedRequest,
                                         final String key,
                                         final String value) {
        Assertions.assertThat(loggedRequest.getHeader(key))
                .isNotNull()
                .isEqualTo(value);
    }
}
