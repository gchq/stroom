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

package stroom.app;

import stroom.app.HttpClientProviderCacheImpl.HttpClientProviderImpl;
import stroom.pipeline.PipelineConfig;

import org.apache.hc.client5.http.impl.classic.CloseableHttpClient;
import org.apache.hc.core5.io.CloseMode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

/// The HTTP client cache is refreshed so key store files changed on disk are picked up. When a
/// client is replaced the cache releases it, which must not close it while something is still
/// using it.
@ExtendWith(MockitoExtension.class)
class TestHttpClientProviderCacheImpl {

    @Mock
    private CloseableHttpClient httpClient;

    @Test
    void testReplacedClientIsNotClosedWhileInUse() throws Exception {
        final HttpClientProviderImpl provider = new HttpClientProviderImpl(httpClient);
        // A caller gets the client from the cache
        assertThat(provider.acquire()).isTrue();

        // The cache refreshes and releases the replaced client
        provider.release();
        verify(httpClient, never()).close(CloseMode.GRACEFUL);

        // Once the caller has finished with it, it is closed
        provider.close();
        verify(httpClient).close(CloseMode.GRACEFUL);
    }

    @Test
    void testReplacedClientNotInUseIsClosed() throws Exception {
        final HttpClientProviderImpl provider = new HttpClientProviderImpl(httpClient);

        provider.release();

        verify(httpClient).close(CloseMode.GRACEFUL);
    }

    @Test
    void testReleasedClientCannotBeAcquiredAndIsOnlyClosedOnce() throws Exception {
        final HttpClientProviderImpl provider = new HttpClientProviderImpl(httpClient);
        provider.release();

        // A caller that got the client just before it was released must get a fresh one instead
        assertThat(provider.acquire()).isFalse();
        provider.release();

        verify(httpClient, times(1)).close(CloseMode.GRACEFUL);
    }

    @Test
    void testCacheIsRefreshedByDefault() {
        // Without a refresh a client in constant use would never pick up a changed key store file,
        // as expireAfterAccess would never be reached.
        assertThat(new PipelineConfig().getHttpClientCache().getRefreshAfterWrite())
                .isNotNull();
    }
}
