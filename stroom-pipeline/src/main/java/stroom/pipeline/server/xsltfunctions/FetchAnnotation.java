package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import org.mortbay.jetty.HttpStatus;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.node.server.StroomPropertyService;
import stroom.util.spring.StroomScope;

import javax.annotation.PostConstruct;
import javax.annotation.Resource;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;

@Component
@Scope(StroomScope.PROTOTYPE)
public class FetchAnnotation extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(FetchAnnotation.class);

    private static final String ANNOTATION_URL_PROPERTY = "stroom.url.annotations-service";

    @Resource
    private StroomPropertyService propertyService;

    private String annotationServiceUrl;

    @PostConstruct
    public void postConstruct() {
        annotationServiceUrl = propertyService.getProperty(ANNOTATION_URL_PROPERTY);
    }

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        Sequence sequence = EmptyAtomicSequence.getInstance();;

        final String annotationId = getSafeString(functionName, context, arguments, 0);
        final String annotationUrl = String.format("%ssingle/%s", annotationServiceUrl, annotationId);

        LOGGER.trace(String.format("Looking Up Annotation for ID %s at %s", annotationId, annotationUrl));

        try {
            if (null != annotationServiceUrl) {
                final URL ann = new URL(annotationUrl);
                final HttpURLConnection yc = (HttpURLConnection) ann.openConnection();

                switch (yc.getResponseCode()) {
                    case HttpStatus.ORDINAL_200_OK: {
                        try (BufferedReader in = new BufferedReader(new InputStreamReader(
                                yc.getInputStream()))) {
                            final String json = in.lines().reduce("", String::concat);

                            sequence = JsonToXml.jsonToXml(context, json);
                            LOGGER.trace(String.format("Found Annotation %s: %s", annotationId, json));
                        }
                        break;
                    }
                    case HttpStatus.ORDINAL_404_Not_Found:
                        // this is an expected failure condition
                        break;
                    default:
                        LOGGER.warn("Could not make request to Annotations Service: " + yc.getResponseCode());
                        break;
                }
            }
        } catch (Exception e) {
            LOGGER.warn("Could not make request to Annotations Service: " + e.getLocalizedMessage());
        }

        return sequence;
    }
}
