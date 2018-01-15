package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import stroom.util.spring.StroomScope;

import java.net.URI;

@Component
@Scope(StroomScope.PROTOTYPE)
public class ParseUri extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(ParseUri.class);

    private static final String EMPTY_STRING = "";
    private static final String NAMESPACE = "uri";

    private static final Attributes EMPTY_ATTS = new org.xml.sax.helpers.AttributesImpl();

    private ReceivingContentHandler contentHandler;

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) {
        try {
            final String value = getSafeString(functionName, context, arguments, 0);
            if (value != null && value.length() > 0) {
                final URI uri = URI.create(value);

                final Configuration configuration = context.getConfiguration();
                final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
                final Builder builder = new TinyBuilder(pipe);

                contentHandler = new ReceivingContentHandler();
                contentHandler.setPipelineConfiguration(pipe);
                contentHandler.setReceiver(builder);

                startDocument();
                dataElement("authority", uri.getAuthority());
                dataElement("fragment", uri.getFragment());
                dataElement("host", uri.getHost());
                dataElement("path", uri.getPath());
                dataElement("port", String.valueOf(uri.getPort()));
                dataElement("query", uri.getQuery());
                dataElement("scheme", uri.getScheme());
                dataElement("schemeSpecificPort", uri.getSchemeSpecificPart());
                dataElement("userInfo", uri.getUserInfo());
                endDocument();

                Sequence sequence = builder.getCurrentRoot();

                // Reset the builder, detaching it from the constructed
                // document.
                builder.reset();

                return sequence;
            }
        } catch (final Exception e) {
            LOGGER.debug(e.getMessage(), e);
            outputWarning(context, new StringBuilder("Problem parsing URI: " + e.getMessage()), e);
        }

        return EmptyAtomicSequence.getInstance();
    }

    private void startDocument() throws SAXException {
        contentHandler.startDocument();
        contentHandler.startPrefixMapping(EMPTY_STRING, NAMESPACE);
    }

    private void endDocument() throws SAXException {
        contentHandler.endPrefixMapping(EMPTY_STRING);
        contentHandler.endDocument();
    }

    private void startElement(final String elementName) throws SAXException {
        contentHandler.startElement(NAMESPACE, elementName, elementName, EMPTY_ATTS);
    }

    private void endElement(final String elementName) throws SAXException {
        contentHandler.endElement(NAMESPACE, elementName, elementName);
    }

    private void dataElement(final String elementName, final String value) throws SAXException {
        startElement(elementName);
        characters(value);
        endElement(elementName);
    }

    private void characters(final String value) throws SAXException {
        if (value != null) {
            final char[] ch = value.toCharArray();
            contentHandler.characters(ch, 0, ch.length);
        }
    }
}
