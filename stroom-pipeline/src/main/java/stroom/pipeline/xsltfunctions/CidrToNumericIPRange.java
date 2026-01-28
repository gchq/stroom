package stroom.pipeline.xsltfunctions;

import stroom.util.net.IpAddressUtil;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.ma.arrays.ArrayItem;
import net.sf.saxon.ma.arrays.SimpleArrayItem;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;

import java.net.UnknownHostException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

class CidrToNumericIPRange extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "cidr-to-numeric-ip-range";

    private static final Pattern IPV4_CIDR_PATTERN = Pattern.compile(
            "^(\\d{1,3}\\.\\d{1,3}\\.\\d{1,3}\\.\\d{1,3})/(\\d{1,2})$");

    @Override
    protected ArrayItem call(final String functionName, final XPathContext context, final Sequence[] arguments) {

        try {
            final String cidr = getSafeString(functionName, context, arguments, 0);
            final Matcher cidrMatcher = IPV4_CIDR_PATTERN.matcher(cidr);

            if (cidrMatcher.matches()) {
                final String cidrAddress = cidrMatcher.group(1);
                final int prefixLength = Integer.parseInt(cidrMatcher.group(2));
                final int subnetMask = 0xFFFFFFFF << (32 - prefixLength);

                final long networkAddress = IpAddressUtil.toNumericIpAddress(cidrAddress) & subnetMask;
                final long broadcastAddress = networkAddress | (~subnetMask);

                // TODO can we return immutable lists, e.g. List.of(...) ?
                return new SimpleArrayItem(new ArrayList<>(Arrays.asList(
                        StringValue.makeStringValue(Long.toString(networkAddress)),
                        StringValue.makeStringValue(Long.toString(broadcastAddress)))));
            } else {
                throw new XPathException("Invalid CIDR format: " + cidr);
            }
        } catch (final UnknownHostException e) {
            log(context, Severity.ERROR, "Invalid IP address", e);
        } catch (final XPathException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        return new SimpleArrayItem(new ArrayList<>());
    }
}
