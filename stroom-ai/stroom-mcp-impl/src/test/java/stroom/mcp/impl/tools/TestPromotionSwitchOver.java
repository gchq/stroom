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

import stroom.ai.impl.tools.StroomLinks;
import stroom.ai.impl.tools.documents.DocumentAccess;
import stroom.ai.impl.tools.processing.ProcessorFilterAccess;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.feed.shared.FeedDoc;
import stroom.importexport.shared.Dependency;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionItem;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;

/// How a promotion's new filters take over feeds old filters select (decided by the user, 2026-11-25): the old
/// filters found live, what moves and what stays, the switch-over in order, and the selection reported afterwards.
class TestPromotionSwitchOver {

    private static final DocRef V10 = new DocRef(PipelineDoc.TYPE, "v10", "ACME-V1.0-Events");
    private static final DocRef V11 = new DocRef(PipelineDoc.TYPE, "v11", "ACME-V1.1-Events");
    private static final DocRef OTHER = new DocRef(PipelineDoc.TYPE, "other", "Other-Events");
    private static final DocRef DICTIONARY = new DocRef(DictionaryDoc.TYPE, "dict-uuid-1", "ACME feeds");
    private static final String RAW = "Raw Events";

    private final List<ProcessorFilter> filters = new ArrayList<>();
    private final Map<String, DictionaryDoc> dictionaries = new HashMap<>();
    private final ProcessorFilters processorFilters = Mockito.mock(ProcessorFilters.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final DocDependencyService dependencies = Mockito.mock(DocDependencyService.class);
    private final StreamProcessing streamProcessing = Mockito.mock(StreamProcessing.class);
    private final PromotionSwitchOver switchOver = new PromotionSwitchOver(() -> processorFilters,
            new PipelineFeeds(Mockito.mock(ProcessorFilterAccess.class), documentAccess, null), () -> dependencies,
            streamProcessing);

    @BeforeEach
    void setUp() {
        Mockito.when(processorFilters.all()).thenAnswer(inv -> List.copyOf(filters));
        Mockito.when(documentAccess.read(any(DocRef.class), Mockito.eq(DictionaryDoc.class))).thenAnswer(inv ->
                Optional.ofNullable(dictionaries.get(((DocRef) inv.getArgument(0)).getUuid())));
        Mockito.when(dependencies.fetchDependencies(any(), any())).thenReturn(new ResultPage<>(List.of()));
    }

    @Test
    void aPredecessorReadingMoreFeedsThanTheBuildMovesOnlyTheBuildsAndTheRestStay() {
        // Seen in the audit (PromoteBuildTool 780-811): the next steps advised disabling V1.0's filter while it still
        // translated ACME-B.
        filter(12, V10, true, all(either(feed("ACME-A"), feed("ACME-B")), type(RAW)));
        Mockito.when(streamProcessing.openTasks(12, List.of("ACME-A"))).thenReturn(2);

        final List<List<PromotionSwitchOver.OldSelection>> overlaps = switchOver.overlaps(List.of(entry("ACME-A")),
                uuid -> false);

        assertThat(overlaps).singleElement().satisfies(found -> assertThat(found).singleElement()
                .satisfies(selection -> {
                    assertThat(selection.filterId()).isEqualTo(12);
                    assertThat(selection.moving()).containsExactly("ACME-A");
                    assertThat(selection.staying()).containsExactly("ACME-B");
                    assertThat(selection.inTerms()).containsExactly("ACME-A");
                    assertThat(selection.unlisted()).isNull();
                }));
        assertThat(PromotionSwitchOver.said(List.of(entry("ACME-A")), overlaps)).singleElement()
                .satisfies(said -> assertThat(said).contains("filter 12 of pipeline 'ACME-V1.0-Events' selects "
                                                             + "ACME-A, which moves", "ACME-B stays with it",
                        "the new filter of 'ACME-V1.1-Events' is made disabled, the switch-over the user's"));

        final List<Map<String, Object>> made = made(40);
        switchOver.handOver(StroomLinks.CHAT, overlaps, made);

        assertThat(made.getFirst().get("switch_over")).asList().containsExactly(
                "Remove ACME-A from filter 12 of pipeline [ACME-V1.0-Events](?action=open-doc&docType=Pipeline"
                + "&docUuid=v10) (its Processors tab); it goes on selecting ACME-B",
                "Let filter 12's in-flight tasks finish (2 open now for ACME-A; processing_status shows them): "
                + "[ACME-V1.0-Events](?action=open-doc&docType=Pipeline&docUuid=v10)",
                "Enable the new filter 40 ('ACME-V1.1-Events'): ?action=open-doc&docType=Pipeline&docUuid=v11");
        assertThat(made.getFirst()).containsEntry("enabled", false);
        final List<Map<String, Object>> actions = TestCreateProcessorFilterTool.actions(made.getFirst());
        assertThat(actions).extracting(action -> action.get("id")).containsExactly("switch-12", "enable-40");
        assertThat(String.valueOf(actions.getFirst().get("summary"))).contains("remove ACME-A from filter 12");
        assertThat(actions.getLast().get("after")).isEqualTo(List.of("switch-12",
                "filter 12's in-flight tasks have finished"));
        // V1.0 keeps ACME-B: never advised to be disabled.
        assertThat(switchOver.keeps(V10, Set.of("acme-a"))).containsExactly("filter 12 (ACME-B)");
    }

    @Test
    void anOldFilterSelectingOnlyTheBuildsFeedsIsDisabledInTheSwitchOver() {
        filter(12, V10, true, all(feed("ACME-A"), type(RAW)));

        final List<List<PromotionSwitchOver.OldSelection>> overlaps = switchOver.overlaps(List.of(entry("ACME-A")),
                uuid -> false);
        final List<Map<String, Object>> made = made(40);
        switchOver.handOver(StroomLinks.CHAT, overlaps, made);

        assertThat(String.valueOf(made.getFirst().get("switch_over"))).contains("Disable filter 12 of pipeline",
                "it selects only ACME-A, which the new filter takes over");
        assertThat(switchOver.keeps(V10, Set.of("ACME-A"))).isEmpty();
    }

    @Test
    void aTextTermOldFilterNamesItsFeedsByNameAndIsReadAllTheSame() {
        filter(12, V10, true, all(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS)
                .value("ACME-A,ACME-B").build(), type(RAW)));
        filter(13, OTHER, true, all(ExpressionTerm.builder().field("Feed").condition(Condition.IN)
                .value("acme-a, ACME-C").build()));

        final List<PromotionSwitchOver.OldSelection> found = switchOver.overlaps(List.of(entry("ACME-A")),
                uuid -> false).getFirst();

        assertThat(found).extracting(selection -> selection.filterId() + ":" + selection.moving() + ":"
                                                  + selection.staying() + ":" + selection.inTerms())
                .containsExactly("12:[ACME-A]:[ACME-B]:[ACME-A]", "13:[ACME-A]:[ACME-C]:[ACME-A]");
    }

    @Test
    void aDictionarySelectedOldFilterIsChangedInItsDictionaryWhoseOtherUsersAreNamed() {
        dictionaries.put(DICTIONARY.getUuid(), DictionaryDoc.builder().uuid(DICTIONARY.getUuid())
                .name(DICTIONARY.getName()).data("ACME-A\nACME-B\n").build());
        filter(12, V10, true, all(inDictionary(), type(RAW)));
        filter(14, OTHER, true, all(inDictionary()));
        Mockito.when(dependencies.fetchDependencies(any(), any())).thenReturn(new ResultPage<>(List.of(
                new Dependency(new DocRef(XsltDoc.TYPE, "x", "ACME-Lookups"), DICTIONARY, true),
                new Dependency(new DocRef(ProcessorFilter.ENTITY_TYPE, "f", "12"), DICTIONARY, true))));

        final List<List<PromotionSwitchOver.OldSelection>> overlaps = switchOver.overlaps(List.of(entry("ACME-A")),
                uuid -> false);

        final PromotionSwitchOver.OldSelection ofV10 = overlaps.getFirst().getFirst();
        assertThat(ofV10.inTerms()).isEmpty();
        assertThat(ofV10.dictionaries()).singleElement().satisfies(use -> {
            assertThat(use.moving()).containsExactly("ACME-A");
            assertThat(use.users()).containsExactly("filter 14 of pipeline 'Other-Events'", "XSLT 'ACME-Lookups'");
        });
        assertThat(ofV10.said()).contains("(ACME-A through dictionary 'ACME feeds')", "ACME-B stays with it");

        final List<Map<String, Object>> made = made(40);
        switchOver.handOver(StroomLinks.CHAT, overlaps, made);

        // The dictionary is said once, though both filters select by it.
        assertThat(made.getFirst().get("switch_over")).asList().filteredOn(step -> String.valueOf(step)
                .startsWith("Remove ACME-A from dictionary")).singleElement().satisfies(step -> assertThat(
                String.valueOf(step)).contains("[ACME feeds](?action=open-doc&docType=Dictionary&docUuid="
                                               + "dict-uuid-1)", "it is also used by filter 14 of pipeline "
                                                                 + "'Other-Events', XSLT 'ACME-Lookups', which stop "
                                                                 + "selecting them too"));
        assertThat(TestCreateProcessorFilterTool.actions(made.getFirst())).extracting(action -> action.get("id"))
                .containsExactly("switch-dict-dict-uui", "enable-40");
    }

    @Test
    void oldFiltersWhoseFeedsCantBeListedSaySoPlainly() {
        // A wildcard, a stream type only, an unreadable dictionary: PipelineFeeds can't list their feeds.
        filter(12, V10, true, all(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS)
                .value("ACME-*").build()));
        filter(13, OTHER, true, all(type(RAW)));
        filter(14, OTHER, true, all(inDictionary()));
        // A wildcard not matching, another stream type: not overlapping.
        filter(15, OTHER, true, all(ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS)
                .value("OTHER-*").build()));
        filter(16, OTHER, true, all(type("Events")));

        final List<PromotionSwitchOver.OldSelection> found = switchOver.overlaps(List.of(entry("ACME-A")),
                uuid -> false).getFirst();

        assertThat(found).extracting(PromotionSwitchOver.OldSelection::filterId).containsExactly(12, 13, 14);
        assertThat(found).extracting(PromotionSwitchOver.OldSelection::unlisted).containsExactly(
                "it selects by the wildcard 'ACME-*'", "it selects by stream type only, any feed's",
                "it selects through dictionary 'ACME feeds', which can't be read");
        assertThat(found.getFirst().said()).contains("so which other feeds it selects can't be listed");
        assertThat(found.getFirst().change(StroomLinks.CHAT, new HashSet<>()))
                .startsWith("Change filter 12 of pipeline [ACME-V1.0-Events]")
                .contains("so it no longer selects ACME-A: it selects by the wildcard 'ACME-*', so its feeds can't "
                          + "be listed");
        assertThat(switchOver.keeps(V10, Set.of("ACME-A"))).containsExactly("filter 12 (it selects by the wildcard "
                                                                            + "'ACME-*')");
    }

    @Test
    void aBrandNewFeedNoOldFilterSelectsKeepsItsFilterAsBefore() {
        // Sample filters (by stream id), filters reading Events (a Pipeline term) and the build's own are passed over.
        filter(12, V10, true, all(feed("ACME-OTHER"), type(RAW)));
        filter(13, V10, true, ExpressionOperator.builder().op(Op.OR).addTerm(ProcessorFilterWrites.term("Id", 5))
                .build());
        filter(14, OTHER, true, all(feed("ACME-A"), ProcessorFilterWrites.pipelineTerm(V10)));
        filter(15, V11, true, all(feed("ACME-A"), type(RAW)));
        filter(16, new DocRef(PipelineDoc.TYPE, "built", "Built"), true, all(feed("ACME-A"), type(RAW)));

        final List<List<PromotionSwitchOver.OldSelection>> overlaps = switchOver.overlaps(List.of(entry("ACME-A"),
                new PromotionProcessing.Entry(OTHER, List.of(), "Events", List.of())), "built"::equals);

        assertThat(overlaps).containsExactly(List.of(), List.of());
        final List<Map<String, Object>> made = made(40);
        final Map<String, Object> before = new LinkedHashMap<>(made.getFirst());
        switchOver.handOver(StroomLinks.CHAT, overlaps, made);
        assertThat(made.getFirst()).isEqualTo(before);
        assertThat(PromotionSwitchOver.said(List.of(entry("ACME-A")), overlaps.subList(0, 1))).isEmpty();
    }

    @Test
    void aFilterWithNoConditionSelectsEveryFeed() {
        filters.add(ProcessorFilter.builder().id(12).pipelineUuid(V10.getUuid()).pipelineName(V10.getName())
                .enabled(true).build());

        assertThat(switchOver.overlaps(List.of(entry("ACME-A")), uuid -> false).getFirst())
                .singleElement().satisfies(selection -> assertThat(selection.unlisted())
                        .isEqualTo("it has no condition, so it selects every feed's streams"));
    }

    @Test
    void theSelectionReportSaysAFeedSelectedTwiceOrByNoEnabledFilterAndTheOldTasksStillOpen() {
        filter(12, V10, true, all(either(feed("ACME-A"), feed("ACME-B")), type(RAW)));
        filter(40, V11, false, all(feed("ACME-A"), type(RAW)));
        Mockito.when(streamProcessing.openTasks(anyInt(), any())).thenReturn(0);
        Mockito.when(streamProcessing.openTasks(12, List.of("ACME-A"))).thenReturn(3);

        assertThat(switchOver.selectionReport(List.of("ACME-A"), RAW, Set.of(40))).containsExactly(
                "ACME-A (Raw Events) is selected twice, by filters of 2 pipelines: filter 12 ('ACME-V1.0-Events'), "
                + "filter 40 ('ACME-V1.1-Events', disabled). Once both are enabled, each of its streams is processed "
                + "by each: until the old selection is changed (the switch-over in the promotion's next steps), leave "
                + "the new filter disabled",
                "filter 12 ('ACME-V1.0-Events') still has 3 task(s) open for ACME-A");

        // ACME-A removed from filter 12, the new filter not yet enabled: selected by none enabled.
        filters.set(0, filter(12, V10, true, all(feed("ACME-B"), type(RAW))));
        filters.removeLast();
        assertThat(switchOver.selectionReport(List.of("ACME-A"), RAW, Set.of(40))).containsExactly(
                "ACME-A (Raw Events) is selected by no enabled filter, so its new streams aren't processed (filter 40 "
                + "('ACME-V1.1-Events', disabled))");

        // Switched over: nothing to say.
        filters.set(1, filter(40, V11, true, all(feed("ACME-A"), type(RAW))));
        filters.removeLast();
        assertThat(switchOver.selectionReport(List.of("ACME-A"), RAW, Set.of(40))).isEmpty();
    }

    @Test
    void mayReadMatchesWildcardsAndStreamTypesAndTakesAnythingElseAsMaybe() {
        final Map<Object, Object> known = new HashMap<>();
        final ExpressionTerm wildcard = ExpressionTerm.builder().field("Feed").condition(Condition.EQUALS)
                .value("AC?E-*").build();
        assertThat(switchOver.mayRead(wildcard, "ACME-A", RAW, known)).isTrue();
        assertThat(switchOver.mayRead(wildcard, "OTHER", RAW, known)).isFalse();
        assertThat(switchOver.mayRead(ExpressionTerm.builder().field("Feed").condition(Condition.NOT_EQUALS)
                .value("ACME-A").build(), "acme-a", RAW, known)).isFalse();
        assertThat(switchOver.mayRead(ExpressionOperator.builder().op(Op.NOT).addTerm(feed("ACME-A")).build(),
                "ACME-A", RAW, known)).isTrue();
        assertThat(switchOver.mayRead(all(feed("ACME-A"), type("Events")), "ACME-A", RAW, known)).isFalse();
        assertThat(switchOver.mayRead(ExpressionTerm.builder().field("Status").condition(Condition.EQUALS)
                .value("Unlocked").build(), "ACME-A", RAW, known)).isTrue();
    }

    // A filter of the pipeline selecting the expression, listed.
    private ProcessorFilter filter(final int id,
                                   final DocRef pipeline,
                                   final boolean enabled,
                                   final ExpressionOperator expression) {
        final ProcessorFilter filter = ProcessorFilter.builder().id(id).pipelineUuid(pipeline.getUuid())
                .pipelineName(pipeline.getName()).enabled(enabled)
                .queryData(QueryData.builder().expression(expression).build())
                .build();
        filters.add(filter);
        return filter;
    }

    // The new filter V1.1's promotion made, disabled, with its enable button.
    private static List<Map<String, Object>> made(final int id) {
        final Map<String, Object> result = new LinkedHashMap<>();
        result.put("filter_id", id);
        result.put("pipeline", V11.getName());
        result.put("feed", "ACME-A");
        result.put("enabled", false);
        result.put("pipeline_link", StroomLinks.CHAT.documentTarget(V11));
        result.put(UserActionEntries.USER_ACTIONS, new ArrayList<>(List.of(UserActionEntries.enableFilter(
                StroomLinks.CHAT, id, V11, "processes feed ACME-A"))));
        return new ArrayList<>(List.of(result));
    }

    private static PromotionProcessing.Entry entry(final String feed) {
        return new PromotionProcessing.Entry(V11, List.of(feed), RAW, List.of());
    }

    private static ExpressionTerm feed(final String name) {
        return ExpressionTerm.builder().field("Feed").condition(Condition.IS_DOC_REF)
                .docRef(new DocRef(FeedDoc.TYPE, name.toLowerCase(), name)).build();
    }

    private static ExpressionTerm type(final String streamType) {
        return ProcessorFilterWrites.term("Type", streamType);
    }

    private static ExpressionTerm inDictionary() {
        return ExpressionTerm.builder().field("Feed").condition(Condition.IN_DICTIONARY).docRef(DICTIONARY).build();
    }

    private static ExpressionOperator all(final ExpressionItem... items) {
        return ProcessorFilterWrites.allOf(List.of(items));
    }

    private static ExpressionOperator either(final ExpressionItem... items) {
        final ExpressionOperator.Builder builder = ExpressionOperator.builder().op(Op.OR);
        for (final ExpressionItem item : items) {
            builder.addItem(item);
        }
        return builder.build();
    }
}
