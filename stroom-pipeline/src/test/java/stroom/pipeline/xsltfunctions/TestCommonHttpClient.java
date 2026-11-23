/*
 * Copyright 2026 Crown Copyright
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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.filter.XsltConfig;
import stroom.util.http.HttpClientConfiguration;
import stroom.util.http.HttpProxyConfiguration;
import stroom.util.jersey.HttpClientProviderCache;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;

class TestCommonHttpClient {

    private final HttpClientProviderCache cache = Mockito.mock(HttpClientProviderCache.class);

    @Test
    void testSecureModeUsesAddressCheckedClient() {
        newClient(true).createClientProvider("");

        Mockito.verify(cache).getAddressChecked(any(HttpClientConfiguration.class));
        Mockito.verify(cache, Mockito.never()).get(any());
    }

    @Test
    void testSecureModeOffUsesOrdinaryClient() {
        newClient(false).createClientProvider("");

        Mockito.verify(cache).get(any(HttpClientConfiguration.class));
        Mockito.verify(cache, Mockito.never()).getAddressChecked(any());
    }

    @Test
    void testProxyRefusedInSecureMode() {
        final CommonHttpClient client = newClient(true);
        final HttpClientConfiguration configuration = HttpClientConfiguration.builder()
                .proxyConfiguration(new HttpProxyConfiguration("localhost", 3128, null, null, null))
                .build();

        assertThatThrownBy(() -> client.createClientProvider(configuration))
                .hasMessageContaining("proxy");
        Mockito.verifyNoInteractions(cache);
    }

    @Test
    void testProxyAllowedWithSecureModeOff() {
        final HttpClientConfiguration configuration = HttpClientConfiguration.builder()
                .proxyConfiguration(new HttpProxyConfiguration("localhost", 3128, null, null, null))
                .build();

        newClient(false).createClientProvider(configuration);

        Mockito.verify(cache).get(configuration);
    }

    private CommonHttpClient newClient(final boolean secureMode) {
        final XsltConfig xsltConfig = new XsltConfig(null, null, secureMode, List.of(), null, null);
        return new CommonHttpClient(cache, () -> xsltConfig);
    }
}
