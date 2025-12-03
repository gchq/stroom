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

package stroom.pipeline.filter;

import stroom.pipeline.errorhandler.ErrorReceiver;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.errorhandler.ProcessException;
import stroom.pipeline.shared.Rec;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.pipeline.state.MetaHolder;
import stroom.pipeline.stepping.Recorder;
import stroom.pipeline.stepping.SteppingFilter;
import stroom.util.shared.ElementId;
import stroom.util.shared.Indicators;
import stroom.util.shared.NullSafe;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.util.shared.TextRange;

import jakarta.inject.Inject;
import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.xpath.XPathEvaluator;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;

import java.util.HashSet;
import java.util.List;
import java.util.Set;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;

public class SAXEventRecorder extends TinyTreeBufferFilter implements Recorder, SteppingFilter {

    private final NamespaceContextImpl namespaceContext = new NamespaceContextImpl();

    private final MetaHolder metaHolder;
    private final ErrorReceiverProxy errorReceiverProxy;

    private boolean filterApplied;
    private SteppingFilterSettings settings;
    private Set<CompiledXPathFilter> xPathFilters;
    private int currentElementDepth;
    private int maxElementDepth;
    private ElementId elementId;

    @Inject
    public SAXEventRecorder(final MetaHolder metaHolder,
                            final ErrorReceiverProxy errorReceiverProxy) {
        this.metaHolder = metaHolder;
        this.errorReceiverProxy = errorReceiverProxy;
    }

    public void setSettings(final SteppingFilterSettings settings) {
        this.settings = settings;

        // Check to see if a filter has been applied to this section of the
        // pipeline.
        filterApplied = checkFilterApplied();
    }

    @Override
    public void startPrefixMapping(final String prefix, final String uri) throws SAXException {
        namespaceContext.addPrefix(prefix, uri);
        super.startPrefixMapping(prefix, uri);
    }

    public NamespaceContextImpl getNamespaceContext() {
        return namespaceContext;
    }

    @Override
    public void startElement(final String uri, final String localName, final String qName, final Attributes atts)
            throws SAXException {
        currentElementDepth++;
        if (currentElementDepth > maxElementDepth) {
            maxElementDepth = currentElementDepth;
        }

        super.startElement(uri, localName, qName, atts);
    }

    @Override
    public void endElement(final String uri, final String localName, final String qName) throws SAXException {
        currentElementDepth--;
        super.endElement(uri, localName, qName);
    }

    /**
     * This method checks to see if a filter has been applied to this section of
     * the pipeline.
     */
    private boolean checkFilterApplied() {
        if (settings != null) {
            if (settings.getSkipToSeverity() != null || settings.getSkipToOutput() != null) {
                return true;
            }
            if (settings.getFilters() != null) {
                for (final XPathFilter xPathFilter : settings.getFilters()) {
                    if (NullSafe.allNonNull(xPathFilter.getMatchType(), xPathFilter.getPath())) {
                        if (xPathFilter.getMatchType().isNeedsValue()) {
                            if (NullSafe.isNonEmptyString(xPathFilter.getValue())) {
                                return true;
                            }
                        } else {
                            return true;
                        }
                    }
                }
            }
        }

        return false;
    }

    /**
     * This method checks that the current event matches the filter that has
     * been applied to this section of the pipeline.
     */
    @Override
    @SuppressWarnings("unchecked")
    public boolean filterMatches(final long recordIndex) {
        final long metaId = metaHolder.getMeta().getId();

        // If we are skipping to a severity then perform check on severity.
        if (settings.getSkipToSeverity() != null) {
            final ErrorReceiver errorReceiver = errorReceiverProxy.getErrorReceiver();
            if (errorReceiver instanceof final LoggingErrorReceiver loggingErrorReceiver) {
                // If this is a stepping filter being used for input then catch
                // indicators raised by the code.
                if (elementId != null) {
                    final Indicators indicators = loggingErrorReceiver.getIndicators(elementId);
                    if (indicators != null) {
                        final Severity maxSeverity = indicators.getMaxSeverity();
                        if (maxSeverity != null && maxSeverity.greaterThanOrEqual(settings.getSkipToSeverity())) {
                            return true;
                        }
                    }
                }
            }
        }

        // If we are skipping to output or no output then check to see if we
        // have output or not.
        if (settings.getSkipToOutput() != null) {
            if (maxElementDepth > 1) {
                if (OutputState.NOT_EMPTY.equals(settings.getSkipToOutput())) {
                    return true;
                }
            } else {
                if (OutputState.EMPTY.equals(settings.getSkipToOutput())) {
                    return true;
                }
            }
        }

        if (xPathFilters == null && settings.getFilters() != null && !settings.getFilters().isEmpty()) {
            // Compile the XPath filters.
            xPathFilters = new HashSet<>();
            for (final XPathFilter xPathFilter : settings.getFilters()) {
                try {
                    // Only add filters that check for uniqueness or that have
                    // had a valid value specified.
                    if (!xPathFilter.getMatchType().isNeedsValue() || xPathFilter.getValue() != null) {
                        final CompiledXPathFilter compiledXPathFilter = new CompiledXPathFilter(xPathFilter,
                                getConfiguration(), getNamespaceContext());
                        xPathFilters.add(compiledXPathFilter);
                    }
                } catch (final XPathExpressionException e) {
                    throw ProcessException.create("Error in XPath filter expression", e);
                }
            }
        }

        if (xPathFilters != null) {
            try {
                final NodeInfo nodeInfo = getEvents();
                final NodeInfo documentInfo = nodeInfo.getRoot();
                for (final CompiledXPathFilter compiledXPathFilter : xPathFilters) {
                    final Object result = compiledXPathFilter.getXPathExpression().evaluate(documentInfo,
                            XPathConstants.NODESET);
                    // May contain NodeInfo, Boolean, Double, Long, String
                    final List<Object> objects = (List<Object>) result;
                    if (NullSafe.hasItems(objects)) {
                        if (isFilterMatch(objects, compiledXPathFilter, metaId, recordIndex)) {
                            return true;
                        }
                    }
                }
            } catch (final XPathExpressionException | RuntimeException e) {
                throw ProcessException.wrap(e);
            }
        }

        return false;
    }

    /**
     * Pkg private for testing
     */
    static boolean isFilterMatch(final List<Object> objects,
                                 final CompiledXPathFilter compiledXPathFilter,
                                 final long metaId,
                                 final long recordIndex) {
        final XPathFilter xPathFilter = compiledXPathFilter.getXPathFilter();
        switch (xPathFilter.getMatchType()) {
            case EXISTS -> {
                return true;
            }
            case NOT_EXISTS -> {
                return objects.isEmpty();
            }
            case CONTAINS -> {
                return contains(objects, xPathFilter);
            }
            case NOT_CONTAINS -> {
                return !contains(objects, xPathFilter);
            }
            case EQUALS -> {
                return equals(objects, xPathFilter);
            }
            case NOT_EQUALS -> {
                return !equals(objects, xPathFilter);
            }
            case UNIQUE -> {
                for (final Object object : objects) {
                    final String value = getStringValue(object, xPathFilter.isIgnoreCase());
                    // See if we previously found a matching record
                    // for this filter.
                    Rec record = xPathFilter.getUniqueRecord(value);
                    if (record != null) {
                        // We did so see if this is the same record.
                        // If it is then we can return this record
                        // again.
                        if (record.getMetaId() == metaId &&
                            record.getRecordIndex() == recordIndex) {
                            return true;
                        }

                    } else {
                        record = new Rec(metaId, recordIndex);
                        xPathFilter.addUniqueValue(value, record);
                        return true;
                    }
                }
            }
        }
        return false;
    }

    /**
     * @return value as a string, trimmed and, if ignoreCase is true, converted to lower case
     */
    private static String getStringValue(final Object object, final Boolean ignoreCase) {
        if (object == null) {
            return null;
        } else {
            return switch (object) {
                case final NodeInfo nodeInfo -> clean(nodeInfo.getStringValue(), ignoreCase);
                case final String str -> clean(str, ignoreCase);
                default -> object.toString();
            };
        }
    }

    private static String clean(final String value, final Boolean ignoreCase) {
        return NullSafe.get(value,
                val -> NullSafe.isTrue(ignoreCase)
                        ? val.trim().toLowerCase()
                        : val.trim());
    }

    private static boolean contains(final List<Object> objects,
                                    final XPathFilter xPathFilter) {
        for (final Object object : objects) {
            if (contains(object, xPathFilter.getValue(), xPathFilter.isIgnoreCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean contains(final Object value, final String text, final Boolean ignoreCase) {
        // Contains doesn't really make any sense for any type other than string, so just convert whatever it
        // is to a string and do contains on that.
        final String valueStr = getStringValue(value, ignoreCase);

        if (valueStr == null || text == null) {
            return false;
        }

        String txt = text.trim();

        if (NullSafe.isTrue(ignoreCase)) {
            // getStringValue does the trim and toLowerCase for value/valueStr
            txt = text.toLowerCase();
        }
        return valueStr.contains(txt);
    }

    private static boolean equals(final List<Object> objects,
                                  final XPathFilter xPathFilter) {
        for (final Object object : objects) {
            if (equals(object, xPathFilter.getValue(), xPathFilter.isIgnoreCase())) {
                return true;
            }
        }
        return false;
    }

    private static boolean equals(final Object value, final String text, final Boolean ignoreCase) {
        if (value == null || text == null) {
            return false;
        } else {
            return switch (value) {
                case final NodeInfo nodeInfo -> equalsAsString(nodeInfo.getStringValue(), text, ignoreCase);
                case final String str -> equalsAsString(str, text, ignoreCase);
                case final Double aDouble -> equalsAsDouble(aDouble, text);
                case final Long aLong -> equalsAsLong(aLong, text);
                case final Boolean aBool -> equalsAsBoolean(aBool, text);
                default -> equalsAsString(value.toString(), text, ignoreCase);
            };
        }
    }

    private static boolean equalsAsString(final String value, final String text, final Boolean ignoreCase) {
        if (value == null || text == null) {
            return false;
        } else {
            final String val = value.trim();
            final String txt = text.trim();

            if (NullSafe.isTrue(ignoreCase)) {
                return val.equalsIgnoreCase(txt);
            } else {
                return val.equals(txt);
            }
        }
    }

    private static boolean equalsAsDouble(final Double value, final String text) {
        try {
            final Double val2 = Double.parseDouble(text);
            return value.equals(val2);
        } catch (final NumberFormatException e) {
            // We know the xpath returned a number so if we can't parse the user value to a number
            // it is not a match
            return false;
        }
    }

    private static boolean equalsAsLong(final Long value, final String text) {
        try {
            final Long val2 = Long.parseLong(text);
            return value.equals(val2);
        } catch (final NumberFormatException e) {
            // We know the xpath returned a number so if we can't parse the user value to a number
            // it is not a match
            return false;
        }
    }

    private static boolean equalsAsBoolean(final Boolean value, final String text) {
        try {
            final Boolean val2 = Boolean.parseBoolean(text);
            return value.equals(val2);
        } catch (final NumberFormatException e) {
            // We know the xpath returned a number so if we can't parse the user value to a number
            // it is not a match
            return false;
        }
    }

    @Override
    public Object getData(final TextRange textRange) {
        final NodeInfo events = getEvents();
        if (events == null) {
            return null;
        }
        return new NodeInfoSerializer(events);
    }

    @Override
    public void clear(final TextRange textRange) {
        // Clear the event buffer as this is a new record.
        clearBuffer();

        // Clear the namespace context maps.
        namespaceContext.clear();

        // We are processing a new record so reset fields.
        maxElementDepth = 0;
    }

    @Override
    public ElementId getElementId() {
        return elementId;
    }

    @Override
    public void setElementId(final ElementId elementId) {
        this.elementId = elementId;
    }

    @Override
    public boolean isFilterApplied() {
        return filterApplied;
    }

    @Override
    public String toString() {
        return "SAXEventRecorder{" +
               "elementId='" + elementId + '\'' +
               '}';
    }

    // --------------------------------------------------------------------------------


    public static class CompiledXPathFilter {

        private final XPathFilter xPathFilter;
        private final XPathExpression xPathExpression;

        public CompiledXPathFilter(final XPathFilter xPathFilter,
                                   final Configuration configuration,
                                   final NamespaceContext namespaceContext) throws XPathExpressionException {
            this.xPathFilter = xPathFilter;

            final String path = xPathFilter.getPath();
            final XPathEvaluator xPathEvaluator = new XPathEvaluator(configuration);

            final String defaultNamespaceURI = namespaceContext.getNamespaceURI("");
            xPathEvaluator.getStaticContext().setDefaultElementNamespace(defaultNamespaceURI);
            xPathEvaluator.getStaticContext().setNamespaceContext(namespaceContext);
            xPathExpression = xPathEvaluator.compile(path);
        }

        public XPathFilter getXPathFilter() {
            return xPathFilter;
        }

        public XPathExpression getXPathExpression() {
            return xPathExpression;
        }

        @Override
        public String toString() {
            return "CompiledXPathFilter{" +
                   "xPathFilter=" + xPathFilter +
                   ", xPathExpression=" + xPathExpression +
                   '}';
        }
    }
}
