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


package stroom.gwt.workbench.client.app.rest;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestCorpusKey {

    @Test
    void testParse() {
        final CorpusKey key = CorpusKey.parse("GET /api/sessionInfo/v1 #da39a3ee5e6b4b0d");

        assertThat(key.getMethod()).isEqualTo("GET");
        assertThat(key.getPath()).isEqualTo("/sessionInfo/v1");
        assertThat(key.getQuery()).isNull();
        assertThat(key.getBodyHash()).isEqualTo("da39a3ee5e6b4b0d");
    }

    @Test
    void testParse_withQueryAndNoHash() {
        final CorpusKey key = CorpusKey.parse("post /api/node/v1/info?node=node1a");

        assertThat(key.getMethod()).isEqualTo("POST");
        assertThat(key.getPath()).isEqualTo("/node/v1/info");
        assertThat(key.getQuery()).isEqualTo("node=node1a");
        assertThat(key.getBodyHash()).isNull();
    }

    @Test
    void testParse_invalid() {
        assertThatThrownBy(() -> CorpusKey.parse("GET")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> CorpusKey.parse("GET /sessionInfo/v1 #00"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("/api/");
    }

    @Test
    void testParse_badHash() {
        assertThatThrownBy(() -> CorpusKey.parse("GET /api/sessionInfo/v1 #xyz"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("hex");
    }

    @Test
    void testBodyHash() {
        // As gwt-suite/lib/corpus.mjs hashes bodies: sha1(body || '').slice(0, 16)
        assertThat(CorpusKey.bodyHash(null)).isEqualTo("da39a3ee5e6b4b0d");
        assertThat(CorpusKey.bodyHash("")).isEqualTo("da39a3ee5e6b4b0d");
        assertThat(CorpusKey.bodyHash("abc")).isEqualTo("a9993e364706816a");
        // UTF-8, as Node hashes strings
        assertThat(CorpusKey.bodyHash("é")).isEqualTo("bf15be717ac1b080");
    }

    @Test
    void testSha1() {
        // Checked against Python's hashlib, around the 55/56 byte padding boundary and over
        // several blocks
        assertThat(Sha1.hex("y".repeat(55))).isEqualTo("f3c8b47e97bc2a23d9870c16d129390bf78225bb");
        assertThat(Sha1.hex("y".repeat(56))).isEqualTo("8902d391f35bbaf08469aee6ab763e4048f3f6ba");
        assertThat(Sha1.hex("x".repeat(1000))).isEqualTo("c3efa690fa3fdd2e2526853eed670538ea127638");
    }

    @Test
    void testToMatcher_matchesTheBodyHash() {
        final RequestMatcher matcher = CorpusKey.parse("POST /api/x #a9993e364706816a").toMatcher();

        assertThat(matcher.matches(new RecordedRequest("POST", "/x", null, "abc"))).isTrue();
        assertThat(matcher.matches(new RecordedRequest("POST", "/x", null, "abd"))).isFalse();
        assertThat(matcher.matches(new RecordedRequest("POST", "/x", null, null))).isFalse();
        assertThat(matcher.describe()).isEqualTo("POST /x [no query] [body #a9993e364706816a]");
        // Ignoring the body, e.g. for a body with an id the client generates
        assertThat(CorpusKey.parse("POST /api/x #a9993e364706816a").toMatcherIgnoringBody()
                .matches(new RecordedRequest("POST", "/x", null, "abd"))).isTrue();
        // No body hashes as an empty one
        assertThat(CorpusKey.parse("GET /api/x #da39a3ee5e6b4b0d").toMatcher()
                .matches(new RecordedRequest("GET", "/x", null, null))).isTrue();
    }

    @Test
    void testToMatcher() {
        final RequestMatcher withQuery = CorpusKey.parse("GET /api/node/v1/info?b=2&a=1").toMatcher();
        assertThat(withQuery.matches(new RecordedRequest("GET", "/node/v1/info", "a=1&b=2", null))).isTrue();
        assertThat(withQuery.matches(new RecordedRequest("GET", "/node/v1/info", null, null))).isFalse();

        // A key without a query only matches requests without one, as in the corpus
        final RequestMatcher noQuery = CorpusKey.parse("GET /api/node/v1/info").toMatcher();
        assertThat(noQuery.matches(new RecordedRequest("GET", "/node/v1/info", null, null))).isTrue();
        assertThat(noQuery.matches(new RecordedRequest("GET", "/node/v1/info", "a=1", null))).isFalse();
    }
}
