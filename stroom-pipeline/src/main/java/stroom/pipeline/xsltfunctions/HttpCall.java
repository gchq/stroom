package stroom.pipeline.xsltfunctions;

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
import okhttp3.MediaType;
import okhttp3.OkHttpClient;
import okhttp3.Request;
import okhttp3.RequestBody;
import okhttp3.Response;
import org.xml.sax.SAXException;
import org.xml.sax.helpers.AttributesImpl;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import javax.inject.Inject;
import java.io.IOException;
import java.util.List;
import java.util.Map.Entry;
import java.util.Optional;

class HttpCall extends StroomExtensionFunctionCall {
    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(HttpCall.class);

    private static final AttributesImpl EMPTY_ATTS = new AttributesImpl();
    private static final String URI = "stroom-http";

    private final HttpClientCache httpClientCache;

    @Inject
    HttpCall(final HttpClientCache httpClientCache) {
        this.httpClientCache = httpClientCache;
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();

        final String url = getOptionalString(arguments, 0).orElse("");
        final String headers = getOptionalString(arguments, 1).orElse("");
        final String mediaType = getOptionalString(arguments, 2).orElse("application/json; charset=utf-8");
        final String data = getOptionalString(arguments, 3).orElse("");
        final String clientConfig = getOptionalString(arguments, 4).orElse("");

        if (url.isEmpty()) {
            log(context, Severity.WARNING, "No URL specified for HTTP call", null);

        } else {
            try {
                try {
                    try (final Response response = execute(url, headers, mediaType, data, clientConfig)) {
                        sequence = createSequence(context, response);
                    }
                } catch (final IOException e) {
                    LOGGER.trace(e::getMessage, e);
                    sequence = createError(context, "Could not make request: " + e.getMessage());
                }
            } catch (final SAXException e2) {
                LOGGER.trace(e2::getMessage, e2);
                log(context, Severity.ERROR, e2.getMessage(), e2);
            }
        }

        return sequence;
    }

    Response execute(final String url, final String headers, final String mediaType, final String data, final String clientConfig) throws IOException {
        LOGGER.debug(() -> "Creating client");
        final OkHttpClient client = httpClientCache.get(clientConfig);

        LOGGER.debug(() -> "Creating request builder");
        Request.Builder builder = new Request.Builder().url(url);

        if (data != null && data.length() > 0) {
            final RequestBody body = RequestBody.create(data, MediaType.parse(mediaType));
            builder = builder.post(body);
        }

        if (headers != null && headers.length() > 0) {
            final String[] parts = headers.split("\n");
            for (final String part : parts) {
                int index = part.indexOf(": ");
                if (index > 0) {
                    final String key = part.substring(0, index);
                    final String value = part.substring(index + 2);
                    builder = builder.addHeader(key, value);
                }
            }
        }

        final Request request = builder.build();

        return client.newCall(request).execute();
    }

    private Sequence createSequence(final XPathContext context, final Response response) throws SAXException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        contentHandler.startDocument();
        startElement(contentHandler, "response");
        data(contentHandler, "successful", String.valueOf(response.isSuccessful()));
        data(contentHandler, "code", String.valueOf(response.code()));
        data(contentHandler, "message", response.message());

        // Write headers.
        if (response.headers() != null && response.headers().size() > 0) {
            startElement(contentHandler, "headers");
            for (final Entry<String, List<String>> entry : response.headers().toMultimap().entrySet()) {
                final String key = entry.getKey();
                for (final String value : entry.getValue()) {
                    startElement(contentHandler, "header");
                    data(contentHandler, "key", key);
                    data(contentHandler, "value", value);
                    endElement(contentHandler, "header");
                }
            }
            endElement(contentHandler, "headers");
        }

        try {
            if (response.body() != null) {
                data(contentHandler, "body", response.body().string());
            }
        } catch (final NullPointerException | IOException e) {
            LOGGER.debug(e::getMessage, e);
        }

        endElement(contentHandler, "response");
        contentHandler.endDocument();

        Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed
        // document.
        builder.reset();

        return sequence;
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

        Sequence sequence = builder.getCurrentRoot();

        // Reset the builder, detaching it from the constructed
        // document.
        builder.reset();

        return sequence;
    }

    private void data(final ReceivingContentHandler contentHandler, final String name, final String value) throws SAXException {
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
