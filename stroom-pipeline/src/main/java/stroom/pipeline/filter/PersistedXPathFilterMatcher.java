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

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.xml.event.SaxEventReader;
import stroom.util.logging.LambdaLogger;
import stroom.util.logging.LambdaLoggerFactory;
import stroom.util.logging.LogUtil;

import org.xml.sax.SAXException;

import java.util.List;

/**
 * Applies stepping XPath filters to a persisted element output by firing its stored SAX events into a
 * {@link SAXEventRecorder} (which rebuilds the Saxon tree and namespace context exactly as live stepping
 * does) and reusing the recorder's XPath match logic. Firing the events avoids re-parsing XML text: the
 * store already holds the events, so there is no serialise-then-parse round trip. The match is evaluated
 * while the document is still buffered, i.e. before {@code endProcessing} tears the tree down.
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
     * @return true if any of the given XPath filters matches the element's persisted output events.
     */
    public static boolean matches(final byte[] outputEvents,
                                  final List<XPathFilter> filters,
                                  final long metaId,
                                  final long recordIndex) {
        if (outputEvents == null || outputEvents.length == 0 || filters == null || filters.isEmpty()) {
            return false;
        }

        final SAXEventRecorder recorder = new SAXEventRecorder(null, null);
        try {
            recorder.startProcessing();
            recorder.startStream();
            try {
                // Fire the stored events straight into the recorder - it rebuilds the tree and namespace
                // context as a parse would, without re-parsing XML text.
                SaxEventReader.replay(outputEvents, recorder);
                // Evaluate while the document is still buffered (endProcessing below resets the tree).
                return recorder.matchesXPathFilters(filters, metaId, recordIndex);
            } finally {
                recorder.endStream();
                recorder.endProcessing();
            }
        } catch (final SAXException | RuntimeException e) {
            LOGGER.debug(() -> LogUtil.message("Unable to evaluate XPath filter over persisted output: {}",
                    e.getMessage()), e);
            return false;
        }
    }
}
