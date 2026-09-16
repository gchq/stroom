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

package stroom.proxy.app.handler;

import org.junit.jupiter.api.Test;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * {@code forwardHeadersAdditionalAllowSet} decides which extra headers are forwarded to a
 * downstream — a security-relevant setting — and it was in none of {@code equals}, {@code hashCode}
 * or {@code toString}. Two configurations that forward different headers compared equal, and a
 * logged config did not say which headers it would forward.
 */
class TestForwardHttpPostConfig {

    @Test
    void testTwoConfigsForwardingDifferentHeadersAreNotEqual() {
        final ForwardHttpPostConfig allowsFoo = baseBuilder()
                .forwardHeadersAdditionalAllowSet(Set.of("Foo"))
                .build();
        final ForwardHttpPostConfig allowsNothingExtra = baseBuilder().build();

        assertThat(allowsFoo).isNotEqualTo(allowsNothingExtra);
        assertThat(allowsFoo.hashCode()).isNotEqualTo(allowsNothingExtra.hashCode());
        assertThat(allowsFoo.toString()).contains("Foo");
    }

    @Test
    void testTwoConfigsAlikeInEveryFieldAreEqual() {
        assertThat(baseBuilder().forwardHeadersAdditionalAllowSet(Set.of("Foo")).build())
                .isEqualTo(baseBuilder().forwardHeadersAdditionalAllowSet(Set.of("Foo")).build());
    }

    private static ForwardHttpPostConfig.Builder baseBuilder() {
        return ForwardHttpPostConfig.builder()
                .enabled(true)
                .instant(false)
                .name("test")
                .forwardUrl("http://downstream:8080/datafeed");
    }
}
