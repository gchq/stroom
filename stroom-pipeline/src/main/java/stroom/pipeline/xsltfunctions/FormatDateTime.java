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

import stroom.pipeline.LocationFactory;
import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.shared.data.PipelineReference;
import stroom.util.date.DateFormatterCache;
import stroom.util.shared.Severity;

import net.sf.saxon.expr.XPathContext;
import net.sf.saxon.om.EmptyAtomicSequence;
import net.sf.saxon.om.Sequence;
import net.sf.saxon.trans.XPathException;
import net.sf.saxon.value.DateTimeValue;
import net.sf.saxon.value.StringValue;

import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;
import java.time.ZonedDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

class FormatDateTime extends StroomExtensionFunctionCall {

    public static final String FUNCTION_NAME = "format-dateTime";

    @Override
    void configure(final ErrorReceiver errorReceiver,
                   final LocationFactory locationFactory,
                   final List<PipelineReference> pipelineReferences) {
        super.configure(errorReceiver, locationFactory, pipelineReferences);
    }

    @Override
    protected Sequence call(final String functionName, final XPathContext context, final Sequence[] arguments) {
        String result = null;

        try {
            if (arguments.length >= 1 && arguments.length <= 3) {
                result = convertToSpecifiedDateFormat(functionName, context, arguments);
            }
        } catch (final XPathException | RuntimeException e) {
            log(context, Severity.ERROR, e.getMessage(), e);
        }

        if (result == null) {
            return EmptyAtomicSequence.getInstance();
        }
        return StringValue.makeStringValue(result);
    }

    private String convertToSpecifiedDateFormat(final String functionName, final XPathContext context,
                                                final Sequence[] arguments) throws XPathException {
        String result = null;
        final DateTimeValue value = getSafeDateTime(functionName, context, arguments, 0);

        String patternOut = null;
        if (arguments.length >= 2) {
            patternOut = getSafeString(functionName, context, arguments, 1);
        }
        String timeZoneOut = null;
        if (arguments.length == 3) {
            timeZoneOut = getSafeString(functionName, context, arguments, 2);
        }

        // Create the supplied date.
        final Instant instant = value.toJavaInstant();

        // Resolve the output time zone.
        final ZoneId zoneId = getTimeZone(context, timeZoneOut);
        try {
            // Now format the date using the specified pattern and time
            // zone.
            final ZonedDateTime dateTime = instant.atZone(zoneId);
            final DateTimeFormatter dateTimeFormatter = DateFormatterCache.getFormatter(patternOut);
            result = dateTimeFormatter.format(dateTime);
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Failed to format date: \"");
            sb.append(value);
            sb.append("\" (Pattern: ");
            sb.append(patternOut);
            sb.append(", Time Zone: ");
            sb.append(timeZoneOut);
            sb.append(")");
            outputWarning(context, sb, e);
        }

        return result;
    }

    private ZoneId getTimeZone(final XPathContext context, final String timeZone) {
        try {
            return DateFormatterCache.getZoneId(timeZone);
        } catch (final RuntimeException e) {
            final StringBuilder sb = new StringBuilder();
            sb.append("Time Zone '");
            sb.append(timeZone);
            sb.append("' is not recognised, defaulting to UTC");
            outputWarning(context, sb, e);
        }
        return ZoneOffset.UTC;
    }
}
