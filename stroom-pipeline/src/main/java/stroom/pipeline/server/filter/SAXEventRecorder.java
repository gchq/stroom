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

package stroom.pipeline.server.filter;

import net.sf.saxon.Configuration;
import net.sf.saxon.om.NodeInfo;
import net.sf.saxon.xpath.XPathEvaluator;
import org.springframework.context.annotation.Scope;
import org.springframework.stereotype.Component;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import stroom.pipeline.server.errorhandler.ErrorReceiver;
import stroom.pipeline.server.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.server.errorhandler.LoggingErrorReceiver;
import stroom.pipeline.server.errorhandler.ProcessException;
import stroom.pipeline.server.task.NodeInfoSerializer;
import stroom.pipeline.server.task.Recorder;
import stroom.pipeline.server.task.SteppingFilter;
import stroom.pipeline.shared.Record;
import stroom.pipeline.shared.SteppingFilterSettings;
import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.state.StreamHolder;
import stroom.util.shared.Indicators;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;
import stroom.util.spring.StroomScope;

import javax.annotation.Resource;
import javax.xml.namespace.NamespaceContext;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathExpression;
import javax.xml.xpath.XPathExpressionException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Component
@Scope(StroomScope.PROTOTYPE)
public class SAXEventRecorder extends TinyTreeBufferFilter implements Recorder, SteppingFilter {
    private final NamespaceContextImpl namespaceContext = new NamespaceContextImpl();
    @Resource
    private StreamHolder streamHolder;
    @Resource
    private ErrorReceiverProxy errorReceiverProxy;
    private boolean filterApplied;
    private SteppingFilterSettings settings;
    private Set<CompiledXPathFilter> xPathFilters;
    private int currentElementDepth;
    private int maxElementDepth;
    private String elementId;

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
            if (settings.getXPathFilters() != null) {
                for (final XPathFilter xPathFilter : settings.getXPathFilters()) {
                    if (xPathFilter.getMatchType() != null && xPathFilter.getXPath() != null) {
                        if (xPathFilter.getMatchType().isNeedsValue()) {
                            if (xPathFilter.getValue() != null && xPathFilter.getValue().length() > 0) {
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
    public boolean filterMatches(final long recordNo) {
        final long streamId = streamHolder.getStream().getId();

        // If we are skipping to a severity then perform check on severity.
        if (settings.getSkipToSeverity() != null) {
            final ErrorReceiver errorReceiver = errorReceiverProxy.getErrorReceiver();
            if (errorReceiver != null && errorReceiver instanceof LoggingErrorReceiver) {
                final LoggingErrorReceiver loggingErrorReceiver = (LoggingErrorReceiver) errorReceiver;

                // If this is a stepping filter being used for input then catch
                // indicators raised by the code.
                if (elementId != null) {
                    final Indicators indicators = loggingErrorReceiver.getIndicatorsMap().get(elementId);
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

        if (xPathFilters == null && settings.getXPathFilters() != null && settings.getXPathFilters().size() > 0) {
            // Compile the XPath filters.
            xPathFilters = new HashSet<>();
            for (final XPathFilter xPathFilter : settings.getXPathFilters()) {
                try {
                    // Only add filters that check for uniqueness or that have
                    // had a valid value specified.
                    if (!xPathFilter.getMatchType().isNeedsValue() || xPathFilter.getValue() != null) {
                        final CompiledXPathFilter compiledXPathFilter = new CompiledXPathFilter(xPathFilter,
                                getConfiguration(), getNamespaceContext());
                        xPathFilters.add(compiledXPathFilter);
                    }
                } catch (final XPathExpressionException e) {
                    throw ProcessException.wrap("Error in XPath filter expression", e);
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
                    final List<NodeInfo> nodes = (List<NodeInfo>) result;
                    if (nodes.size() > 0) {
                        final XPathFilter xPathFilter = compiledXPathFilter.getXPathFilter();
                        switch (xPathFilter.getMatchType()) {
                            case EXISTS:
                                return true;

                            case CONTAINS:
                                for (int i = 0; i < nodes.size(); i++) {
                                    final NodeInfo node = nodes.get(i);
                                    if (contains(node.getStringValue(), xPathFilter.getValue(),
                                            xPathFilter.isIgnoreCase())) {
                                        return true;
                                    }
                                }
                                break;

                            case EQUALS:
                                for (int i = 0; i < nodes.size(); i++) {
                                    final NodeInfo node = nodes.get(i);
                                    if (equals(node.getStringValue(), xPathFilter.getValue(), xPathFilter.isIgnoreCase())) {
                                        return true;
                                    }
                                }
                                break;
                            case UNIQUE:
                                for (int i = 0; i < nodes.size(); i++) {
                                    final NodeInfo node = nodes.get(i);
                                    String value = node.getStringValue();
                                    if (value != null) {
                                        value = value.trim();
                                        if (xPathFilter.isIgnoreCase() != null && xPathFilter.isIgnoreCase()) {
                                            value = value.toLowerCase();
                                        }
                                    }

                                    // See if we previously found a matching record
                                    // for this filter.
                                    Record record = xPathFilter.getUniqueRecord(value);
                                    if (record != null) {
                                        // We did so see if this is the same record.
                                        // If it is then we can return this record
                                        // again.
                                        if (record.getStreamId() == streamId && record.getRecordNo() == recordNo) {
                                            return true;
                                        }

                                    } else {
                                        record = new Record(streamId, recordNo);
                                        xPathFilter.addUniqueValue(value, record);
                                        return true;
                                    }
                                }
                                break;
                        }
                    }
                }
            } catch (final Exception e) {
                throw ProcessException.wrap(e);
            }
        }

        return false;
    }

    private boolean contains(final String value, final String text, final Boolean ignoreCase) {
        if (value != null && text != null) {
            String val = value.trim();
            String txt = text.trim();

            if (ignoreCase != null && ignoreCase) {
                val = val.toLowerCase();
                txt = text.toLowerCase();
            }

            if (val.contains(txt)) {
                return true;
            }
        }

        return false;
    }

    private boolean equals(final String value, final String text, final Boolean ignoreCase) {
        if (value != null && text != null) {
            String val = value.trim();
            String txt = text.trim();

            if (ignoreCase != null && ignoreCase) {
                val = val.toLowerCase();
                txt = text.toLowerCase();
            }

            if (val.equals(txt)) {
                return true;
            }
        }

        return false;
    }

    @Override
    public Object getData() {
        final NodeInfo events = getEvents();
        if (events == null) {
            return null;
        }

        return new NodeInfoSerializer(events);
    }

    @Override
    public void clear() {
        // Clear the event buffer as this is a new record.
        clearBuffer();

        // Clear the namespace context maps.
        namespaceContext.clear();

        // We are processing a new record so reset fields.
        maxElementDepth = 0;
    }

    @Override
    public String getElementId() {
        return elementId;
    }

    @Override
    public void setElementId(final String elementId) {
        this.elementId = elementId;
    }

    @Override
    public boolean isFilterApplied() {
        return filterApplied;
    }

    public static class CompiledXPathFilter {
        private final XPathFilter xPathFilter;
        private final XPathExpression xPathExpression;

        public CompiledXPathFilter(final XPathFilter xPathFilter, final Configuration configuration,
                                   final NamespaceContext namespaceContext) throws XPathExpressionException {
            this.xPathFilter = xPathFilter;

            final String path = xPathFilter.getXPath();
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
    }
}
