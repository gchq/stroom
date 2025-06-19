package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.shared.Severity;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Item;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.util.Optional;

class FetchJson extends StroomExtensionFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FetchJson.class);

    private final CommonHttpClient commonHttpClient;

    @Inject
    FetchJson(final HttpClientCache httpClientCache) {
        commonHttpClient = new CommonHttpClient(httpClientCache);
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context,
                            final Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();

        final String url = getOptionalString(arguments, 0).orElse("");
        final String clientConfigStr = getOptionalString(arguments, 1).orElse("");

        if (url.isEmpty()) {
            log(context, Severity.WARNING, "No URL specified for HTTP call", null);

        } else {
            try {
                final HttpClient httpClient = commonHttpClient.createClient(clientConfigStr);
                final HttpGet httpGet = new HttpGet(url);
                sequence = httpClient.execute(httpGet, response -> {
                    try {
                        switch (response.getCode()) {
                            case 200: { // OK
                                try (final BufferedReader in = new BufferedReader(new InputStreamReader(
                                        response.getEntity().getContent()))) {
                                    final String json = in.lines().reduce("", String::concat);
                                    final Sequence<?> s = JsonToXml.jsonToXml(context, json);
                                    LOGGER.trace(String.format("Found Data %s: %s", url, json));
                                    return s;
                                }
                            }
                            case 404: // NOT_FOUND
                                // this is an expected failure condition
                                break;
                            default:
                                throw new RuntimeException("Could not make request to Annotations Service: " +
                                                           response.getCode());
                        }
                    } catch (final SAXException | IOException e) {
                        throw new RuntimeException("Could not make request to Annotations Service: " +
                                                   e.getLocalizedMessage());
                    }

                    return EmptyAtomicSequence.getInstance();
                });

            } catch (final IOException | RuntimeException e) {
                LOGGER.warn(() -> "Could not make request to Annotations Service: " + e.getLocalizedMessage());
            }
        }

        return sequence;
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
