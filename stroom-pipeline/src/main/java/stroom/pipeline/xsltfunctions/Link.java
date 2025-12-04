/*
 * Copyright 2016-2025 Crown Copyright
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package stroom.pipeline.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class Link extends StroomExtensionFunctionCall {

    private static final Logger LOGGER = LoggerFactory.getLogger(Link.class);

    @Override
    protected Sequence call(final String functionName,
                            final XPathContext context,
                            final Sequence[] arguments) throws XPathException {

        String link = "";

        if (arguments.length == 1) {
            final String url = getSafeString(functionName, context, arguments, 0);
            link = makeLink(url, url, null);
        } else if (arguments.length == 2) {
            final String text = getSafeString(functionName, context, arguments, 0);
            final String url = getSafeString(functionName, context, arguments, 1);
            link = makeLink(text, url, null);
        } else if (arguments.length == 3) {
            final String text = getSafeString(functionName, context, arguments, 0);
            final String url = getSafeString(functionName, context, arguments, 1);
            final String type = getSafeString(functionName, context, arguments, 2);
            link = makeLink(text, url, type);
        }

        LOGGER.trace(String.format("Generating link %s", link));
        return StringValue.makeStringValue(link);
    }

    private String makeLink(final String text, final String href, final String type) {
        final StringBuilder sb = new StringBuilder();
        if (text != null) {
            sb.append("[");
            sb.append(text);
            sb.append("]");
        }
        if (href != null) {
            sb.append("(");
            sb.append(href);
            sb.append(")");
        }
        if (type != null) {
            sb.append("{");
            sb.append(type);
            sb.append("}");
        }
        return sb.toString();
    }
}
