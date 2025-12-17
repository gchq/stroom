package stroom.langchain.impl;

import dev.langchain4j.http.client.HttpClient;
import dev.langchain4j.http.client.HttpRequest;
import dev.langchain4j.http.client.SuccessfulHttpResponse;
import dev.langchain4j.http.client.sse.ServerSentEventListener;
import dev.langchain4j.http.client.sse.ServerSentEventParser;
import org.apache.hc.client5.http.classic.methods.HttpDelete;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.apache.hc.client5.http.classic.methods.HttpHead;
import org.apache.hc.client5.http.classic.methods.HttpOptions;
import org.apache.hc.client5.http.classic.methods.HttpPatch;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.client5.http.classic.methods.HttpPut;
import org.apache.hc.core5.http.ClassicHttpRequest;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.ParseException;
import org.apache.hc.core5.http.io.entity.EntityUtils;
import org.apache.hc.core5.http.io.entity.StringEntity;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

public class ApacheHttpClient implements HttpClient {

    private final org.apache.hc.client5.http.classic.HttpClient httpClient;

    public ApacheHttpClient(final org.apache.hc.client5.http.classic.HttpClient httpClient) {
        this.httpClient = httpClient;
    }

    @Override
    public SuccessfulHttpResponse execute(final HttpRequest request) {
        try {
            final ClassicHttpRequest apacheRequest = createApacheRequest(request);
            return httpClient.execute(apacheRequest, this::convertResponse);
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void execute(final HttpRequest request,
                        final ServerSentEventParser parser,
                        final ServerSentEventListener listener) {
        final ClassicHttpRequest apacheRequest = createApacheRequest(request);
        try {
            httpClient.execute(apacheRequest, response -> {
                handleServerSentEvents(response, parser, listener);
                return null;
            });
        } catch (final IOException e) {
            listener.onError(e);
        }
    }

    private void handleServerSentEvents(final ClassicHttpResponse response,
                                        final ServerSentEventParser parser,
                                        final ServerSentEventListener listener) throws IOException {
        parser.parse(response.getEntity().getContent(), listener);
    }

    private ClassicHttpRequest createApacheRequest(final HttpRequest request) {
        final String method = request.method().name();
        final String url = request.url();

        final ClassicHttpRequest apacheRequest = switch (method.toUpperCase(Locale.ROOT)) {
            case "GET" -> new HttpGet(url);
            case "POST" -> {
                final HttpPost post = new HttpPost(url);
                if (request.body() != null) {
                    post.setEntity(new StringEntity(request.body(), ContentType.APPLICATION_JSON));
                }
                yield post;
            }
            case "PUT" -> {
                final HttpPut put = new HttpPut(url);
                if (request.body() != null) {
                    put.setEntity(new StringEntity(request.body(), ContentType.APPLICATION_JSON));
                }
                yield put;
            }
            case "DELETE" -> new HttpDelete(url);
            case "PATCH" -> {
                final HttpPatch patch = new HttpPatch(url);
                if (request.body() != null) {
                    patch.setEntity(new StringEntity(request.body(), ContentType.APPLICATION_JSON));
                }
                yield patch;
            }
            case "HEAD" -> new HttpHead(url);
            case "OPTIONS" -> new HttpOptions(url);
            default -> throw new IllegalArgumentException("Unsupported HTTP method: " + method);
        };

        // Add headers
        if (request.headers() != null) {
            request.headers().forEach((key, values) ->
                    values.forEach(value ->
                            apacheRequest.addHeader(key, value)));
        }

        return apacheRequest;
    }

    private SuccessfulHttpResponse convertResponse(final ClassicHttpResponse response) {
        try {
            final int statusCode = response.getCode();

            final Map<String, List<String>> headers = new HashMap<>();
            for (final Header header : response.getHeaders()) {
                headers.computeIfAbsent(header.getName(), k -> new ArrayList<>()).add(header.getValue());
            }

            String body = null;
            if (response.getEntity() != null) {
                body = EntityUtils.toString(response.getEntity());
            }

            return SuccessfulHttpResponse.builder()
                    .statusCode(statusCode)
                    .headers(headers)
                    .body(body)
                    .build();
        } catch (final IOException e) {
            throw new UncheckedIOException(e);
        } catch (final ParseException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }
}
