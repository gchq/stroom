package stroom.pipeline.xsltfunctions;

import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;

import jakarta.inject.Inject;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.apache.hc.client5.http.classic.HttpClient;
import org.apache.hc.client5.http.classic.methods.HttpGet;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;

class FetchJson extends StroomExtensionFunctionCall {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(FetchJson.class);

    private final HttpClientCache httpClientCache;

    @Inject
    FetchJson(final HttpClientCache httpClientCache) {
        this.httpClientCache = httpClientCache;
    }

    @Override
    protected Sequence call(final String functionName, XPathContext context,
                            final Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();
        final String urlString = getSafeString(functionName, context, arguments, 0);
        try {
            final HttpClient httpClient = httpClientCache.get("");
            final HttpGet httpGet = new HttpGet(urlString);
            sequence = httpClient.execute(httpGet, response -> {
                try {
                    switch (response.getCode()) {
                        case 200: { // OK
                            try (final BufferedReader in = new BufferedReader(new InputStreamReader(
                                    response.getEntity().getContent()))) {
                                final String json = in.lines().reduce("", String::concat);
                                final Sequence<?> s = JsonToXml.jsonToXml(context, json);
                                LOGGER.trace(String.format("Found Data %s: %s", urlString, json));
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

        return sequence;
    }
}
