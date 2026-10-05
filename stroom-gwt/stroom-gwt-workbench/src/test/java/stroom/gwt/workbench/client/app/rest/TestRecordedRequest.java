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

class TestRecordedRequest {

    @Test
    void testFromUrl() {
        final RecordedRequest request = RecordedRequest.fromUrl(
                "post", "http://localhost:6008/api/explorer/v2/find", "{}");

        assertThat(request.getMethod()).isEqualTo("POST");
        assertThat(request.getPath()).isEqualTo("/explorer/v2/find");
        assertThat(request.getQuery()).isNull();
        assertThat(request.getBody()).isEqualTo("{}");
        assertThat(request.describe()).isEqualTo("POST /explorer/v2/find");
    }

    @Test
    void testFromUrl_query() {
        final RecordedRequest request = RecordedRequest.fromUrl(
                "GET", "https://host/api/meta/v1/info?id=1&x=2", null);

        assertThat(request.getPath()).isEqualTo("/meta/v1/info");
        assertThat(request.getQuery()).isEqualTo("id=1&x=2");
        assertThat(request.describe()).isEqualTo("GET /meta/v1/info?id=1&x=2");
    }

    @Test
    void testFromUrl_notApi() {
        // Only the /api service root is removed
        assertThat(RecordedRequest.fromUrl("GET", "http://host/apix/a", null).getPath()).isEqualTo("/apix/a");
        assertThat(RecordedRequest.fromUrl("GET", "http://host/other/a", null).getPath()).isEqualTo("/other/a");
        assertThat(RecordedRequest.fromUrl("GET", "http://host", null).getPath()).isEqualTo("/");
    }

    @Test
    void testFromUrl_relative() {
        assertThat(RecordedRequest.fromUrl("GET", "/api/sessionInfo/v1", null).getPath())
                .isEqualTo("/sessionInfo/v1");
    }
}
