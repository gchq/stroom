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

import stroom.ai.impl.tools.AgentInstructions;
import stroom.ai.impl.tools.DocumentAccess;
import stroom.ai.impl.tools.InstructionRules.RequiredImport;
import stroom.ai.impl.tools.KeptMapping;
import stroom.ai.impl.tools.ToolCall;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.validation.XsltVersionHistory;
import stroom.docref.DocRef;
import stroom.docstore.api.DocumentActionHandler;
import stroom.pipeline.shared.XsltDoc;
import stroom.security.api.SecurityContext;
import stroom.util.shared.UserRef;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;

/// Saving an XSLT in a build, as the Stroom MCP server's `tools/translation.py` `create_xslt` and `update_xslt` did
/// for draft_cef_mapping: checked first, refused without the imports the instructions require, kept with its plan,
/// tagged, its change pending in its version history, and a hand edit it would undo refused.
class TestXsltSaves {

    private static final String XSL = "http://www.w3.org/1999/XSL/Transform";
    private static final String SAVED = "<xsl:stylesheet xmlns:xsl=\"" + XSL + "\" version=\"2.0\">"
                                        + "<xsl:output method=\"text\"/><xsl:template match=\"/\">"
                                        + "<a><xsl:value-of select=\"A\"/></a></xsl:template></xsl:stylesheet>";
    private static final String EDITED = "<xsl:stylesheet xmlns:xsl=\"" + XSL + "\" version=\"2.0\">"
                                         + "<xsl:output method=\"text\"/><xsl:template match=\"/\">"
                                         + "<a><xsl:value-of select=\"A\"/></a><b><xsl:value-of select=\"B\"/></b>"
                                         + "</xsl:template></xsl:stylesheet>";

    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final WriteGuard guard = Mockito.mock(WriteGuard.class);
    private final CheckXsltTool checkXslt = Mockito.mock(CheckXsltTool.class);
    private final BuildPlan plan = Mockito.mock(BuildPlan.class);
    private final AgentInstructions agentInstructions = Mockito.mock(AgentInstructions.class);
    private final Consent consent = Mockito.mock(Consent.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final XsltSaves saves = new XsltSaves(documentAccess, guard, checkXslt, plan, agentInstructions, consent,
            () -> securityContext);
    private final Map<String, XsltDoc> docs = new LinkedHashMap<>();
    private final List<XsltDoc> written = new ArrayList<>();
    private final ToolCallContext context = new SteppingWorld().context();

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        Mockito.when(checkXslt.check(anyString(), any(), any())).thenReturn(Map.of("ok", true, "errors", List.of()));
        Mockito.when(agentInstructions.importsRequired(any(), anyString(), anyList(), anyList())).thenReturn(
                List.of());
        Mockito.when(guard.buildFolder(anyString(), eq(false))).thenReturn(Optional.empty());
        Mockito.when(guard.buildOf(any())).thenReturn(Optional.of("b"));
        Mockito.when(guard.tags(any())).thenReturn(Set.of());
        Mockito.when(plan.sampleFormat(anyString())).thenReturn(Optional.empty());
        Mockito.when(plan.withNext(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        Mockito.when(plan.nextFor(any(), any(), any())).thenAnswer(inv -> inv.getArgument(2));
        Mockito.when(securityContext.getUserRef()).thenReturn(UserRef.builder().uuid("u").subjectId("jane")
                .displayName("jane").build());
        Mockito.when(guard.createFilled(eq(XsltDoc.TYPE), anyString(), anyString(), any(), eq(false), any()))
                .thenAnswer(inv -> {
                    final String name = inv.getArgument(1);
                    docs.put("x-new", XsltDoc.builder().uuid("x-new").name(name).version("v0").build());
                    return ((Function<DocRef, Object>) inv.getArgument(3)).apply(new DocRef(XsltDoc.TYPE, "x-new",
                            name));
                });
        Mockito.when(documentAccess.read(any(DocRef.class), eq(XsltDoc.class))).thenAnswer(inv ->
                Optional.ofNullable(docs.get(((DocRef) inv.getArgument(0)).getUuid())));
        final DocumentActionHandler<XsltDoc> handler = Mockito.mock(DocumentActionHandler.class);
        Mockito.when(handler.writeDocument(any())).thenAnswer(inv -> {
            final XsltDoc doc = inv.getArgument(0);
            written.add(doc);
            final XsltDoc saved = doc.copy().version("v" + written.size()).build();
            docs.put(doc.getUuid(), saved);
            return saved;
        });
        Mockito.when(documentAccess.handler(XsltDoc.TYPE)).thenReturn(Optional.of(handler));
    }

    @Test
    void anXsltIsCreatedWithItsPlanKeptItsChangePendingAndTagged() {
        final KeptMapping.Kept kept = new KeptMapping.Kept(XsltSaves.CEF, Map.of("topic", "t"));

        final Map<String, Object> saved = saves.create(context, "b", "CEF", SAVED, kept, null);

        assertThat(saved).containsEntry("uuid", "x-new").containsEntry("name", "CEF").containsEntry("version", "v1");
        final XsltDoc doc = written.getLast();
        assertThat(doc.getData()).isEqualTo(SAVED);
        assertThat(KeptMapping.readMapping(doc.getDescription())).contains(kept);
        assertThat(XsltVersionHistory.untouched(doc.getDescription(), SAVED)).contains(true);
        assertThat(XsltVersionHistory.pendingOf(doc.getDescription()).orElseThrow().toString()).contains("Created")
                .contains("jane");
        Mockito.verify(guard).createFilled(eq(XsltDoc.TYPE), eq("CEF"), eq("b"), any(), eq(false), eq("CEF"));
        Mockito.verify(guard).tag(List.of(new DocRef(XsltDoc.TYPE, "x-new", "CEF")), Set.of(WriteGuard.KEPT_MAPPING));
        Mockito.verify(plan).withNext(any(), eq("b"), any());
    }

    @Test
    void anXsltCheckXsltRejectsIsNotSaved() {
        Mockito.when(checkXslt.check(anyString(), any(), any())).thenReturn(Map.of("ok", false, "errors",
                List.of("stroom:bogus() is not a Stroom function")));

        assertThatThrownBy(() -> saves.create(context, "b", "CEF", SAVED, null, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("XSLT not saved: stroom:bogus() is not a Stroom function");
        assertThat(written).isEmpty();
    }

    @Test
    void anXsltWithoutTheImportsTheInstructionsRequireIsRefused() {
        Mockito.when(agentInstructions.importsRequired(any(), eq("cef"), anyList(), anyList())).thenReturn(List.of(
                new RequiredImport("CEF-Shared", "/Feeds/ACME")));

        assertThatThrownBy(() -> saves.create(context, "b", "CEF", SAVED, null, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessage("Standing instructions require every cef XSLT to import 'CEF-Shared' (the AGENTS doc in "
                            + "/Feeds/ACME): add <xsl:import href=\"CEF-Shared\"/> first in the stylesheet, or "
                            + "build it from a mapping (build_translation_xslt adds them)");
    }

    @Test
    void codeThatUndoesAHandEditIsRefusedSayingWhatItChanged() {
        docs.put("x-1", XsltDoc.builder().uuid("x-1").name("CEF").version("v3").updateUser("bob").data(EDITED)
                .description(XsltVersionHistory.withPending(null, null, "jane", "Created", "Stroom AI tools",
                        Instant.now(), SAVED))
                .build());

        assertThatThrownBy(() -> saves.update(call(), context, "x-1", SAVED, null, null, false, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("Not saved: XSLT 'CEF' was edited by hand since the tools saved it (by bob), "
                                        + "and this code undoes the edit: it no longer writes")
                .hasMessageEndingWith("Only if the user says to drop their edit: discard_hand_edit=true.");
        assertThat(written).isEmpty();
    }

    @Test
    void aHandEditTheUserSaidToDropIsSavedOver() {
        docs.put("x-1", XsltDoc.builder().uuid("x-1").name("CEF").version("v3").updateUser("bob").data(EDITED)
                .description(XsltVersionHistory.withPending(null, null, "jane", "Created", "Stroom AI tools",
                        Instant.now(), SAVED))
                .build());

        final Map<String, Object> saved = saves.update(call(), context, "x-1", SAVED, null, "Back as it was", true,
                null);

        assertThat(saved).containsEntry("uuid", "x-1");
        assertThat(written.getLast().getData()).isEqualTo(SAVED);
        assertThat(XsltVersionHistory.pendingOf(written.getLast().getDescription()).orElseThrow().toString())
                .contains("Back as it was");
        Mockito.verify(guard).checkManaged(any(), eq(new DocRef(XsltDoc.TYPE, "x-1", "CEF")));
        Mockito.verify(plan).nextFor(any(), any(), any());
    }

    @Test
    void codeThatKeepsTheHandEditIsSaved() {
        docs.put("x-1", XsltDoc.builder().uuid("x-1").name("CEF").version("v3").data(EDITED)
                .description(XsltVersionHistory.withPending(null, null, "jane", "Created", "Stroom AI tools",
                        Instant.now(), SAVED))
                .build());

        final Map<String, Object> saved = saves.update(call(), context, "x-1", EDITED, null, null, false, null);

        assertThat(saved).containsEntry("uuid", "x-1");
        Mockito.verify(consent, Mockito.never()).choose(any(), any(), anyString(), anyString(), anyList(), isNull());
    }

    @Test
    void anXsltNotTheToolsOwnIsRefused() {
        docs.put("x-2", XsltDoc.builder().uuid("x-2").name("Theirs").data(SAVED).build());
        Mockito.when(guard.checkManaged(any(), any())).thenThrow(new ToolRefusal("XSLT 'Theirs' was not created by "
                                                                                 + "this server."));

        assertThatThrownBy(() -> saves.update(call(), context, "x-2", SAVED, null, null, false, null))
                .isInstanceOf(ToolRefusal.class)
                .hasMessageStartingWith("XSLT 'Theirs' was not created");
    }

    @Test
    void aLostMappingIsNamedForAnXsltTaggedAsKeptWithOne() {
        final DocRef ref = new DocRef(XsltDoc.TYPE, "x-1", "CEF");
        assertThat(saves.lostMapping(ref, "no mapping here")).isEmpty();

        Mockito.when(guard.tags(ref)).thenReturn(Set.of(WriteGuard.KEPT_MAPPING));

        assertThat(saves.lostMapping(ref, "no mapping here")).hasValueSatisfying(lost -> assertThat(lost)
                .startsWith("XSLT 'CEF' was saved by stroom-mcp with the mapping (or plan) it is generated from")
                .contains("rebuild_mapping uuid='x-1'"));
        assertThat(saves.lostMapping(ref, KeptMapping.withMapping(null, "cef", Map.of("topic", "t")))).isEmpty();
    }

    @Test
    void anXsltsRoleIsWhatItWrites() {
        assertThat(XsltSaves.xsltRole("<Events xmlns=\"event-logging:3\"/>")).isEqualTo("Events");
        assertThat(XsltSaves.xsltRole("<referenceData/>")).isEqualTo("Reference");
        assertThat(XsltSaves.xsltRole("<kafkaRecords xmlns=\"kafka-records:1\"/>")).isEqualTo("CEF");
        assertThat(XsltSaves.xsltRole(SAVED)).isEqualTo("CEF");
        assertThat(XsltSaves.xsltRole("<array/>")).isEqualTo("Indexing");
        assertThat(XsltSaves.xsltRole("<records xmlns=\"event-logging:3\"/>")).isEqualTo("Indexing");
        assertThat(XsltSaves.xsltRole("<records/>")).isEqualTo("Records");
        assertThat(XsltSaves.xsltRole("<other/>")).isNull();
    }

    private static ToolCall call() {
        return new ToolCall("1", DraftCefMappingTool.NAME, "{}");
    }
}
