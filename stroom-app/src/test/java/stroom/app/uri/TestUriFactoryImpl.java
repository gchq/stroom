package stroom.app.uri;

import stroom.config.app.AppConfig;
import stroom.config.app.Config;
import stroom.config.common.NodeUriConfig;
import stroom.config.common.PublicUriConfig;
import stroom.config.common.UiUriConfig;
import stroom.config.common.UriFactory;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import static org.assertj.core.api.Assertions.assertThat;

public class TestUriFactoryImpl {

    @Test
    void testAll() {
        NodeUriConfig nodeUriConfig = new NodeUriConfig();
        PublicUriConfig publicUriConfig = new PublicUriConfig();
        UiUriConfig uiUriConfig = new UiUriConfig();
        test(nodeUriConfig,
                publicUriConfig,
                uiUriConfig,
                "http://localhost:8080",
                "http://localhost:8080",
                "http://localhost:8080");

        test(new NodeUriConfig("https", "test", 443, "path"),
                publicUriConfig,
                uiUriConfig,
                "https://test:443/path",
                "https://test:443/path",
                "https://test:443/path");

        test(new NodeUriConfig("https", "test", null, null),
                publicUriConfig,
                uiUriConfig,
                "https://test",
                "https://test",
                "https://test");

        test(new NodeUriConfig("https", "test", null, null),
                new PublicUriConfig("https", "test", 443, "test"),
                uiUriConfig,
                "https://test",
                "https://test:443/test",
                "https://test:443/test");

        test(new NodeUriConfig("http", "localhost", 8080, null),
                new PublicUriConfig("https", "test", null, null),
                new UiUriConfig("http", "dev", 5000, "dev"),
                "http://localhost:8080",
                "https://test",
                "http://dev:5000/dev");
    }

    private void test(final NodeUriConfig nodeUriConfig,
                      final PublicUriConfig publicUriConfig,
                      final UiUriConfig uiUriConfig,
                      final String nodeUri,
                      final String publicUri,
                      final String uiUri) {
        final AppConfig appConfig = Mockito.mock(AppConfig.class);
        Mockito.when(appConfig.getNodeUri()).thenReturn(nodeUriConfig);
        Mockito.when(appConfig.getPublicUri()).thenReturn(publicUriConfig);
        Mockito.when(appConfig.getUiUri()).thenReturn(uiUriConfig);

        final UriFactory uriFactory = new UriFactoryImpl(new Config(appConfig), () -> appConfig);
        assertThat(uriFactory.nodeUri("/").toString()).isEqualTo(nodeUri);
        assertThat(uriFactory.uiUri("/").toString()).isEqualTo(uiUri);
        assertThat(uriFactory.publicUri("/").toString()).isEqualTo(publicUri);
    }
}
