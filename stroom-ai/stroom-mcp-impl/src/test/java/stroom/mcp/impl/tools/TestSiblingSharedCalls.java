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

import stroom.ai.impl.tools.generation.SharedTemplate;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TestSiblingSharedCalls {

    private static final String META = "Stream-Meta-V1";
    private static final String USERS = "Common-Index-V1";

    @Test
    void onlyACallMadeByTwoPipelinesCountsAndTheMostUsedWinsItsPlace() {
        final List<SiblingSharedCalls.Call> calls = SiblingSharedCalls.calls(List.of(
                usage(META, "eventMeta", List.of("Meta"), "A", "B", "C"),
                usage("Other-Meta", "otherMeta", List.of("Meta"), "D", "E"),
                usage(META, "deviceOnly", List.of("EventSource/Device"), "A"),
                usage(META, null, List.of(), "A", "B")), false);

        assertThat(calls).hasSize(1);
        assertThat(calls.getFirst().template()).isEqualTo("eventMeta");
        assertThat(calls.getFirst().usedBy()).containsExactly("A", "B", "C");
    }

    @Test
    void anIndexingCallsPlacesAreTheDocumentsKeys() {
        final List<SiblingSharedCalls.Call> calls = SiblingSharedCalls.calls(List.of(
                usage(USERS, "userId", List.of("user/id"), "A", "B")), true);

        assertThat(calls.getFirst().places()).containsExactly("user.id");
        assertThat(calls.getFirst().asShared()).isEqualTo(new SharedTemplate(USERS, "userId", "user.id", Map.of()));
    }

    @Test
    void aPipelineNamedTwiceCountsOnce() {
        assertThat(SiblingSharedCalls.calls(List.of(usage(META, "eventMeta", List.of("Meta"), "A", "A")), false))
                .isEmpty();
    }

    @Test
    void anXsltCallingTheTemplateFromTheSameImportFollowsIt() {
        final List<SiblingSharedCalls.Call> calls = List.of(call(META, "eventMeta", "Meta"));
        final String calling = xslt(META, "<xsl:call-template name=\"eventMeta\"/>");

        assertThat(SiblingSharedCalls.notFollowed(calls, calling, null)).isEmpty();
        // The same name from another import is another template.
        assertThat(SiblingSharedCalls.notFollowed(calls, xslt("Mine", "<xsl:call-template name=\"eventMeta\"/>"),
                null)).hasSize(1);
    }

    @Test
    void aWarningSaysWhetherTheXsltWritesThePlaceItselfOrLeavesItOut() {
        final List<SiblingSharedCalls.Call> calls = List.of(call(META, "eventMeta", "Meta"));
        final String code = xslt(META, "");

        assertThat(SiblingSharedCalls.notFollowed(calls, code, "Meta"::equals).getFirst())
                .startsWith("Meta: 2 existing pipelines (A, B) write it with eventMeta from 'Stream-Meta-V1'")
                .contains("but this XSLT writes it itself", "shared {href=Stream-Meta-V1, template=eventMeta, "
                                                            + "at=Meta}");
        assertThat(SiblingSharedCalls.notFollowed(calls, code, at -> false).getFirst()).contains("leaves it out");
        assertThat(SiblingSharedCalls.notFollowed(calls, code, null).getFirst()).contains("doesn't call it");
    }

    @Test
    void aDraftedMappingCallsThePipelinesTemplateInPlaceOfWhatItMappedThere() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", List.of(Map.of("path", "Meta/OriginId", "field", "guid"),
                Map.of("path", "MetaData", "value", "x"),
                Map.of("path", "EventSource/System/Name", "value", "Acme")));
        mapping.put("events", List.of(Map.of("name", "logon", "fields", List.of(
                Map.of("path", "Meta", "value", "y"),
                Map.of("path", "EventDetail/TypeId", "value", "Logon")))));

        final List<String> notes = SiblingSharedCalls.intoMapping(List.of(call(META, "eventMeta", "Meta")), mapping);

        assertThat(mapping.get("shared")).isEqualTo(List.of(Map.of("href", META, "template", "eventMeta", "at",
                "Meta")));
        assertThat(paths(mapping.get("common"))).containsExactly("MetaData", "EventSource/System/Name");
        assertThat(paths(((Map<?, ?>) ((List<?>) mapping.get("events")).getFirst()).get("fields")))
                .containsExactly("EventDetail/TypeId");
        assertThat(notes).singleElement().asString().contains("so this mapping calls it too (shared), in place of "
                                                              + "['Meta/OriginId', 'Meta']");
    }

    @Test
    void aCallWithParametersOrOfSeveralElementsIsLeftToTheAgent() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", List.of(Map.of("path", "Meta/OriginId", "field", "guid")));
        final SiblingSharedCalls.Call withParams = new SiblingSharedCalls.Call(META, "eventMeta", List.of("Meta"),
                Map.of("guid", "data[@name='guid']/@value"), List.of("A", "B"));
        final SiblingSharedCalls.Call several = new SiblingSharedCalls.Call(META, "both", List.of("EventTime",
                "EventSource"), Map.of(), List.of("A", "B"));

        final List<String> notes = SiblingSharedCalls.intoMapping(List.of(withParams, several), mapping);

        assertThat(mapping).doesNotContainKey("shared");
        assertThat(paths(mapping.get("common"))).containsExactly("Meta/OriginId");
        assertThat(notes).hasSize(2);
        assertThat(notes.getFirst()).contains("with parameters [guid]", "this source's own values");
        assertThat(notes.get(1)).startsWith("EventTime, EventSource: ");
    }

    @Test
    void aMappingAlreadyCallingATemplateThereKeepsItsOwn() {
        final Map<String, Object> mapping = new LinkedHashMap<>();
        mapping.put("common", List.of());
        mapping.put("shared", List.of(Map.of("href", "Mine", "template", "myMeta", "at", "Meta")));

        assertThat(SiblingSharedCalls.intoMapping(List.of(call(META, "eventMeta", "Meta")), mapping)).isEmpty();
        assertThat(mapping.get("shared")).isEqualTo(List.of(Map.of("href", "Mine", "template", "myMeta", "at",
                "Meta")));
    }

    @Test
    void aPlanTakesTheCallsItCanAndNotesTheRest() {
        final List<String> notes = new ArrayList<>();
        final List<SharedTemplate> shared = SiblingSharedCalls.forPlan(List.of(call(USERS, "userId", "user.id"),
                new SiblingSharedCalls.Call(USERS, "host", List.of("host.name"), Map.of("h", "x"), List.of("A",
                        "B"))), notes);

        assertThat(shared).containsExactly(new SharedTemplate(USERS, "userId", "user.id", Map.of()));
        assertThat(notes).hasSize(2);
        assertThat(notes.getFirst()).contains("so this plan calls it too", "shared=[]");
        assertThat(notes.get(1)).contains("Give shared with it");
    }

    @Test
    void theStagesCallsAreReadFromItsTemplatesChildren() {
        final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
        final List<Map<String, Object>> children = List.of(Map.of("name", "A"), Map.of("name", "B"));
        Mockito.when(templates.find("translation")).thenReturn(Map.of("candidates", List.of(
                Map.of("uuid", "t1", "children", 2),
                Map.of("uuid", "t2", "children", 0))));
        Mockito.when(templates.children("t1")).thenReturn(children);
        Mockito.when(templates.sharedXsltUsage(children)).thenReturn(List.of(usage(META, "eventMeta",
                List.of("Meta"), "A", "B")));

        assertThat(new SiblingSharedCalls(templates).forStage("translation"))
                .extracting(SiblingSharedCalls.Call::template)
                .containsExactly("eventMeta");
        Mockito.verify(templates, Mockito.never()).children("t2");
    }

    @Test
    void noneWhenThePipelinesCantBeRead() {
        final PipelineTemplates templates = Mockito.mock(PipelineTemplates.class);
        Mockito.when(templates.find("indexing")).thenThrow(new IllegalStateException("no index"));

        assertThat(new SiblingSharedCalls(templates).forStage("indexing")).isEmpty();
    }

    private static Map<String, Object> usage(final String href,
                                             final String template,
                                             final List<String> at,
                                             final String... usedBy) {
        final Map<String, Object> row = new LinkedHashMap<>();
        row.put("href", href);
        row.put("template", template);
        row.put("at", at);
        row.put("used_by", List.of(usedBy));
        return row;
    }

    private static SiblingSharedCalls.Call call(final String href, final String template, final String at) {
        return new SiblingSharedCalls.Call(href, template, List.of(at), Map.of(), List.of("A", "B"));
    }

    private static String xslt(final String imported, final String body) {
        return "<xsl:stylesheet xmlns:xsl=\"http://www.w3.org/1999/XSL/Transform\" version=\"3.0\">"
               + "<xsl:import href=\"" + imported + "\"/><xsl:template match=\"Event\">" + body
               + "</xsl:template></xsl:stylesheet>";
    }

    private static List<Object> paths(final Object entries) {
        return ((List<?>) entries).stream().map(entry -> ((Map<?, ?>) entry).get("path")).map(Object.class::cast)
                .toList();
    }
}
