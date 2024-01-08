package stroom.pipeline.xsltfunctions;

import stroom.pipeline.xml.event.simple.AttributesImpl;
import stroom.util.shared.Severity;

import net.sf.saxon.Configuration;
import net.sf.saxon.event.Builder;
import net.sf.saxon.event.PipelineConfiguration;
import net.sf.saxon.event.ReceivingContentHandler;
import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.tree.tiny.TinyBuilder;
import org.xml.sax.SAXException;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CidrToNumericIPRange extends StroomExtensionFunctionCall {

    private static final String XML_NAMESPACE = "reference-data:2";
    private static final String RANGE_ELEMENT = "range";
    private static final String FROM_ELEMENT = "from";
    private static final String TO_ELEMENT = "to";
    private static final Pattern IPV4_CIDR_PATTERN = Pattern.compile(
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {

        try {
            final String cidr = getSafeString(functionName, context, arguments, 0);
            final Matcher cidrMatcher = IPV4_CIDR_PATTERN.matcher(cidr);

            if (cidrMatcher.matches()) {
                final InetAddress cidrAddress = InetAddress.getByName(cidrMatcher.group(1));
                final int prefixLength = Integer.parseInt(cidrMatcher.group(2));
                final int subnetMask = 0xFFFFFFFF << (32 - prefixLength);

                long networkAddress = byteArrayToLong(cidrAddress.getAddress()) & subnetMask;
                long broadcastAddress = networkAddress | (~subnetMask);

                return createRange(context, networkAddress, broadcastAddress);
            } else {
                throw new XPathException("Invalid CIDR format: " + cidr);
            }
        } catch (UnknownHostException e) {
            log(context, Severity.ERROR, "Invalid IP address", e);
        } catch (XPathException | SAXException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }

    private static long byteArrayToLong(byte[] bytes) {
        long value = 0;
        for (byte b : bytes) {
            value = (value << 8) | (b & 0xFF);
        }
        return value;
    }

    private Sequence createRange(final XPathContext context, final long from, final long to) throws SAXException {
        final Configuration configuration = context.getConfiguration();
        final PipelineConfiguration pipe = configuration.makePipelineConfiguration();
        final Builder builder = new TinyBuilder(pipe);

        final ReceivingContentHandler contentHandler = new ReceivingContentHandler();
        contentHandler.setPipelineConfiguration(pipe);
        contentHandler.setReceiver(builder);

        contentHandler.startDocument();
        startElement(contentHandler, RANGE_ELEMENT);

        startElement(contentHandler, FROM_ELEMENT);
        characters(contentHandler, Long.toString(from));
        endElement(contentHandler, FROM_ELEMENT);

        startElement(contentHandler, TO_ELEMENT);
        characters(contentHandler, Long.toString(to));
        endElement(contentHandler, TO_ELEMENT);

        endElement(contentHandler, RANGE_ELEMENT);
        contentHandler.endDocument();

        final Sequence sequence = builder.getCurrentRoot();
        builder.reset();

        return sequence;
    }

    private void startElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.startElement(XML_NAMESPACE, name, name, new AttributesImpl());
    }

    private void endElement(final ReceivingContentHandler contentHandler, final String name) throws SAXException {
        contentHandler.endElement(XML_NAMESPACE, name, name);
    }

    private void characters(final ReceivingContentHandler contentHandler, final String characters) throws SAXException {
        final char[] chars = characters.toCharArray();
        contentHandler.characters(chars, 0, chars.length);
    }
}
