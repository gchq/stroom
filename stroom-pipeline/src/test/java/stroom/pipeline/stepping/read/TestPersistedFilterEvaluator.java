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

package stroom.pipeline.stepping.read;

import stroom.pipeline.shared.XPathFilter;
import stroom.pipeline.stepping.store.CapturedData;
import stroom.pipeline.stepping.store.CapturedElementData;
import stroom.pipeline.xml.event.EventListSerializer;
import stroom.pipeline.xml.event.simple.SimpleEventListBuilder;

import org.xml.sax.ContentHandler;
import org.xml.sax.InputSource;
import org.xml.sax.XMLReader;

import java.io.StringReader;
import javax.xml.parsers.SAXParserFactory;
import stroom.pipeline.shared.stepping.SteppingFilterSettings;
import stroom.util.shared.Indicators;
import stroom.util.shared.OutputState;
import stroom.util.shared.Severity;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestPersistedFilterEvaluator {

    private final PersistedFilterEvaluator evaluator = new PersistedFilterEvaluator();

    private CapturedElementData data(final boolean hasOutput, final Severity maxSeverity) {
        Indicators indicators = null;
        if (maxSeverity != null) {
            indicators = new Indicators(new HashMap<>(Map.of(maxSeverity, 1)), new HashSet<>(), new ArrayList<>());
        }
        return new CapturedElementData(null, hasOutput ? CapturedData.text("out") : null,
                false, false, hasOutput, indicators);
    }

    private boolean matches(final CapturedElementData data, final SteppingFilterSettings settings) {
        return evaluator.matches(data, settings, 1L, 0L);
    }

    @Test
    void testSkipToSeverity() {
        final SteppingFilterSettings warn = new SteppingFilterSettings(Severity.WARNING, null, null);
        assertThat(matches(data(false, Severity.ERROR), warn)).isTrue();      // ERROR >= WARNING
        assertThat(matches(data(false, Severity.WARNING), warn)).isTrue();    // equal
        assertThat(matches(data(false, Severity.INFO), warn)).isFalse();      // INFO < WARNING
        assertThat(matches(data(false, null), warn)).isFalse();               // no indicators
    }

    @Test
    void testSkipToSeverityBoundary() {
        final SteppingFilterSettings fatal = new SteppingFilterSettings(Severity.FATAL_ERROR, null, null);
        assertThat(matches(data(false, Severity.ERROR), fatal)).isFalse();
        assertThat(matches(data(false, Severity.FATAL_ERROR), fatal)).isTrue();
    }

    @Test
    void testSkipToOutputNotEmpty() {
        final SteppingFilterSettings notEmpty = new SteppingFilterSettings(null, OutputState.NOT_EMPTY, null);
        assertThat(matches(data(true, null), notEmpty)).isTrue();
        assertThat(matches(data(false, null), notEmpty)).isFalse();
    }

    @Test
    void testSkipToOutputEmpty() {
        final SteppingFilterSettings empty = new SteppingFilterSettings(null, OutputState.EMPTY, null);
        assertThat(matches(data(false, null), empty)).isTrue();
        assertThat(matches(data(true, null), empty)).isFalse();
    }

    @Test
    void testInactiveSettingsNeverMatch() {
        assertThat(matches(data(true, Severity.ERROR), new SteppingFilterSettings(null, null, null)))
                .isFalse();
        assertThat(matches(data(true, null), null)).isFalse();
    }

    private CapturedElementData xmlOutput(final String xml) {
        return new CapturedElementData(null, CapturedData.saxEvents(saxEventBytes(xml)),
                false, false, true, null);
    }

    private static byte[] saxEventBytes(final String xml) {
        try {
            final SimpleEventListBuilder builder = new SimpleEventListBuilder();
            final SAXParserFactory factory = SAXParserFactory.newInstance();
            factory.setNamespaceAware(true);
            final XMLReader reader = factory.newSAXParser().getXMLReader();
            reader.setContentHandler((ContentHandler) builder);
            reader.parse(new InputSource(new StringReader(xml)));
            return EventListSerializer.toBytes(builder.getEventList());
        } catch (final Exception e) {
            throw new RuntimeException(e);
        }
    }

    private SteppingFilterSettings xpath(final String path, final XPathFilter.MatchType matchType, final String value) {
        return new SteppingFilterSettings(null, null,
                List.of(new XPathFilter(path, matchType, value, false, null, null)));
    }

    // Mirrors real stepping XPath usage: event XML with a default namespace (unprefixed XPath steps
    // resolve to that default namespace).
    private static final String EVENTS_XML =
            "<Events xmlns=\"events:1\"><Event><Id>hello world</Id></Event></Events>";

    @Test
    void testXPathExists() {
        final CapturedElementData data = xmlOutput(EVENTS_XML);
        assertThat(matches(data, xpath("/Events/Event", XPathFilter.MatchType.EXISTS, null))).isTrue();
        assertThat(matches(data, xpath("/Events/Missing", XPathFilter.MatchType.EXISTS, null))).isFalse();
        // NOTE: mirrors live SAXEventRecorder.filterMatches - match types are only evaluated against a
        // non-empty result set, so NOT_EXISTS matches only when the path exists but yields empty content.
        assertThat(matches(data, xpath("/Events/Event", XPathFilter.MatchType.NOT_EXISTS, null))).isFalse();
    }

    @Test
    void testXPathEqualsAndContains() {
        final CapturedElementData data = xmlOutput(EVENTS_XML);
        assertThat(matches(data, xpath("/Events/Event/Id", XPathFilter.MatchType.EQUALS, "hello world"))).isTrue();
        assertThat(matches(data, xpath("/Events/Event/Id", XPathFilter.MatchType.EQUALS, "nope"))).isFalse();
        assertThat(matches(data, xpath("/Events/Event/Id", XPathFilter.MatchType.CONTAINS, "world"))).isTrue();
        assertThat(matches(data, xpath("/Events/Event/Id", XPathFilter.MatchType.CONTAINS, "xyz"))).isFalse();
    }

    @Test
    void testXPathNoOutputXmlDoesNotMatch() {
        // An element with no output XML cannot match an XPath filter.
        assertThat(matches(data(false, null), xpath("/Events/Event", XPathFilter.MatchType.EXISTS, null))).isFalse();
    }

    @Test
    void testEarlierMatchShortCircuitsBeforeXPath() {
        // A severity/output hit must short-circuit and return before the XPath branch is evaluated.
        final SteppingFilterSettings severityAndXpath = new SteppingFilterSettings(
                Severity.ERROR, null, List.of(new XPathFilter()));
        assertThat(matches(data(false, Severity.ERROR), severityAndXpath)).isTrue();
    }
}
