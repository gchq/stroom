package stroom.pipeline.xsltfunctions;

import org.eclipse.jetty.http.HttpStatus;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;
import stroom.properties.api.StroomPropertyService;
import stroom.node.shared.ClientProperties;

import javax.inject.Inject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Map;

class FetchJson extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchJson.class);

    private final Map<String, String> namedUrls;

    @Inject
    FetchJson(final StroomPropertyService propertyService) {
        namedUrls = propertyService.getLookupTable(ClientProperties.URL_LIST, ClientProperties.URL_BASE);
    }

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();

        final String urlName = getSafeString(functionName, context, arguments, 0);
        final String path = getSafeString(functionName, context, arguments, 1);

        LOGGER.trace(String.format("Looking Up JSON at %s - %s", urlName, path));

        try {
            final String namedUrl = namedUrls.get(urlName);

            if (null != namedUrl) {
                final String completeUrl = namedUrl + path;
                final URL ann = new URL(completeUrl);
                final HttpURLConnection yc = (HttpURLConnection) ann.openConnection();

                switch (yc.getResponseCode()) {
                    case HttpStatus.OK_200: {
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                                yc.getInputStream()))) {
                            final String json = in.lines().reduce("", String::concat);

                            sequence = JsonToXml.jsonToXml(context, json);
                            LOGGER.trace(String.format("Found Data %s: %s", completeUrl, json));
                        }
                        break;
                    }
                    case HttpStatus.NOT_FOUND_404:
                        // this is an expected failure condition
                        break;
                    default:
                        LOGGER.warn("Could not make request to Annotations Service: " + yc.getResponseCode());
                        break;
                }
            }
        } catch (final SAXException | IOException | RuntimeException e) {
            LOGGER.warn("Could not make request to Annotations Service: " + e.getLocalizedMessage());
        }

        return sequence;
    }
}
