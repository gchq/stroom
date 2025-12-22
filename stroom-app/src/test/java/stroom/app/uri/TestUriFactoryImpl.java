/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

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
        final NodeUriConfig nodeUriConfig = new NodeUriConfig();
        final PublicUriConfig publicUriConfig = new PublicUriConfig();
        final UiUriConfig uiUriConfig = new UiUriConfig();
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
