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

package stroom.mcp.impl.tools;

import stroom.ai.impl.tools.catalogue.Catalogue;
import stroom.ai.impl.tools.catalogue.CatalogueDiff;
import stroom.ai.impl.tools.catalogue.EventType;
import stroom.ai.impl.tools.documents.KeptMapping;
import stroom.ai.impl.tools.draft.SourceNotes;
import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.feed.shared.FeedDoc;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/// The build's catalogue in its source notes: carried from the promoted notes into a next version's build, what
/// changed since, and written back.
class TestCatalogueNotes {

    private static final String BUILD = "acme-v2";

    private final BuildWorld world = new BuildWorld();
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final CatalogueNotes notes = new CatalogueNotes(world.guard(), world.documentAccess,
            new DocumentWrites(world.documentAccess), new FeedNames(() -> docFinder, world.documentAccess));

    @Test
    void aNextVersionStartsFromThePromotedCatalogueCarriedInAsAWorkingCopy() {
        final DocRef pipeline = promoted();

        final String said = notes.carryInto(BUILD, pipeline).orElseThrow();

        assertThat(said).contains("'Acme source notes' carried into the build", "2 event type(s)");
        final Catalogue carried = notes.catalogue(BUILD).orElseThrow();
        assertThat(carried.eventTypes()).extracting(EventType::typeId).containsExactly("Authenticate-Logon",
                "Authenticate-Logoff");
        assertThat(carried.eventTypes()).allSatisfy(type -> assertThat(type.confirmed()).isTrue());
        assertThat(notes.promoted(BUILD)).contains(carried);
        assertThat(notes.changedSincePromoted(BUILD).orElseThrow().none()).isTrue();
        // The build has notes now: not carried again.
        assertThat(notes.carryInto(BUILD, pipeline)).isEmpty();
    }

    @Test
    void aNextVersionStartsFromThePromotedCatalogueBesideItsFeedBeforeThePipelineIsCopied() {
        final DocRef pipeline = promoted();
        final DocRef feed = world.add(FeedDoc.TYPE, "ACME", world.folder("System/Feeds/Acme").uuid, "promoted-feed")
                .docRef();
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, "*", true)).thenReturn(List.of(feed));

        assertThat(notes.carryBesideFeeds(BUILD, List.of("OTHER"))).isEmpty();
        final String said = notes.carryBesideFeeds(BUILD, List.of("acme")).orElseThrow();

        assertThat(said).contains("'Acme source notes' carried into the build", "2 event type(s)");
        final Catalogue carried = notes.catalogue(BUILD).orElseThrow();
        assertThat(carried.eventTypes()).extracting(EventType::typeId).containsExactly("Authenticate-Logon",
                "Authenticate-Logoff");
        assertThat(carried.eventTypes()).allSatisfy(type -> assertThat(type.confirmed()).isTrue());
        assertThat(notes.promoted(BUILD)).contains(carried);
        // Carried once: neither again nor by the pipeline's copy later.
        assertThat(notes.carryBesideFeeds(BUILD, List.of("ACME"))).isEmpty();
        assertThat(notes.carryInto(BUILD, pipeline)).isEmpty();
        assertThat(world.docs.values().stream()
                .filter(DocumentationDoc.class::isInstance)
                .map(DocumentationDoc.class::cast)
                .filter(doc -> doc.getName().equals("Acme source notes")))
                .hasSize(2);
    }

    @Test
    void whatChangedSinceThePromotedCatalogueIsSaidAndTheRestOfTheNotesKept() {
        notes.carryInto(BUILD, promoted());
        final Catalogue carried = notes.catalogue(BUILD).orElseThrow();
        final List<EventType> types = new ArrayList<>(carried.eventTypes());
        types.removeFirst();
        notes.write(null, BUILD, "Acme", carried.withEventTypes(types));

        final CatalogueDiff.Diff diff = notes.changedSincePromoted(BUILD).orElseThrow();

        assertThat(diff.removed()).containsExactly("Authenticate-Logon");
        final String text = world.docs.values().stream()
                .filter(DocumentationDoc.class::isInstance)
                .map(DocumentationDoc.class::cast)
                .filter(doc -> doc.getName().equals("Acme source notes") && !doc.getUuid().equals("promoted-notes"))
                .findFirst()
                .orElseThrow()
                .getData();
        assertThat(text).startsWith("# Acme source notes\n\nAcme's VPN.");
        assertThat(SourceNotes.readNotes(text)).containsEntry("source", "Acme");
    }

    @Test
    void aBuildWithNoNotesGetsOneWhenItsCatalogueIsWritten() {
        assertThat(notes.catalogue(BUILD)).isEmpty();
        assertThat(notes.promoted(BUILD)).isEmpty();

        notes.write(null, BUILD, "Acme", new Catalogue(List.of(), Catalogue.CatchAll.CONFIRMED, null));

        assertThat(notes.catalogue(BUILD).orElseThrow().catchAll().confirmed()).isTrue();
        assertThat(notes.changedSincePromoted(BUILD)).isEmpty();
    }

    @Test
    void notesMadeAfterTheTranslationWasWrittenAreLinkedFromItsXsltItsOwnTextKept() {
        final String own = "Translates Acme's VPN.\n\nAsk the network team before changing it.";
        final DocRef xslt = world.inBuild(BUILD, XsltDoc.TYPE, "Acme-V2.0-EVENTS", "build-xslt");
        world.docs.put("build-xslt", XsltDoc.builder().uuid("build-xslt").name("Acme-V2.0-EVENTS").version("v0")
                .description(KeptMapping.withMapping(own, KeptMapping.TRANSLATION, Map.of("mapping", Map.of())))
                .build());
        // An XSLT keeping no translation mapping (an indexing one, say) is left alone.
        world.inBuild(BUILD, XsltDoc.TYPE, "Acme indexing", "build-index-xslt");

        notes.write(null, BUILD, "Acme", new Catalogue(List.of(), Catalogue.CatchAll.CONFIRMED, null));

        final String description = ((XsltDoc) world.docs.get(xslt.getUuid())).getDescription();
        final DocRef linked = KeptMapping.readSourceNotes(description).orElseThrow();
        assertThat(world.docs.get(linked.getUuid())).isInstanceOf(DocumentationDoc.class);
        assertThat(linked.getName()).isEqualTo("Acme source notes");
        assertThat(KeptMapping.withoutBlock(description, null)).isEqualTo(own);
        assertThat(KeptMapping.readSourceNotes(((XsltDoc) world.docs.get("build-index-xslt")).getDescription()))
                .isEmpty();
        // Linked already: not written again.
        final int saves = world.saves.get();
        assertThat(SourceNotesLinks.linkTranslations(world.guard(), world.documentAccess, BUILD)).isEmpty();
        assertThat(world.saves.get()).isEqualTo(saves);
    }

    @Test
    void notesCarriedIntoABuildAreLinkedByThePromotedDocTheWorkingCopyIsOf() {
        final DocRef pipeline = promoted();
        world.inBuild(BUILD, XsltDoc.TYPE, "Acme-V2.0-EVENTS", "build-xslt");
        world.docs.put("build-xslt", XsltDoc.builder().uuid("build-xslt").name("Acme-V2.0-EVENTS").version("v0")
                .description(KeptMapping.withMapping("", KeptMapping.TRANSLATION, Map.of("mapping", Map.of())))
                .build());

        notes.carryInto(BUILD, pipeline).orElseThrow();

        assertThat(KeptMapping.readSourceNotes(((XsltDoc) world.docs.get("build-xslt")).getDescription())
                .orElseThrow().getUuid()).isEqualTo("promoted-notes");
        assertThat(SourceNotesLinks.notesOf(world.guard(), BUILD).orElseThrow().getUuid())
                .isEqualTo("promoted-notes");
        assertThat(SourceNotesLinks.notesOf(world.guard(), null)).isEmpty();
        assertThat(SourceNotesLinks.linked(world.guard(), "no-such-build", "text")).isEqualTo("text");
    }

    @Test
    void theBlockIsReplacedInPlaceOrAddedAfterTheText() {
        assertThat(CatalogueNotes.withBlock("# A\n\ntext\n\n<!-- stroom-mcp source notes\n{}\n-->\n\nafter\n",
                "<!-- stroom-mcp source notes\n{\"a\": 1}\n-->"))
                .isEqualTo("# A\n\ntext\n\n<!-- stroom-mcp source notes\n{\"a\": 1}\n-->\n\nafter\n");
        assertThat(CatalogueNotes.withBlock("# A\n", "<!-- stroom-mcp source notes\n{}\n-->"))
                .isEqualTo("# A\n\n<!-- stroom-mcp source notes\n{}\n-->\n");
    }

    // A promoted feed: its pipeline in its Events folder, its source notes beside the feed.
    private DocRef promoted() {
        final BuildWorld.Node source = world.folder("System/Feeds/Acme");
        final BuildWorld.Node events = world.folder("System/Feeds/Acme/Events");
        final DocRef pipeline = world.add(PipelineDoc.TYPE, "Acme-Events", events.uuid, "promoted-pipeline")
                .docRef();
        world.add(DocumentationDoc.TYPE, "Acme source notes", source.uuid, "promoted-notes");
        final Map<String, Object> block = new LinkedHashMap<>();
        block.put("source", "Acme");
        block.put("fields", List.of());
        block.put("event_types", List.of(type("Authenticate-Logon", "login"), type("Authenticate-Logoff",
                "logout")));
        block.put("catch_all", Map.of("status", "confirmed"));
        world.docs.put("promoted-notes", DocumentationDoc.builder().uuid("promoted-notes").name("Acme source notes")
                .version("v0").data("# Acme source notes\n\nAcme's VPN.\n\n" + SourceNotes.block(block) + "\n")
                .build());
        world.folder("System/" + BuildWorld.WORKSPACE + "/" + BUILD);
        return pipeline;
    }

    private static Map<String, Object> type(final String typeId, final String value) {
        final Map<String, Object> type = new LinkedHashMap<>();
        type.put("type_id", typeId);
        type.put("event_detail", "Authenticate");
        type.put("recognised_by", List.of(Map.of("when", List.of(Map.of("field", "action", "equals", value)))));
        type.put("basis", "user_confirmed");
        type.put("status", "confirmed");
        return type;
    }
}
