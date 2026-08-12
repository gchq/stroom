/*
 * Copyright 2016-2026 Crown Copyright
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

package stroom.util.net;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestSsrfGuard {

    @Test
    void requirePublicHostRejectsAllInternalAddresses() {
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://127.0.0.1/x"));                 // loopback
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://169.254.169.254/latest/meta")); // cloud metadata
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://10.0.0.5:8080/"));              // RFC1918
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://192.168.1.1/"));               // RFC1918
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://172.16.0.1/"));                // RFC1918
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://0.0.0.0/"));                   // wildcard
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://[::1]:9200/"));                // IPv6 loopback
        assertBlocked(() -> SsrfGuard.requirePublicHost("http://100.64.0.1/"));                // carrier-grade NAT
    }

    @Test
    void requirePublicHostAllowsPublicAddresses() {
        assertThatCode(() -> SsrfGuard.requirePublicHost("https://8.8.8.8/v1/models"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfGuard.requirePublicHost("https://1.1.1.1/"))
                .doesNotThrowAnyException();
    }

    @Test
    void rejectMetadataAndWildcardBlocksMetadataButAllowsPrivateAndLoopback() {
        // Never-legitimate targets are blocked...
        assertBlocked(() -> SsrfGuard.rejectMetadataAndWildcard("http://169.254.169.254/latest/meta"));
        assertBlocked(() -> SsrfGuard.rejectMetadataAndWildcard("http://0.0.0.0:9200/"));

        // ...but loopback and private addresses are allowed, because an Elasticsearch cluster legitimately
        // lives there (dev on localhost, internal clusters on RFC1918).
        assertThatCode(() -> SsrfGuard.rejectMetadataAndWildcard("http://127.0.0.1:9200/"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfGuard.rejectMetadataAndWildcard("http://10.1.2.3:9200/"))
                .doesNotThrowAnyException();
        assertThatCode(() -> SsrfGuard.rejectMetadataAndWildcard("https://8.8.8.8:9200/"))
                .doesNotThrowAnyException();
    }

    @Test
    void malformedOrHostlessUrlsAreRejected() {
        assertBlocked(() -> SsrfGuard.requirePublicHost("not a url"));
        assertBlocked(() -> SsrfGuard.requirePublicHost("/relative/only"));
    }

    private static void assertBlocked(final ThrowingCallable call) {
        assertThatThrownBy(call).isInstanceOf(IllegalArgumentException.class);
    }
}
