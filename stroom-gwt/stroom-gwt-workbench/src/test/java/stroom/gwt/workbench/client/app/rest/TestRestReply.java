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

class TestRestReply {

    @Test
    void testJson() {
        final RestReply reply = RestReply.json("{}");

        assertThat(reply.getStatus()).isEqualTo(200);
        assertThat(reply.getBody()).isEqualTo("{}");
        assertThat(reply.getContentType()).isEqualTo(RestReply.JSON);
        assertThat(reply.getDelayMillis()).isZero();
    }

    @Test
    void testError() {
        final RestReply reply = RestReply.error(500, "Bad \"thing\"\n\u0001");

        assertThat(reply.getStatus()).isEqualTo(500);
        assertThat(reply.getBody()).isEqualTo("{\"code\":500,\"message\":\"Bad \\\"thing\\\"\\n\\u0001\"}");
    }

    @Test
    void testNoContent() {
        assertThat(RestReply.noContent().getStatus()).isEqualTo(204);
        assertThat(RestReply.noContent().getBody()).isEmpty();
    }

    @Test
    void testDelayed() {
        final RestReply reply = RestReply.json("{}");
        final RestReply delayed = reply.delayed(500);

        assertThat(delayed.getDelayMillis()).isEqualTo(500);
        assertThat(delayed.getBody()).isEqualTo("{}");
        // The original is unchanged
        assertThat(reply.getDelayMillis()).isZero();
    }

    @Test
    void testQuote() {
        assertThat(RestReply.quote(null)).isEqualTo("null");
        assertThat(RestReply.quote("a\\b")).isEqualTo("\"a\\\\b\"");
    }
}
