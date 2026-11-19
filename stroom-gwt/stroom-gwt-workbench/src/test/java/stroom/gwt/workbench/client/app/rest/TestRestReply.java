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

import com.google.gwt.http.client.Header;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestRestReply {

    @Test
    void testJson() {
        final RestReply reply = RestReply.json("{\"a\":1}");

        assertThat(reply.getKind()).isEqualTo(RestReply.Kind.RESPONSE);
        assertThat(reply.getStatus()).isEqualTo(200);
        assertThat(reply.getBody()).isEqualTo("{\"a\":1}");
        assertThat(reply.getContentType()).isEqualTo(RestReply.JSON);
        assertThat(reply.getDelayMillis()).isZero();
        assertThat(reply.getHeaders()).isEmpty();
    }

    @Test
    void testJson_withStatus() {
        final RestReply reply = RestReply.json(409, "{\"message\":\"Conflict\"}");

        assertThat(reply.getStatus()).isEqualTo(409);
        assertThat(reply.getContentType()).isEqualTo(RestReply.JSON);
    }

    @Test
    void testText() {
        assertThat(RestReply.text("hello").getContentType()).isEqualTo("text/plain");
        assertThat(RestReply.text(500, "It broke").getStatus()).isEqualTo(500);
        assertThat(RestReply.text(500, "It broke").getBody()).isEqualTo("It broke");
    }

    @Test
    void testOf() {
        final RestReply reply = RestReply.of(302, "text/html", null);

        assertThat(reply.getStatus()).isEqualTo(302);
        assertThat(reply.getContentType()).isEqualTo("text/html");
        assertThat(reply.getBody()).isEmpty();
    }

    @Test
    void testError() {
        final RestReply reply = RestReply.error(500, "Line 1\nSaid \"no\"");

        assertThat(reply.getStatus()).isEqualTo(500);
        assertThat(reply.getContentType()).isEqualTo(RestReply.JSON);
        assertThat(reply.getBody()).isEqualTo("{\"code\":500,\"message\":\"Line 1\\nSaid \\\"no\\\"\"}");
        assertThat(JsonValues.parse(reply.getBody())).isNotNull();
    }

    @Test
    void testUnauthorisedIsRefused() {
        // A 401 makes RestFactoryImpl reload the page, so every way of making one must fail
        assertThatThrownBy(() -> RestReply.error(401, "Expired"))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("reload");
        assertThatThrownBy(() -> RestReply.json(401, "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestReply.text(401, "x")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestReply.of(401, RestReply.JSON, "")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testBadStatusIsRefused() {
        assertThatThrownBy(() -> RestReply.json(99, "{}")).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> RestReply.json(600, "{}")).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testNoContent() {
        assertThat(RestReply.noContent().getStatus()).isEqualTo(204);
        assertThat(RestReply.noContent().getBody()).isEmpty();
    }

    @Test
    void testDelayed() {
        final RestReply reply = RestReply.json("[]");
        final RestReply delayed = reply.delayed(500);

        assertThat(delayed.getDelayMillis()).isEqualTo(500);
        assertThat(delayed.getBody()).isEqualTo("[]");
        // Replies are immutable
        assertThat(reply.getDelayMillis()).isZero();
        assertThatThrownBy(() -> reply.delayed(-1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void testWithHeader() {
        final RestReply reply = RestReply.json("{}")
                .withHeader("Content-Disposition", "attachment; filename=a.zip")
                .withHeader("Location", "/one")
                .withHeader("location", "/two");

        assertThat(reply.getHeaders()).containsExactly(
                Map.entry("Content-Disposition", "attachment; filename=a.zip"),
                Map.entry("location", "/two"));
        assertThat(reply.getHeader("content-disposition")).isEqualTo("attachment; filename=a.zip");
        assertThat(reply.getHeader("LOCATION")).isEqualTo("/two");
        assertThat(reply.getHeader("content-type")).isEqualTo(RestReply.JSON);
        assertThat(reply.getHeader("X-Missing")).isNull();
    }

    @Test
    void testWithHeader_contentTypeReplacesTheContentType() {
        final RestReply reply = RestReply.json("{}")
                .withHeader("X-Other", "1")
                .withHeader("content-type", "text/csv")
                .delayed(5);

        assertThat(reply.getContentType()).isEqualTo("text/csv");
        assertThat(reply.getHeader("Content-Type")).isEqualTo("text/csv");
        assertThat(reply.getHeaders()).containsExactly(Map.entry("X-Other", "1"));
        assertThat(reply.getDelayMillis()).isEqualTo(5);

        // The response has one Content-Type header, the new one
        final Header[] headers = new FixtureResponse(reply).getHeaders();
        assertThat(headers).extracting(Header::getName).containsExactly("Content-Type", "X-Other");
        assertThat(headers[0].getValue()).isEqualTo("text/csv");
        assertThat(new FixtureResponse(reply).getHeadersAsString())
                .isEqualTo("Content-Type: text/csv\r\nX-Other: 1\r\n");
    }

    @Test
    void testNetworkErrorAndTimeout() {
        final RestReply networkError = RestReply.networkError("Connection refused");
        assertThat(networkError.getKind()).isEqualTo(RestReply.Kind.NETWORK_ERROR);
        assertThat(networkError.getBody()).isEqualTo("Connection refused");
        assertThat(networkError.toString()).isEqualTo("network error: Connection refused");

        final RestReply timeout = RestReply.timeout().delayed(3000);
        assertThat(timeout.getKind()).isEqualTo(RestReply.Kind.TIMEOUT);
        assertThat(timeout.getDelayMillis()).isEqualTo(3000);
    }

    @Test
    void testQuote() {
        assertThat(RestReply.quote(null)).isEqualTo("null");
        assertThat(RestReply.quote("a\\b")).isEqualTo("\"a\\\\b\"");
        assertThat(RestReply.quote("\t")).isEqualTo("\"\\u0009\"");
    }
}
