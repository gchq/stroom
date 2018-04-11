package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class GenerateURL extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(GenerateURL.class);

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        final String title = getSafeString(functionName, context, arguments, 0);
        final String host = getSafeString(functionName, context, arguments, 1);
        final String path = getSafeString(functionName, context, arguments, 2);
        final String target = getSafeString(functionName, context, arguments, 3);

        final String url = String.format("[%s](%s%s){%s}", title, host, path, target);

        LOGGER.trace(String.format("Generating URL %s", url));

        return StringValue.makeStringValue(url);
    }
}
