package stroom.pipeline.xsltfunctions;

import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.BooleanValue;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class IPInCidr extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "ip-in-cidr";

    private static final Pattern IPV4_CIDR_PATTERN = Pattern.compile(
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {

        try {
            final String ip = getSafeString(functionName, context, arguments, 0);
            final String cidr = getSafeString(functionName, context, arguments, 1);
            final Matcher cidrMatcher = IPV4_CIDR_PATTERN.matcher(cidr);

            if (cidrMatcher.matches()) {
                final InetAddress ipAddress = InetAddress.getByName(ip);
                final InetAddress cidrAddress = InetAddress.getByName(cidrMatcher.group(1));
                final int prefixLength = Integer.parseInt(cidrMatcher.group(2));
                final int subnetMask = 0xFFFFFFFF << (32 - prefixLength);

                // Convert subnet mask to byte array
                final byte[] subnetMaskBytes = new byte[]{
                        (byte) ((subnetMask & 0xFF000000) >>> 24),
                        (byte) ((subnetMask & 0x00FF0000) >>> 16),
                        (byte) ((subnetMask & 0x0000FF00) >>> 8),
                        (byte) (subnetMask & 0x000000FF)
                };

                for (int i = 0; i < ipAddress.getAddress().length; i++) {
                    if ((ipAddress.getAddress()[i] & subnetMaskBytes[i]) !=
                            (cidrAddress.getAddress()[i] & subnetMaskBytes[i])) {
                        return BooleanValue.FALSE;
                    }
                }

                return BooleanValue.TRUE;
            } else {
                throw new XPathException("Invalid CIDR format: " + cidr);
            }
        } catch (final UnknownHostException e) {
            log(context, Severity.ERROR, "Invalid IP address format", e);
        } catch (final XPathException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return EmptyAtomicSequence.getInstance();
    }
}
