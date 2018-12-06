package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.spring.StroomScope;

@Component
@Scope(StroomScope.PROTOTYPE)
public class Link extends StroomExtensionFunctionCall {
    private static final Logger LOGGER = LoggerFactory.getLogger(Link.class);

    private static final String DEFAULT_TARGET = "BROWSER_TAB";

    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        String link = "";

        if (arguments.length == 1) {
            final String url = getSafeString(functionName, context, arguments, 0);
            link = makeLink(url, url, "", DEFAULT_TARGET);
        } else if (arguments.length == 2) {
            final String title = getSafeString(functionName, context, arguments, 0);
            final String url = getSafeString(functionName, context, arguments, 1);
            link = makeLink(title, url, "", DEFAULT_TARGET);
        } else if (arguments.length == 3) {
            final String title = getSafeString(functionName, context, arguments, 0);
            final String host = getSafeString(functionName, context, arguments, 1);
            final String path = getSafeString(functionName, context, arguments, 2);
            link = makeLink(title, host, path, DEFAULT_TARGET);
        } else if (arguments.length == 4) {
            final String title = getSafeString(functionName, context, arguments, 0);
            final String host = getSafeString(functionName, context, arguments, 1);
            final String path = getSafeString(functionName, context, arguments, 2);
            final String target = getSafeString(functionName, context, arguments, 3);
            link = makeLink(title, host, path, target);
        }

        LOGGER.trace(String.format("Generating link %s", link));
        return StringValue.makeStringValue(link);
    }

    private String makeLink(final String title, final String host, final String path, final String target) {
        return "[" + title + "](" + host + path + "){" + target + "}";
    }
}
