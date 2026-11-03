/*
 * Copyright 2026 Crown Copyright
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

package stroom.ai.impl.tools.generation;

import stroom.ai.impl.tools.validation.EventSchema;
import stroom.ai.impl.tools.validation.HeldSchemasWorld;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// Translations written in an environment's fork of the event-logging schema: generated in the fork's namespace,
/// valid against the fork's schema, and read back as a translation in event-logging:3 is.
class TestForkedEvents extends XsltRuns {

    private static final String FORK_NS = HeldSchemasWorld.FORK_NS;
    private static final EventSchema FORK = EventSchema.parse(HeldSchemasWorld.XSD_FORK)
            .withSystemId(HeldSchemasWorld.FORK_SYSTEM_ID);

    @Test
    void theForksSchemaKnowsItsNamespaceSystemIdAndExtraActivity() {
        assertThat(FORK.namespace()).isEqualTo(FORK_NS);
        assertThat(FORK.isFork()).isTrue();
        assertThat(FORK.systemId()).isEqualTo(HeldSchemasWorld.FORK_SYSTEM_ID);
        // Its own version, not event-logging's.
        assertThat(FORK.version()).isEqualTo("1.2.0");
        assertThat(FORK.hasPath("EventDetail/Charge/Account")).isTrue();
        // The standard schema has no Charge, and is not a fork.
        assertThat(SCHEMA.namespace()).isEqualTo("event-logging:3");
        assertThat(SCHEMA.isFork()).isFalse();
        assertThat(SCHEMA.hasPath("EventDetail/Charge/Account")).isFalse();
        // A copy knowing its system id shares the rest.
        assertThat(SCHEMA.withSystemId(null)).isSameAs(SCHEMA);
        assertThat(SCHEMA.withSystemId("file://event-logging-v4.1.0.xsd").pathsNamed("User"))
                .isEqualTo(SCHEMA.pathsNamed("User"));
    }

    @Test
    void aTranslationForAForkWritesValidEventsInItsNamespace() {
        final XsltGenerator.Generated result = XsltGenerator.generate(mapping(), FORK, "1.2.0");

        assertThat(result.ok()).as(String.valueOf(result.problems())).isTrue();
        assertThat(result.xslt()).contains("xmlns=\"" + FORK_NS + "\"")
                .contains("xsi:schemaLocation=\"" + FORK_NS + " " + HeldSchemasWorld.FORK_SYSTEM_ID + "\"")
                .doesNotContain("event-logging:3");
        final XmlElement events = transform(result.xslt(), RECORDS);
        assertThat(events.namespace()).isEqualTo(FORK_NS);
        assertThat(children(events, "Event")).hasSize(2);
        assertThat(invalid(events, HeldSchemasWorld.XSD_FORK)).isEmpty();
        // Not event-logging's Events.
        assertThat(invalid(events, XSD)).isNotEmpty();
    }

    @Test
    void theForksOwnActivityIsMappedForItAndRefusedForEventLogging() {
        final Map<String, Object> charge = rule("charge", List.of(equalsCondition("action", "login")), json("""
                [{"path": "EventDetail/TypeId", "value": "Charge"},
                 {"path": "EventDetail/Charge/Account", "field": "user"}]"""));
        final TranslationMapping mapping = mapping(with("events", List.of(charge, otherRule())));

        final XsltGenerator.Generated forked = XsltGenerator.generate(mapping, FORK, "1.2.0");

        assertThat(forked.ok()).as(String.valueOf(forked.problems())).isTrue();
        final XmlElement events = transform(forked.xslt(), RECORDS);
        assertThat(invalid(events, HeldSchemasWorld.XSD_FORK)).isEmpty();
        assertThat(text(children(events, "Event").getFirst(), "EventDetail/Charge/Account")).isEqualTo("o'neil");
        assertThat(XsltGenerator.generate(mapping, SCHEMA, "4.1.0").ok()).isFalse();
    }

    @Test
    void aStandardSchemaThatKnowsItsSystemIdWritesWhatItAlwaysDid() {
        assertThat(XsltGenerator.generate(mapping(), SCHEMA.withSystemId("file://event-logging-v4.1.0.xsd"),
                "4.1.0").xslt()).isEqualTo(XsltGenerator.generate(mapping(), SCHEMA, "4.1.0").xslt());
    }

    @Test
    void aForksTranslationIsReadBackAsAStandardOneIs() {
        final String standard = XsltGenerator.generate(mapping(), SCHEMA, "4.1.0").xslt();
        final String forked = XsltGenerator.generate(mapping(), FORK, "1.2.0").xslt();

        assertThat(XsltReader.rebuild(forked, null, null).mapping())
                .isEqualTo(XsltReader.rebuild(standard, null, null).mapping());
        assertThat(XsltReader.read(forked).rules()).hasSameSizeAs(XsltReader.read(standard).rules());
    }

    @Test
    void onlyTheForksNamespaceIsRenamedNotASystemIdBeginningWithIt() {
        final String code = "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"" + FORK_NS
                            + "\"><xsl:template match=\"/\"><Events xsi:schemaLocation=\"" + FORK_NS + " " + FORK_NS
                            + "-v1.xsd\"/></xsl:template></xsl:stylesheet>";

        assertThat(ForkedEvents.forkedNamespace(code)).isEqualTo(FORK_NS);
        assertThat(ForkedEvents.asStandard(code))
                .contains("xmlns=\"event-logging:3\"")
                .contains("xsi:schemaLocation=\"event-logging:3 " + FORK_NS + "-v1.xsd\"");
        // Standard, not well-formed, or null: as it is.
        final String standard = code.replace(FORK_NS, "event-logging:3");
        assertThat(ForkedEvents.forkedNamespace(standard)).isNull();
        assertThat(ForkedEvents.asStandard(standard)).isSameAs(standard);
        assertThat(ForkedEvents.asStandard("<cut")).isEqualTo("<cut");
        assertThat(ForkedEvents.asStandard(null)).isNull();
    }
}
