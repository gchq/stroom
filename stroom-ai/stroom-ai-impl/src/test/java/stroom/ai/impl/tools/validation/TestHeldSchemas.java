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

package stroom.ai.impl.tools.validation;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/// The schemas a Stroom holds, and a schema location naming a system id it doesn't hold. Ported from the Stroom
/// MCP server's `tests/test_schema_locations.py` and the schema cases of `tests/test_event_versions.py`. Seen on
/// Stroom 7.13: file:///event-logging-v4.1.0.xsd (three slashes) was not refused as unknown; the schema filter
/// validated against event-logging 3.2.3's schema instead, refusing Version 4.1.0, and the error hid the cause.
class TestHeldSchemas {

    private final HeldSchemas held = new HeldSchemasWorld()
            .hold("file://event-logging-v3.2.3.xsd", "event-logging:3", HeldSchemasWorld.XSD_4_1_0)
            .hold("file://event-logging-v4.1.0.xsd", "event-logging:3", HeldSchemasWorld.XSD_4_1_0)
            .hold("file://records-v2.0.xsd", "records:2", HeldSchemasWorld.XSD_4_1_0)
            .heldSchemas();

    @Test
    void testThreeSlashesAreRefusedWithTheIdStroomHolds() {
        final List<String> problems = problems("file:///event-logging-v4.1.0.xsd");
        assertThat(problems).singleElement().asString()
                .contains("write 'file://event-logging-v4.1.0.xsd' (file: and two slashes)")
                .contains("event-logging 3.2.3's, refusing Version 4.1.0");
    }

    @Test
    void testABareFileNameIsRefusedToo() {
        assertThat(problems("event-logging-v4.1.0.xsd")).singleElement().asString()
                .contains("write 'file://event-logging-v4.1.0.xsd'");
    }

    @Test
    void testAVersionStroomDoesntHoldListsThoseItDoesNewestFirst() {
        assertThat(problems("file://event-logging-v3.5.2.xsd")).singleElement().asString()
                .contains("it holds file://event-logging-v4.1.0.xsd, file://event-logging-v3.2.3.xsd.");
    }

    @Test
    void testHeldIdsUnknownNamespacesAndTemplatesAreNotJudged() {
        assertThat(problems("file://event-logging-v4.1.0.xsd")).isEmpty();
        assertThat(held.locationProblems(XmlTrees.parse(
                "<x xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" "
                + "xsi:schemaLocation=\"acme:1 file:///acme.xsd\"/>"))).isEmpty();
        assertThat(problems("file://event-logging-v{$version}.xsd")).isEmpty();
    }

    @Test
    void testValidatingADocumentThatNamesItSaysWhatToWrite() {
        assertThatThrownBy(() -> held.source("file:///event-logging-v4.1.0.xsd"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("write 'file://event-logging-v4.1.0.xsd' (file: and two slashes)");
    }

    @Test
    void testOnlyTheMostRecentlyUsedSchemaModelsAreKept() {
        // Before, a model was kept for every XSD text ever read (each edit of an XSD is a new text), for good.
        final HeldSchemasWorld world = new HeldSchemasWorld();
        final int versions = HeldSchemas.MAX_EVENT_SCHEMAS + 2;
        for (int i = 0; i < versions; i++) {
            world.hold("file://event-logging-v4.1." + i + ".xsd", "event-logging:3",
                    HeldSchemasWorld.XSD_4_1_0 + "<!-- " + i + " -->");
        }
        final HeldSchemas schemas = world.heldSchemas();
        for (int i = 0; i < versions; i++) {
            final EventSchema schema = schemas.eventSchema("4.1." + i);
            assertThat(schemas.eventSchema("4.1." + i)).isSameAs(schema);
        }
        assertThat(schemas.eventSchemasKept()).isEqualTo(HeldSchemas.MAX_EVENT_SCHEMAS);
    }

    @Test
    void testTheVersionsStroomHoldsComeNewestFirst() {
        final HeldSchemas versions = new HeldSchemasWorld()
                .hold("file://event-logging-v3.5.2.xsd", "event-logging:3", "")
                .hold("file://event-logging-v4.0.2.xsd", "event-logging:3", "")
                .hold("file://records-v2.0.xsd", "records:2", "")
                .hold("file://event-logging-v3.10.0.xsd", "event-logging:3", "")
                .heldSchemas();
        // By number, not as text.
        assertThat(versions.eventVersions()).containsExactly("4.0.2", "3.10.0", "3.5.2");
    }

    @Test
    void testASchemaStroomDoesntHoldIsRefusedWithItsVersionsNewestFirst() {
        final HeldSchemas versions = new HeldSchemasWorld()
                .hold("file://event-logging-v3.4.2.xsd", "event-logging:3", "")
                .hold("file://event-logging-v4.1.0.xsd", "event-logging:3", "")
                .hold("file://records-v2.0.xsd", "records:2", "")
                .heldSchemas();
        assertThatThrownBy(() -> versions.source("file://event-logging-v3.5.2.xsd"))
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("newest first: 4.1.0, 3.4.2 (all in namespace event-logging:3");
    }

    @Test
    void testNoEventLoggingSchemaAtAllSaysToImportIt() {
        final HeldSchemas none = new HeldSchemasWorld().heldSchemas();
        assertThat(none.heldEventVersions()).isEmpty();
        assertThatThrownBy(none::eventVersions)
                .isInstanceOf(ValidationException.class)
                .hasMessageContaining("holds no event-logging XML schema");
        assertThatThrownBy(() -> none.chosenOrLatest(null)).isInstanceOf(ValidationException.class);
    }

    @Test
    void testASchemaTheUserCantViewIsNotHeldForThem() {
        final HeldSchemas hidden = new HeldSchemasWorld()
                .holdUnviewable("file://event-logging-v4.1.0.xsd")
                .hold("file://event-logging-v3.5.2.xsd", "event-logging:3", "")
                .heldSchemas();
        assertThat(hidden.eventVersions()).containsExactly("3.5.2");
        assertThat(hidden.systemIds()).containsExactly("file://event-logging-v3.5.2.xsd");
    }

    @Test
    void testAnExistingTranslationKeepsItsOwnVersion() {
        final String code = "<Events xsi:schemaLocation=\"event-logging:3 file://event-logging-v3.5.2.xsd\" "
                            + "Version=\"3.5.2\">";
        // Kept with it, the one its code writes, else the newest.
        assertThat(held.translationVersion("4.0.2", code)).isEqualTo("4.0.2");
        assertThat(held.translationVersion(null, code)).isEqualTo("3.5.2");
        assertThat(held.translationVersion(null, "<xsl:stylesheet/>")).isEqualTo("4.1.0");
        assertThat(held.chosenOrLatest("3.2.3")).isEqualTo("3.2.3");
        assertThat(held.chosenOrLatest(null)).isEqualTo("4.1.0");
    }

    @Test
    void testTheDeclaredSystemIdAndVersionAreRead() {
        final String xml = "<Events xmlns=\"event-logging:3\" xsi:schemaLocation=\"event-logging:3 "
                           + "file://event-logging-v4.1.0.xsd\" Version=\"4.1.0\"/>";
        assertThat(HeldSchemas.declaredSystemId(xml)).isEqualTo("file://event-logging-v4.1.0.xsd");
        assertThat(HeldSchemas.declaredSystemId("<Events/>")).isNull();
        assertThat(HeldSchemas.eventLoggingVersion(xml)).isEqualTo("4.1.0");
        assertThat(HeldSchemas.eventLoggingSystemId("3.5.2")).isEqualTo("file://event-logging-v3.5.2.xsd");
    }

    @Test
    void testTheEventSchemaOfAVersionIsParsedOnce() {
        final EventSchema first = held.eventSchema("4.1.0");
        assertThat(held.eventSchema("4.1.0")).isSameAs(first);
        assertThat(first.version()).isEqualTo("4.1.0");
        assertThatThrownBy(() -> held.eventSchema("9.9.9")).isInstanceOf(ValidationException.class);
    }

    @Test
    void testASchemaIsCompiledThroughStroomsPoolAndGivenBack() {
        final HeldSchemasWorld world = new HeldSchemasWorld()
                .hold("file://event-logging-v4.1.0.xsd", "event-logging:3", HeldSchemasWorld.XSD_4_1_0);
        world.heldSchemas().validate("file://event-logging-v4.1.0.xsd", "<Events xmlns=\"event-logging:3\" "
                                                                        + "Version=\"4.1.0\"/>");
        Mockito.verify(world.pool()).borrowObject(Mockito.any(), Mockito.eq(true));
        Mockito.verify(world.pool()).returnObject(Mockito.any(), Mockito.eq(true));
    }

    private List<String> problems(final String location) {
        final String xslt = "<xsl:stylesheet version=\"2.0\" xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" "
                            + "xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\" xmlns=\"event-logging:3\">"
                            + "<xsl:template match=\"/\"><Events xsi:schemaLocation=\"event-logging:3 " + location
                            + "\" Version=\"4.1.0\"/></xsl:template></xsl:stylesheet>";
        return held.locationProblems(XmlTrees.parse(xslt));
    }
}
