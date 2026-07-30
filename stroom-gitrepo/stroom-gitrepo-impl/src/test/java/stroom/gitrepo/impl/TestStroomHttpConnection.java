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

package stroom.gitrepo.impl;

import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.HttpHost;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.apache.hc.core5.http.message.BasicClassicHttpResponse;
import org.apache.hc.core5.http.protocol.HttpContext;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.io.IOException;
import java.io.OutputStream;
import java.net.ProtocolException;
import java.net.URL;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestStroomHttpConnection {

    private static final String URL_STRING = "https://git.example.com/repo.git/info/refs";

    @Test
    void bodyWrittenToTheOutputStreamIsSentAsTheRequestEntity() throws Exception {
        // JGit hands over a body by writing to an output stream and only later asking for a response, so
        // the bridge has to hold that body until the request is actually sent. If this is wrong a push
        // silently sends nothing.
        final HttpClient httpClient = mockClient(response(200, "ok"));
        final StroomHttpConnection connection = new StroomHttpConnection(new URL(URL_STRING), httpClient);

        connection.setRequestMethod("POST");
        try (final OutputStream outputStream = connection.getOutputStream()) {
            outputStream.write("want abc123".getBytes(StandardCharsets.UTF_8));
        }

        assertThat(connection.getResponseCode()).isEqualTo(200);

        final HttpEntity sent = capturedRequest(httpClient).getEntity();
        assertThat(sent).isNotNull();
        assertThat(new String(sent.getContent().readAllBytes(), StandardCharsets.UTF_8))
                .isEqualTo("want abc123");
        // Repeatable, because the client replays the request after an authentication challenge - which is
        // the normal case for Git over HTTP, not an edge case.
        assertThat(sent.isRepeatable()).isTrue();
    }

    @Test
    void noRequestIsSentUntilSomethingNeedsTheResponse() throws Exception {
        final HttpClient httpClient = mockClient(response(200, "ok"));
        final StroomHttpConnection connection = new StroomHttpConnection(new URL(URL_STRING), httpClient);

        connection.setRequestProperty("Accept", "application/x-git-upload-pack-advertisement");

        Mockito.verifyNoInteractions(httpClient);

        connection.connect();

        Mockito.verify(httpClient).executeOpen(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void theRequestIsSentOnlyOnceHoweverOftenTheResponseIsRead() throws Exception {
        final HttpClient httpClient = mockClient(response(200, "ok"));
        final StroomHttpConnection connection = new StroomHttpConnection(new URL(URL_STRING), httpClient);

        connection.getResponseCode();
        connection.getResponseMessage();
        connection.getInputStream();

        Mockito.verify(httpClient, Mockito.times(1))
                .executeOpen(Mockito.any(), Mockito.any(), Mockito.any());
    }

    @Test
    void headersSurviveAChangeOfMethod() throws Exception {
        // Changing the method rebuilds the underlying request, and nothing in JGit's interface promises the
        // method is set before the headers.
        final HttpClient httpClient = mockClient(response(200, "ok"));
        final StroomHttpConnection connection = new StroomHttpConnection(new URL(URL_STRING), httpClient);

        connection.setRequestProperty("Accept", "application/x-git-upload-pack-result");
        connection.setRequestMethod("POST");
        connection.connect();

        final ClassicHttpRequest sent = capturedRequest(httpClient);
        assertThat(sent.getMethod()).isEqualTo("POST");
        assertThat(sent.getFirstHeader("Accept").getValue())
                .isEqualTo("application/x-git-upload-pack-result");
    }

    @Test
    void anUnsupportedMethodIsRejected() throws Exception {
        final StroomHttpConnection connection = new StroomHttpConnection(
                new URL(URL_STRING), mockClient(response(200, "ok")));

        assertThatThrownBy(() -> connection.setRequestMethod("DELETE"))
                .isInstanceOf(ProtocolException.class);
    }

    @Test
    void theTargetHostCarriesTheSchemeSoTlsIsUsedForHttps() throws Exception {
        final HttpClient httpClient = mockClient(response(200, "ok"));
        new StroomHttpConnection(new URL(URL_STRING), httpClient).connect();

        final ArgumentCaptor<HttpHost> hostCaptor = ArgumentCaptor.forClass(HttpHost.class);
        Mockito.verify(httpClient).executeOpen(hostCaptor.capture(), Mockito.any(), Mockito.any());
        assertThat(hostCaptor.getValue().getSchemeName()).isEqualTo("https");
        assertThat(hostCaptor.getValue().getHostName()).isEqualTo("git.example.com");
    }

    @Test
    void responseHeadersAreReadable() throws Exception {
        final ClassicHttpResponse httpResponse = response(200, "ok");
        httpResponse.addHeader("Content-Type", "application/x-git-upload-pack-advertisement");
        final HttpClient httpClient = mockClient(httpResponse);

        final StroomHttpConnection connection = new StroomHttpConnection(new URL(URL_STRING), httpClient);
        connection.connect();

        assertThat(connection.getHeaderField("Content-Type"))
                .isEqualTo("application/x-git-upload-pack-advertisement");
        assertThat(connection.getHeaderFields()).containsKey("Content-Type");
    }

    private static ClassicHttpResponse response(final int code, final String body) {
        final BasicClassicHttpResponse response = new BasicClassicHttpResponse(code, "OK");
        response.setEntity(new StringEntity(body, StandardCharsets.UTF_8));
        return response;
    }

    private static HttpClient mockClient(final ClassicHttpResponse response) throws IOException {
        final HttpClient httpClient = Mockito.mock(HttpClient.class);
        Mockito.when(httpClient.executeOpen(
                        Mockito.any(HttpHost.class),
                        Mockito.any(ClassicHttpRequest.class),
                        Mockito.any(HttpContext.class)))
                .thenReturn(response);
        return httpClient;
    }

    private static ClassicHttpRequest capturedRequest(final HttpClient httpClient) throws IOException {
        final ArgumentCaptor<ClassicHttpRequest> captor = ArgumentCaptor.forClass(ClassicHttpRequest.class);
        Mockito.verify(httpClient).executeOpen(Mockito.any(), captor.capture(), Mockito.any());
        return captor.getValue();
    }
}
