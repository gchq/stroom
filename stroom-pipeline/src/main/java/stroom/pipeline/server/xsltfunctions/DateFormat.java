/*
 * Copyright 2016 Crown Copyright
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

package stroom.pipeline.server.xsltfunctions;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.StringValue;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import stroom.util.date.DateUtil;
import stroom.util.spring.StroomScope;

@Component
@Scope(StroomScope.PROTOTYPE)
@Deprecated
public class DateFormat extends StroomExtensionFunctionCall {
    @Override
    protected Sequence call(String functionName, XPathContext context, Sequence[] arguments) throws XPathException {
        outputWarning(context,
                new StringBuilder("Deprecated function 'date-format' please use the new function 'format-date'"), null);

        long ms = -1;
        if (arguments.length == 1) {
            final String milliseconds = getSafeString(functionName, context, arguments, 0);

            try {
                ms = Long.parseLong(milliseconds);
            } catch (final Throwable e) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Failed to parse date: \"");
                sb.append(milliseconds);
                sb.append('"');
                outputWarning(context, sb, e);
                return StringValue.EMPTY_STRING;
            }

        } else if (arguments.length >= 2) {
            String date = getSafeString(functionName, context, arguments, 0);
            String pattern = getSafeString(functionName, context, arguments, 1);
            String timeZone = null;
            if (arguments.length > 2) {
                timeZone = getSafeString(functionName, context, arguments, 2);
            }

            try {
                ms = DateUtil.parseDate(pattern, timeZone, date);
            } catch (final Throwable e) {
                final StringBuilder sb = new StringBuilder();
                sb.append("Failed to parse date: \"");
                sb.append(date);
                sb.append("\" (Pattern: ");
                sb.append(pattern);
                sb.append(", Time Zone: ");
                sb.append(timeZone);
                sb.append(")");
                outputWarning(context, sb, e);
                return StringValue.EMPTY_STRING;
            }
        }

        final String time = DateUtil.createNormalDateTimeString(ms);
        return StringValue.makeStringValue(time);
    }
}
