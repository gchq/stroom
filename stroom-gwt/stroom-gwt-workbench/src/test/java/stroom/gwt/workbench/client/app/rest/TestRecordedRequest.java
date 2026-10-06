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

class TestRecordedRequest {

    private static final String ROOT = "http://localhost:6008/api/";

    @Test
    void testFromUrl() {
        final RecordedRequest request = RecordedRequest.fromUrl(
                "post", "http://localhost:6008/api/explorer/v2/find", "{\"a\":1}", ROOT);

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/explorer/v2/find");
        assertThat(request.getQuery()).isNull();
        assertThat(request.getBody()).isEqualTo("{\"a\":1}");
        assertThat(request.describe()).isEqualTo("POST /explorer/v2/find");
        assertThat(request.describeWithBody()).isEqualTo("POST /explorer/v2/find {\"a\":1}");
    }

    @Test
    void testFromUrl_withQuery() {
        final RecordedRequest request = RecordedRequest.fromUrl(
                "GET", "http://localhost:6008/api/node/v1/info?node=node1a&x=1", null, ROOT);

        assertThat(request.getPath()).isEqualTo("/node/v1/info");
        assertThat(request.getQuery()).isEqualTo("node=node1a&x=1");
        assertThat(request.describe()).isEqualTo("GET /node/v1/info?node=node1a&x=1");
        assertThat(request.describeWithBody()).isEqualTo(request.describe());
    }

    @Test
    void testFromUrl_rootWithContextPath() {
        // The root is whatever RestFactoryImpl set, not a literal "/api"
        final RecordedRequest request = RecordedRequest.fromUrl(
                "GET", "https://host/stroom/api/sessionInfo/v1", null, "https://host/stroom/api/");

        assertThat(request.getPath()).isEqualTo("/sessionInfo/v1");
    }

    @Test
    void testFromUrl_pathOutsideRootIsKept() {
        final RecordedRequest request = RecordedRequest.fromUrl(
                "GET", "http://localhost:6008/apiary/x", null, ROOT);

        assertThat(request.getPath()).isEqualTo("/apiary/x");
    }

    @Test
    void testFromUrl_noRoot() {
        final RecordedRequest request = RecordedRequest.fromUrl(
                "GET", "http://localhost:6008/api/sessionInfo/v1", null, null);

        assertThat(request.getPath()).isEqualTo("/api/sessionInfo/v1");
    }

    @Test
    void testFromUrl_relativeUrl() {
        final RecordedRequest request = RecordedRequest.fromUrl("GET", "/api/sessionInfo/v1", null, ROOT);

        assertThat(request.getPath()).isEqualTo("/sessionInfo/v1");
    }

    @Test
    void testFromUrl_noPath() {
        assertThat(RecordedRequest.fromUrl("GET", "http://localhost:6008", null, ROOT).getPath())
                .isEqualTo("/");
    }

    @Test
    void testRootPathOf() {
        assertThat(RecordedRequest.rootPathOf("http://localhost:6008/api/")).isEqualTo("/api");
        assertThat(RecordedRequest.rootPathOf("http://localhost:6008/")).isEmpty();
        assertThat(RecordedRequest.rootPathOf("/api")).isEqualTo("/api");
        assertThat(RecordedRequest.rootPathOf(null)).isEmpty();
    }

    @Test
    void testParse() {
        final RecordedRequest request = RecordedRequest.parse("PUT /jobNode/v1/11/schedule {\"a\": \"0 0 * * ?\"}");

        assertThat(request.getMethod()).isEqualTo("PUT");
        assertThat(request.getPath()).isEqualTo("/jobNode/v1/11/schedule");
        assertThat(request.getQuery()).isNull();
        assertThat(request.getBody()).isEqualTo("{\"a\": \"0 0 * * ?\"}");
    }

    @Test
    void testParseQueryWithoutBody() {
        final RecordedRequest request = RecordedRequest.parse("GET /jobNode/v1/info?jobName=Data%20Retention&node=n1");

        assertThat(request.getMethod()).isEqualTo("GET");
        assertThat(request.getPath()).isEqualTo("/jobNode/v1/info");
        assertThat(request.getQuery()).isEqualTo("jobName=Data%20Retention&node=n1");
        assertThat(request.getBody()).isNull();
    }

    @Test
    void testParseIsTheReverseOfDescribeWithBody() {
        final RecordedRequest request = new RecordedRequest("POST", "/a/b", "x=1", "[1, 2]");

        final RecordedRequest parsed = RecordedRequest.parse(request.describeWithBody());

        assertThat(parsed.describeWithBody()).isEqualTo(request.describeWithBody());
        assertThat(parsed.getBody()).isEqualTo("[1, 2]");
    }

    @Test
    void testParseRefusesText() {
        assertThatThrownBy(() -> RecordedRequest.parse("GET"))
                .isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RecordedRequest.parse(""))
                .isInstanceOf(IllegalArgumentException.class);
    }
}
