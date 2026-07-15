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

import stroom.pipeline.LocationFactoryProxy;
import stroom.pipeline.errorhandler.ErrorReceiverProxy;
import stroom.pipeline.errorhandler.FatalErrorReceiver;
import stroom.pipeline.factory.Processor;
import stroom.pipeline.factory.ProcessorFactory;
import stroom.pipeline.factory.SimpleProcessorFactory;
import stroom.pipeline.parser.XMLParser;
import stroom.pipeline.shared.XPathFilter;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import java.io.ByteArrayInputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * Applies stepping XPath filters to a persisted element output XML string, by re-parsing the XML into a
 * {@link SAXEventRecorder} (which rebuilds the Saxon tree and namespace context exactly as live stepping
 * does) and reusing the recorder's XPath match logic. The match is evaluated while the parsed document is
 * still buffered, i.e. before {@code endProcessing} tears the tree down.
 * <p>
 * Lives in the {@code filter} package so it can reuse {@code SAXEventRecorder}'s XPath machinery. Unique
 * ({@code UNIQUE}) filters accumulate their seen values on the supplied {@link XPathFilter} instances, so
 * callers must reuse the same filter objects across a scan (and clear them when starting a fresh scan).
 */
public final class PersistedXPathFilterMatcher {

    private static final LambdaLogger LOGGER = LambdaLoggerFactory.getLogger(PersistedXPathFilterMatcher.class);

    private PersistedXPathFilterMatcher() {
    }

    /**
     * @return true if any of the given XPath filters matches the given output XML.
     */
    public static boolean matches(final String outputXml,
                                  final List<XPathFilter> filters,
                                  final long metaId,
                                  final long recordIndex) {
        if (outputXml == null || outputXml.isBlank() || filters == null || filters.isEmpty()) {
            return false;
        }

        final SAXEventRecorder recorder = new SAXEventRecorder(null, null);
        final ErrorReceiverProxy errorReceiverProxy = new ErrorReceiverProxy(new FatalErrorReceiver());
        final XMLParser parser = new XMLParser(errorReceiverProxy, new LocationFactoryProxy());
        parser.setTarget(recorder);
        parser.setInputStream(
                new ByteArrayInputStream(outputXml.getBytes(StandardCharsets.UTF_8)), null);

        final ProcessorFactory processorFactory = new SimpleProcessorFactory(errorReceiverProxy);
        final Processor processor = processorFactory.create(parser.createProcessors());

        try {
            parser.startProcessing();
            parser.startStream();
            try {
                processor.process();
                // Evaluate while the document is still buffered (endProcessing below resets the tree).
                return recorder.matchesXPathFilters(filters, metaId, recordIndex);
            } finally {
                parser.endStream();
                parser.endProcessing();
            }
        } catch (final RuntimeException e) {
            // Persisted output that isn't standalone well-formed XML (a fragment, non-XML, etc.) simply
            // can't match an XPath filter - treat as a non-match rather than aborting the whole step scan.
            LOGGER.debug(() -> LogUtil.message("Unable to evaluate XPath filter over persisted output: {}",
                    e.getMessage()), e);
            return false;
        }
    }
}
