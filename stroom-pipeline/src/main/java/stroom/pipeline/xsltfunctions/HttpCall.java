/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.pipeline.xsltfunctions;

import stroom.pipeline.errorhandler.ProcessException;
import stroom.util.io.StreamUtil;
import stroom.util.jersey.HttpClientCache;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpPost;
import org.apache.hc.core5.http.ClassicHttpResponse;
import org.apache.hc.core5.http.ContentType;
import org.apache.hc.core5.http.Header;
import org.apache.hc.core5.http.HttpEntity;
import org.apache.hc.core5.http.io.HttpClientResponseHandler;
import org.apache.hc.core5.http.io.entity.StringEntity;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

class HttpCall extends StroomExtensionFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpCall.class);

    public static final String FUNCTION_NAME = "http-call";
    private static final AttributesImpl EMPTY_ATTS = new AttributesImpl();
    private static final String URI = "stroom-http";
    private static final String HEADER_DELIMITER = "\n";
    private static final String HEADER_KV_DELIMITER = ":";

    private final CommonHttpClient commonHttpClient;

    @Inject
    HttpCall(final HttpClientCache httpClientCache) {
        commonHttpClient = new CommonHttpClient(httpClientCache);
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments)
            throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();

        final String url = getOptionalString(arguments, 0).orElse("");
        final String headers = getOptionalString(arguments, 1).orElse("");
        final String mediaType = getOptionalString(arguments, 2).orElse("application/json; charset=utf-8");
        final String data = getOptionalString(arguments, 3).orElse("");
        final String clientConfigStr = getOptionalString(arguments, 4).orElse("");

        if (url.isEmpty()) {
            log(context, Severity.WARNING, "No URL specified for HTTP call", null);

        } else {
            try {
                final HttpClient httpClient = commonHttpClient.createClient(clientConfigStr);
                sequence = execute(url, headers, mediaType, data, httpClient, response ->
                        createSequence(context, response));

            } catch (final Exception e) {
                final String msg = buildErrorMessage(e);
                LOGGER.trace(msg, e);
                log(context, Severity.ERROR, msg, e);
                try {
                    sequence = createError(context, msg);
                } catch (final SAXException ex) {
                    LOGGER.trace(msg, e);
                    log(context, Severity.ERROR, msg, e);
                }
            }
        }

        return sequence;
    }

    private String buildErrorMessage(final Throwable t) {
        // Config contains passwords so mask their values
        final String cleanedErrorMsg = t.getMessage()
                .replaceAll("(\"[^\"]+Password\"\\s*:\\s*)\"[^\"]+\"",
                        "$1\"XXXXXX\"");

        return LogUtil.message(
                "Error calling XSLT function {}(): {}", FUNCTION_NAME, cleanedErrorMsg);
    }

    <T> T execute(final String url,
                  final String headers,
                  final String mediaType,
                  final String data,
                  final HttpClient httpClient,
                  final HttpClientResponseHandler<T> responseHandler) {
        LOGGER.debug(() -> "Creating request builder");
        final HttpPost httpPost = new HttpPost(url);

        if (data != null && !data.isEmpty()) {
            final ContentType contentType = ContentType.create(mediaType);
            httpPost.setEntity(new StringEntity(data, contentType));
        }

        if (headers != null && !headers.isEmpty()) {
            final String[] parts = headers.split(HEADER_DELIMITER);
            for (final String part : parts) {
                final int index = part.indexOf(HEADER_KV_DELIMITER);
                if (index > 0) {
                    final String key = part.substring(0, index).trim();
                    final String value = part.substring(index + HEADER_KV_DELIMITER.length()).trim();
                    httpPost.setHeader(key, value);
                }
            }
        }

        try {
            return httpClient.execute(httpPost, responseHandler);
        } catch (final IOException e) {
            throw ProcessException.create(LogUtil.message(
                    "Error sending request to \"{}\": {}", url, e.getMessage()), e);
        }
    }

    private Sequence createSequence(final XPathContext context, final ClassicHttpResponse response) {
        try {
            final Configuration configuration = context.getConfiguration();
            final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
            final Builder builder = new TinyBuilder(pipe);

            final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
            contentHandler.setPipelineConfiguration(pipe);
            contentHandler.setReceiver(builder);

            contentHandler.startDocument();
            startElement(contentHandler, "response");
            data(contentHandler, "successful", String.valueOf(response.getCode() == 200));
            data(contentHandler, "code", String.valueOf(response.getCode()));
            data(contentHandler, "message", response.getReasonPhrase());

            // Write headers.
            if (response.getHeaders() != null && response.getHeaders().length > 0) {
                startElement(contentHandler, "headers");
                for (final Header header : response.getHeaders()) {
                    startElement(contentHandler, "header");
                    data(contentHandler, "key", header.getName());
                    data(contentHandler, "value", header.getValue());
                    endElement(contentHandler, "header");
                }
                endElement(contentHandler, "headers");
            }

            try {
                final HttpEntity entity = response.getEntity();
                if (entity != null) {
                    try (final InputStream inputStream = entity.getContent()) {
                        final String string = StreamUtil.streamToString(inputStream);
                        data(contentHandler, "body", string);
                    }
                }
            } catch (final NullPointerException | IOException e) {
                LOGGER.debug(e::getMessage, e);
            }

            endElement(contentHandler, "response");
            contentHandler.endDocument();

            final Sequence sequence = builder.getCurrentRoot();

            // Reset the builder, detaching it from the constructed
            // document.
            builder.reset();

            return sequence;
        } catch (final SAXException e) {
            throw new RuntimeException(e.getMessage(), e);
        }
    }

    private Sequence createError(final XPathContext context, final String message) throws SAXException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        contentHandler.startDocument();
        data(contentHandler, "error", message);
        contentHandler.endDocument();

        final Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed
        // document.
        builder.reset();

        return sequence;
    }

    private void data(final ReceivingContentHandler contentHandler, final String name, final String value)
            throws SAXException {
        if (value != null) {
            startElement(contentHandler, name);
            characters(contentHandler, value);
            endElement(contentHandler, name);
        }
    }

    private void startElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.startElement(URI, name, name, EMPTY_ATTS);
    }

    private void endElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.endElement(URI, name, name);
    }

    private void characters(final ReceivingContentHandler contentHandler, final String characters) throws SAXException {
        final char[] chars = characters.toCharArray();
        contentHandler.characters(chars, 0, chars.length);
    }

    private Optional<String> getOptionalString(final Sequence[] arguments, final int index) throws XPathException {
        if (arguments.length > index) {
            final Sequence sequence = arguments[index];
            if (sequence != null) {
                final Item item = sequence.iterate().next();
                if (item != null) {
                    return Optional.ofNullable(item.getStringValue());
                }
            }
        }
        return Optional.empty();
    }
}
