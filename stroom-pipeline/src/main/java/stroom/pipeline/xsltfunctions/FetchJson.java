package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.eclipse.jetty.http.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.xml.sax.SAXException;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

class FetchJson extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchJson.class);

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();

        final String urlString = getSafeString(functionName, context, arguments, 0);
        try {
            final URL url = new URL(urlString);
            final HttpURLConnection yc = (HttpURLConnection) url.openConnection();

            switch (yc.getResponseCode()) {
                case HttpStatus.OK_200: {
                    try (BufferedReader in = new BufferedReader(new InputStreamReader(
                            yc.getInputStream()))) {
                        final String json = in.lines().reduce("", String::concat);
                        sequence = JsonToXml.jsonToXml(context, json);
                        LOGGER.trace(String.format("Found Data %s: %s", urlString, json));
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
        } catch (final SAXException | IOException | RuntimeException e) {
            LOGGER.warn("Could not make request to Annotations Service: " + e.getLocalizedMessage());
        }

        return sequence;
    }
}
