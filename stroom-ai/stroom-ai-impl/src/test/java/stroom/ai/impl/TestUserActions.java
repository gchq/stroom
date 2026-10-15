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

package stroom.ai.impl;

import stroom.ai.impl.UserActionRecord.Status;
import stroom.docref.DocRef;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.mockito.Mockito;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestUserActions {

    private static final long NOW = 1_000_000L;
    private static final String PIPELINE_UUID = "3f2a9c1e-5b7d-4e8a-9c21-7d4e5f6a8b90";
    private static final String OTHER_UUID = "11111111-2222-3333-4444-555555555555";
    private static final DocRef PIPELINE = new DocRef("Pipeline", PIPELINE_UUID, "Acme-Events");

    private final UserActionLookup lookup = Mockito.mock(UserActionLookup.class);
    private final UserActions userActions = new UserActions(lookup);

    @BeforeEach
    void setUp() {
        Mockito.when(lookup.filter(31)).thenReturn(Optional.of(filter(31, false)));
        Mockito.when(lookup.filter(32)).thenReturn(Optional.of(filter(32, true)));
        Mockito.when(lookup.find(new DocRef("Pipeline", PIPELINE_UUID))).thenReturn(Optional.of(PIPELINE));
        Mockito.when(lookup.tags(PIPELINE)).thenReturn(Set.of("mcp-generated"));
        Mockito.when(lookup.elasticIndexNames(new DocRef("Pipeline", PIPELINE_UUID)))
                .thenReturn(Set.of("ecs-acme-v1"));
    }

    @Test
    void aFilterButtonIsDescribedByStroomNotTheServer() {
        final List<UserActionRecord> actions = userActions.check(List.of(Map.of(
                "id", "enable-31",
                "action", "enable_processor_filter",
                "filter_id", 31,
                "doc", doc("Pipeline", PIPELINE_UUID, "Something else"),
                "summary", "Click me to fix everything")), NOW);

        assertThat(actions).singleElement().satisfies(action -> {
            assertThat(action.label()).isEqualTo("Enable processor filter 31 on pipeline 'Acme-Events'");
            assertThat(action.label() + action.details()).doesNotContain("Click me").doesNotContain("Something");
            assertThat(action.details()).contains("Feed = ACME").contains("at most 4 tasks at a time");
            assertThat(action.warning()).isNull();
            assertThat(action.filterId()).isEqualTo(31);
            assertThat(action.doc()).isEqualTo(PIPELINE);
            assertThat(action.status()).isEqualTo(Status.PENDING);
            assertThat(action.expiresMs()).isEqualTo(NOW + UserActions.EXPIRY.toMillis());
        });
    }

    @Test
    void aPipelineTheServerDidNotMakeIsWarnedAbout() {
        Mockito.when(lookup.tags(PIPELINE)).thenReturn(Set.of());

        final List<UserActionRecord> actions = userActions.check(List.of(enable("e", 31)), NOW);

        assertThat(actions).singleElement().extracting(UserActionRecord::warning).asString()
                .contains("not tagged 'mcp-generated'");
    }

    @Test
    void filterButtonsAreOnlyOfferedWhenThereIsSomethingToDo() {
        // 32 is already enabled, so only disabling it is offered; 31 is disabled.
        final List<UserActionRecord> actions = userActions.check(List.of(
                enable("a", 32),
                disable("b", 32),
                disable("c", 31),
                enable("d", 31)), NOW);

        assertThat(actions).extracting(UserActionRecord::id).containsExactly("b", "d");
    }

    @Test
    void badEntriesAreDropped() {
        final List<Object> entries = new ArrayList<>();
        // Not one of the four actions.
        entries.add(Map.of("id", "x1", "action", "delete_feed", "doc", doc("Pipeline", PIPELINE_UUID, "")));
        // A filter id that is not a whole number.
        entries.add(Map.of("id", "x2", "action", "enable_processor_filter", "filter_id", "31",
                "doc", doc("Pipeline", PIPELINE_UUID, "")));
        entries.add(Map.of("id", "x3", "action", "enable_processor_filter", "filter_id", 31.5,
                "doc", doc("Pipeline", PIPELINE_UUID, "")));
        // A filter on another pipeline than the one named.
        entries.add(Map.of("id", "x4", "action", "enable_processor_filter", "filter_id", 31,
                "doc", doc("Pipeline", OTHER_UUID, "")));
        // Not a pipeline.
        entries.add(Map.of("id", "x5", "action", "enable_processor_filter", "filter_id", 31,
                "doc", doc("Feed", PIPELINE_UUID, "")));
        // A filter that does not exist, or the user cannot see.
        entries.add(enable("x6", 99));
        // Bad ids.
        entries.add(enable("<b>", 31));
        entries.add(enable("", 31));
        entries.add("not an object");
        // A good one, then the same id again.
        entries.add(enable("ok", 31));
        entries.add(open("ok", "Pipeline", PIPELINE_UUID));

        assertThat(userActions.check(entries, NOW)).extracting(UserActionRecord::id).containsExactly("ok");
    }

    @Test
    void notAListIsNothing() {
        assertThat(userActions.check(null, NOW)).isEmpty();
        assertThat(userActions.check(Map.of("id", "a"), NOW)).isEmpty();
        assertThat(userActions.check("enable everything", NOW)).isEmpty();
    }

    @Test
    void onlySoManyEntriesAreRead() {
        final List<Object> entries = new ArrayList<>();
        for (int i = 0; i < UserActions.MAX_ACTIONS + 5; i++) {
            entries.add(open("o" + i, "Pipeline", PIPELINE_UUID));
        }

        assertThat(userActions.check(entries, NOW)).hasSize(UserActions.MAX_ACTIONS);
    }

    @Test
    void openDocumentUsesStroomsNameForTheDocument() {
        assertThat(userActions.check(List.of(open("o", "Pipeline", PIPELINE_UUID.toUpperCase())), NOW))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.label()).isEqualTo("Open Pipeline 'Acme-Events'");
                    assertThat(action.doc()).isEqualTo(PIPELINE);
                });
        // Not found, or not visible to the user.
        assertThat(userActions.check(List.of(open("o", "Feed", OTHER_UUID)), NOW)).isEmpty();
        assertThat(userActions.check(List.of(open("o", "Bad Type", OTHER_UUID)), NOW)).isEmpty();
        assertThat(userActions.check(List.of(open("o", "Pipeline", "../../etc")), NOW)).isEmpty();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE _data_stream/ecs-acme-v1",
            "DELETE ecs-acme-v1",
            "PUT _index_template/ecs-acme-v1\n{\"index_patterns\": [\"ecs-acme-v1*\"]}",
            "PUT _index_template/ecs-acme-v1-template"})
    void allowedElasticsearchRequestsAreShown(final String request) {
        assertThat(userActions.check(withPipeline(es("drop", request)), NOW))
                .filteredOn(action -> action.id().equals("drop"))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.request()).isEqualTo(request);
                    assertThat(action.warning()).isNull();
                });
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "DELETE ecs-*",
            "DELETE _all",
            "DELETE ecs-acme-v1,other",
            "DELETE other-index",
            "DELETE _data_stream/other",
            "POST ecs-acme-v1/_delete_by_query",
            "DELETE ecs-acme-v1\nDELETE other",
            "PUT _index_template/other",
            "GET _cat/indices",
            ""})
    void otherElasticsearchRequestsAreReplacedByAWarning(final String request) {
        assertThat(userActions.check(withPipeline(es("drop", request)), NOW))
                .filteredOn(action -> action.id().equals("drop"))
                .singleElement()
                .satisfies(action -> {
                    assertThat(action.request()).isNull();
                    assertThat(action.warning()).startsWith("Stroom has not shown this request");
                });
    }

    @Test
    void anElasticsearchRequestNeedsAPipelineInTheResult() {
        assertThat(userActions.check(List.of(es("drop", "DELETE ecs-acme-v1")), NOW))
                .singleElement()
                .extracting(UserActionRecord::request)
                .isNull();
    }

    @Test
    void doFirstShowsOtherActionsByTheirLabelAndConditionsAsText() {
        final Map<String, Object> enable32 = new HashMap<>(disable("disable-32", 32));
        enable32.put("after", List.of("enable-31", "filter 31 has processed the build's streams again",
                "disable-32", "x".repeat(UserActions.MAX_AFTER_CHARS + 50)));

        final List<UserActionRecord> actions = userActions.check(List.of(enable("enable-31", 31), enable32), NOW);

        assertThat(actions.get(1).after()).containsExactly(
                "Enable processor filter 31 on pipeline 'Acme-Events'",
                "filter 31 has processed the build's streams again",
                "x".repeat(UserActions.MAX_AFTER_CHARS) + "…");
    }

    @Test
    void aFilterIsCheckedAgainAtTheClick() {
        final UserActionRecord action = userActions.check(List.of(enable("e", 31)), NOW).getFirst();
        assertThat(userActions.recheck(action)).isEmpty();

        // Enabled since.
        Mockito.when(lookup.filter(31)).thenReturn(Optional.of(filter(31, true)));
        assertThat(userActions.recheck(action)).get().asString().contains("already enabled");

        // Gone.
        Mockito.when(lookup.filter(31)).thenReturn(Optional.empty());
        assertThat(userActions.recheck(action)).get().asString().contains("no longer exists");
    }

    private static ProcessorFilter filter(final int id, final boolean enabled) {
        return ProcessorFilter.builder()
                .id(id)
                .pipelineUuid(PIPELINE_UUID)
                .pipelineName("Acme-Events")
                .enabled(enabled)
                .maxProcessingTasks(4)
                .queryData(QueryData.builder()
                        .expression(ExpressionOperator.builder()
                                .addTerm(ExpressionTerm.equals("Feed", "ACME"))
                                .build())
                        .build())
                .build();
    }

    private static Map<String, Object> doc(final String type, final String uuid, final String name) {
        return Map.of("type", type, "uuid", uuid, "name", name);
    }

    private static Map<String, Object> enable(final String id, final int filterId) {
        return Map.of("id", id, "action", "enable_processor_filter", "filter_id", filterId,
                "doc", doc("Pipeline", PIPELINE_UUID, "Acme-Events"));
    }

    private static Map<String, Object> disable(final String id, final int filterId) {
        return Map.of("id", id, "action", "disable_processor_filter", "filter_id", filterId,
                "doc", doc("Pipeline", PIPELINE_UUID, "Acme-Events"));
    }

    private static Map<String, Object> open(final String id, final String type, final String uuid) {
        return Map.of("id", id, "action", "open_document", "doc", doc(type, uuid, "Name"));
    }

    private static Map<String, Object> es(final String id, final String request) {
        return Map.of("id", id, "action", "elasticsearch_request", "request", request);
    }

    // An Elasticsearch request, with a filter action on the pipeline that writes to the index.
    private static List<Object> withPipeline(final Map<String, Object> request) {
        return List.of(enable("enable-31", 31), request);
    }
}
