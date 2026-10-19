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
            assertThat(action.details()).isEqualTo("Processes data where Feed is 'ACME'. At most 4 tasks at a time.");
            assertThat(action.warning()).isNull();
            assertThat(action.filterId()).isEqualTo(31);
            assertThat(action.doc()).isEqualTo(PIPELINE);
            assertThat(action.status()).isEqualTo(Status.PENDING);
            assertThat(action.expiresMs()).isEqualTo(NOW + UserActions.EXPIRY.toMillis());
        });
    }

    @Test
    void aCreateQueryButtonIsOnlyOfferedByStroomsOwnTools() {
        final DocRef view = new DocRef("View", OTHER_UUID, "Windows events");
        Mockito.when(lookup.usableDataSource("from \"Windows events\" select User")).thenReturn(Optional.of(view));
        final Map<String, Object> createQuery = createQuery("q1", "Windows users",
                "from \"Windows events\" select User");

        // From an MCP server, it is dropped.
        assertThat(userActions.check(List.of(createQuery), NOW)).isEmpty();

        // From one of Stroom's own tools, it is offered, described by Stroom.
        assertThat(userActions.check(null, List.of(createQuery), NOW)).singleElement().satisfies(action -> {
            assertThat(action.action()).isEqualTo(UserActions.CREATE_QUERY);
            assertThat(action.label()).isEqualTo("Create query 'Windows users', reading View 'Windows events'");
            assertThat(action.name()).isEqualTo("Windows users");
            assertThat(action.request()).isEqualTo("from \"Windows events\" select User");
            assertThat(action.details()).isEqualTo("Who logged on");
            assertThat(action.doc()).isEqualTo(view);
            assertThat(action.status()).isEqualTo(Status.PENDING);
        });
    }

    @Test
    void aCreateDashboardButtonIsOnlyOfferedByStroomsOwnTools() {
        final DocRef view = new DocRef("View", OTHER_UUID, "Windows events");
        Mockito.when(lookup.usableDataSource("from \"Windows events\" select User")).thenReturn(Optional.of(view));
        final Map<String, Object> createDashboard = createDashboard("d1", DashboardBuilder.CLASSIC, true);

        // From an MCP server, it is dropped.
        assertThat(userActions.check(List.of(createDashboard), NOW)).isEmpty();

        // From one of Stroom's own tools, it is offered, described by Stroom.
        assertThat(userActions.check(null, List.of(createDashboard), NOW)).singleElement().satisfies(action -> {
            assertThat(action.action()).isEqualTo(UserActions.CREATE_DASHBOARD);
            assertThat(action.label()).isEqualTo("Create dashboard 'Windows users', reading View 'Windows events', "
                                                 + "with a classic (expression) query and a table and a data "
                                                 + "preview");
            assertThat(action.name()).isEqualTo("Windows users");
            assertThat(action.request()).isEqualTo("from \"Windows events\" select User");
            assertThat(action.details()).isEqualTo("Who logged on");
            assertThat(action.doc()).isEqualTo(view);
            assertThat(action.newDashboard()).isEqualTo(new UserActionRecord.NewDashboard(DashboardBuilder.CLASSIC,
                    true));
        });
        assertThat(userActions.check(null, List.of(createDashboard("d2", DashboardBuilder.STROOMQL, false)), NOW))
                .singleElement()
                .satisfies(action -> assertThat(action.label()).endsWith("with a StroomQL query and its table"));
    }

    @Test
    void aCreateDashboardButtonNeedsAKnownQueryTypeAndAUsableDataSource() {
        final DocRef view = new DocRef("View", OTHER_UUID, "Windows events");
        Mockito.when(lookup.usableDataSource("from \"Windows events\" select User")).thenReturn(Optional.of(view));
        final Map<String, Object> noPreview = new HashMap<>(createDashboard("d1", DashboardBuilder.CLASSIC, true));
        noPreview.remove("preview");

        assertThat(userActions.check(null, List.of(createDashboard("d1", "lucene", true)), NOW)).isEmpty();
        assertThat(userActions.check(null, List.of(noPreview), NOW)).isEmpty();
        final Map<String, Object> noQueryType = new HashMap<>(createDashboard("d1", DashboardBuilder.CLASSIC, true));
        noQueryType.remove("query_type");
        assertThat(userActions.check(null, List.of(noQueryType), NOW)).isEmpty();

        Mockito.when(lookup.usableDataSource(Mockito.anyString())).thenReturn(Optional.empty());
        assertThat(userActions.check(null, List.of(createDashboard("d1", DashboardBuilder.CLASSIC, true)), NOW))
                .isEmpty();
    }

    @Test
    void aCodeChangeButtonIsOnlyOfferedByStroomsOwnTools() {
        final DocRef xslt = new DocRef("XSLT", OTHER_UUID, "Event translation");
        Mockito.when(lookup.find(new DocRef("XSLT", OTHER_UUID))).thenReturn(Optional.of(xslt));
        final Map<String, Object> change = codeChange("c1", "xsltFilter", "<b/>");

        // From an MCP server, it is dropped.
        assertThat(userActions.check(List.of(change), NOW)).isEmpty();

        // From one of Stroom's own tools, it is offered, described by Stroom.
        assertThat(userActions.check(null, List.of(change), NOW)).singleElement().satisfies(action -> {
            assertThat(action.action()).isEqualTo(UserActions.APPLY_CODE_CHANGE);
            assertThat(action.label()).isEqualTo("Change to XSLT 'Event translation' (element 'xsltFilter' of "
                                                 + "pipeline 'Acme-Events'): +1 −1 lines");
            assertThat(action.details()).isEqualTo("Lower-cases the user name.");
            assertThat(action.doc()).isEqualTo(xslt);
            assertThat(action.request()).isEqualTo("<b/>");
            assertThat(action.codeChange().pipeline()).isEqualTo(PIPELINE);
            assertThat(action.codeChange().element()).isEqualTo("xsltFilter");
            assertThat(action.codeChange().baseCode()).isEqualTo("<a/>");
            assertThat(action.codeChange().added()).isEqualTo(1);
            assertThat(action.status()).isEqualTo(Status.PENDING);
        });
    }

    @Test
    void aQueryChangeButtonIsOnlyOfferedByStroomsOwnToolsForAQuery() {
        final DocRef query = new DocRef("Query", OTHER_UUID, "Twitter tweets");
        Mockito.when(lookup.find(new DocRef("Query", OTHER_UUID))).thenReturn(Optional.of(query));
        final Map<String, Object> change = new HashMap<>();
        change.put("id", "q1");
        change.put("action", UserActions.APPLY_QUERY_CHANGE);
        change.put("doc", doc("Query", OTHER_UUID, "Something else"));
        change.put("request", "from v\nselect a, b");
        change.put("base", "from v\nselect a");
        change.put("base_hash", "b".repeat(64));
        change.put("added", 1);
        change.put("removed", 1);
        change.put("summary", "Adds b.");

        // From an MCP server, it is dropped.
        assertThat(userActions.check(List.of(change), NOW)).isEmpty();

        assertThat(userActions.check(null, List.of(change), NOW)).singleElement().satisfies(action -> {
            assertThat(action.action()).isEqualTo(UserActions.APPLY_QUERY_CHANGE);
            assertThat(action.label()).isEqualTo("Change to Query 'Twitter tweets': +1 −1 lines");
            assertThat(action.doc()).isEqualTo(query);
            assertThat(action.request()).isEqualTo("from v\nselect a, b");
            assertThat(action.codeChange().pipeline()).isNull();
            assertThat(action.codeChange().element()).isNull();
            assertThat(action.codeChange().baseCode()).isEqualTo("from v\nselect a");
        });

        // Only for a Query.
        final Map<String, Object> notAQuery = new HashMap<>(change);
        notAQuery.put("doc", doc("XSLT", OTHER_UUID, "x"));
        assertThat(userActions.check(null, List.of(notAQuery), NOW)).isEmpty();
    }

    @Test
    void aDashboardChangeButtonIsOnlyOfferedByStroomsOwnTools() {
        final DocRef dashboard = new DocRef("Dashboard", OTHER_UUID, "Logons");
        Mockito.when(lookup.find(new DocRef("Dashboard", OTHER_UUID))).thenReturn(Optional.of(dashboard));
        Mockito.when(lookup.canEdit(dashboard)).thenReturn(false);
        final Map<String, Object> change = dashboardChange("d1");

        // From an MCP server, it is dropped.
        assertThat(userActions.check(List.of(change), NOW)).isEmpty();

        // From one of Stroom's own tools, it is offered, described by Stroom.
        assertThat(userActions.check(null, List.of(change), NOW)).singleElement().satisfies(action -> {
            assertThat(action.action()).isEqualTo(UserActions.APPLY_DASHBOARD_CHANGE);
            assertThat(action.label()).isEqualTo("Change to table 'Events' on dashboard 'Logons': 2 change(s)");
            assertThat(action.doc()).isEqualTo(dashboard);
            assertThat(action.dashboardChange().componentId()).isEqualTo("table-1");
            assertThat(action.dashboardChange().snapshotHash()).isEqualTo("abc123");
            // Whether the user could edit it, so the button can say why it can't be used.
            assertThat(action.dashboardChange().editable()).isFalse();
            assertThat(action.dashboardChange().changes())
                    .containsExactly("Group by 'EventType' (level 1)", "Add column 'Count' with expression count(), "
                                                                       + "at the end");
            assertThat(action.dashboardChange().operations()).hasSize(2);
        });
    }

    @Test
    void aDashboardChangeButtonNeedsADashboardTheUserCanSeeAndGoodChanges() {
        Mockito.when(lookup.find(new DocRef("Dashboard", OTHER_UUID))).thenReturn(Optional.empty());
        assertThat(userActions.check(null, List.of(dashboardChange("d1")), NOW)).isEmpty();

        Mockito.when(lookup.find(new DocRef("Dashboard", OTHER_UUID)))
                .thenReturn(Optional.of(new DocRef("Dashboard", OTHER_UUID, "Logons")));
        final Map<String, Object> badOp = new HashMap<>(dashboardChange("d2"));
        badOp.put("changes", List.of(Map.of("op", "delete_everything", "column", "x")));
        final Map<String, Object> notADashboard = new HashMap<>(dashboardChange("d3"));
        notADashboard.put("doc", doc("Feed", OTHER_UUID, "Logons"));
        final Map<String, Object> noHash = new HashMap<>(dashboardChange("d4"));
        noHash.remove("snapshot_hash");
        assertThat(userActions.check(null, List.of(badOp, notADashboard, noHash), NOW)).isEmpty();
    }

    @Test
    void aCodeChangeButtonNeedsADocumentTheUserCanSeeAndGoodArguments() {
        // The user can't see the element's document.
        Mockito.when(lookup.find(new DocRef("XSLT", OTHER_UUID))).thenReturn(Optional.empty());
        assertThat(userActions.check(null, List.of(codeChange("c1", "xsltFilter", "<b/>")), NOW)).isEmpty();

        Mockito.when(lookup.find(new DocRef("XSLT", OTHER_UUID)))
                .thenReturn(Optional.of(new DocRef("XSLT", OTHER_UUID, "X")));
        final Map<String, Object> noHash = new HashMap<>(codeChange("c2", "xsltFilter", "<b/>"));
        noHash.remove("base_hash");
        final Map<String, Object> notAPipeline = new HashMap<>(codeChange("c3", "xsltFilter", "<b/>"));
        notAPipeline.put("pipeline", doc("Feed", PIPELINE_UUID, "Acme-Events"));
        assertThat(userActions.check(null, List.of(
                codeChange("c4", "two\nlines", "<b/>"),
                codeChange("c5", "xsltFilter", ""),
                noHash,
                notAPipeline), NOW)).isEmpty();
    }

    @Test
    void aCreateQueryButtonNeedsAQueryTheUserCanRunAndAGoodName() {
        Mockito.when(lookup.usableDataSource(Mockito.anyString())).thenReturn(Optional.empty());
        assertThat(userActions.check(null, List.of(createQuery("q1", "Secret", "from Secret select A")), NOW))
                .isEmpty();

        Mockito.when(lookup.usableDataSource(Mockito.anyString()))
                .thenReturn(Optional.of(new DocRef("View", OTHER_UUID, "V")));
        assertThat(userActions.check(null, List.of(
                createQuery("q2", "Two\nlines", "from V select A"),
                createQuery("q3", " ", "from V select A"),
                createQuery("q4", "x".repeat(UserActions.MAX_NAME_CHARS + 1), "from V select A"),
                createQuery("q5", "Fine", "from V select A")), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("q5");
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
    void otherElasticsearchRequestsAreLeftOut(final String request) {
        // Not shown as a step that reads like an error: the model has the request to give the user, in the text.
        assertThat(userActions.check(withPipeline(es("drop", request)), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("enable-31");
    }

    @Test
    void anElasticsearchRequestNeedsAPipelineInTheResult() {
        assertThat(userActions.check(List.of(es("drop", "DELETE ecs-acme-v1")), NOW)).isEmpty();
    }

    @Test
    void anIndexNameWithVariablesNamesTheIndexesItMakes() {
        // The pipeline's XSLT fills the variables in for each document, so the index a request names may be any of
        // those the pattern makes.
        assertThat(UserActions.namesIndex("stroom-ecs-twitter-v1-{_suffixName}", "stroom-ecs-twitter-v1-2026"))
                .isTrue();
        assertThat(UserActions.namesIndex("ecs-{_kind}-v1-{year}", "ecs-auth-v1-2026")).isTrue();
        // Fixed text after a variable must be there too.
        assertThat(UserActions.namesIndex("stroom-twitter-{year}-v1", "stroom-twitter-2026-v1")).isTrue();
        assertThat(UserActions.namesIndex("stroom-twitter-{year}-v1", "stroom-twitter-2026-v2")).isFalse();
        assertThat(UserActions.namesIndex("stroom-twitter-{year}-v1", "stroom-twitter-2026")).isFalse();
        assertThat(UserActions.namesIndex("ecs-acme-v1", "ecs-acme-v1")).isTrue();
        assertThat(UserActions.namesIndex("ecs-acme-v1", "ecs-acme-v1-2026")).isFalse();
        assertThat(UserActions.namesIndex("stroom-ecs-twitter-v1-{_suffixName}", "stroom-ecs-twitter-v1-")).isFalse();
        assertThat(UserActions.namesIndex("stroom-ecs-twitter-v1-{_suffixName}", "other-2026")).isFalse();
        // Starting with a variable, it could make almost any name.
        assertThat(UserActions.namesIndex("{_index}", "anything")).isFalse();
        assertThat(UserActions.namesIndex("{_index}", "{_index}")).isTrue();
        // Characters that mean something to a regular expression are taken as they are.
        assertThat(UserActions.namesIndex("ecs.acme-{x}", "ecsXacme-1")).isFalse();
    }

    @Test
    void anIndexMadeFromAPatternMayBeDropped() {
        Mockito.when(lookup.elasticIndexNames(new DocRef("Pipeline", PIPELINE_UUID)))
                .thenReturn(Set.of("ecs-acme-v1-{_suffixName}"));

        assertThat(userActions.check(withPipeline(es("drop", "DELETE _data_stream/ecs-acme-v1-2026")), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("enable-31", "drop");
        assertThat(userActions.check(withPipeline(es("drop", "DELETE other-2026")), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("enable-31");
    }

    @Test
    void anElasticsearchRequestMayNameItsOwnPipeline() {
        // As propose_index_template gives the agreed template: on its own, for the pipeline that writes the index.
        final Map<String, Object> commit = new HashMap<>(es("commit-template",
                "PUT _index_template/ecs-acme-v1\n{\"index_patterns\": [\"ecs-acme-v1*\"]}"));
        commit.put("doc", doc("Pipeline", PIPELINE_UUID, "Acme-Events"));

        assertThat(userActions.check(List.of(commit), NOW))
                .singleElement()
                .satisfies(action -> assertThat(action.request()).startsWith("PUT _index_template/ecs-acme-v1"));
        // Only a pipeline counts.
        commit.put("doc", doc("Feed", PIPELINE_UUID, "Acme-Events"));
        assertThat(userActions.check(List.of(commit), NOW)).isEmpty();
    }

    @Test
    void aTemplateIsNamedAfterAnIndexUpToItsFirstVariable() {
        Mockito.when(lookup.elasticIndexNames(new DocRef("Pipeline", PIPELINE_UUID)))
                .thenReturn(Set.of("ecs-acme-v1-{year}", "{feed}-events"));

        assertThat(userActions.check(withPipeline(es("t", "PUT _index_template/ecs-acme-v1-template")), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("enable-31", "t");
        // A template for an index with fixed text after a variable is named after the text before it.
        Mockito.when(lookup.elasticIndexNames(new DocRef("Pipeline", PIPELINE_UUID)))
                .thenReturn(Set.of("stroom-twitter-{year}-v1"));
        assertThat(userActions.check(withPipeline(es("t", "PUT _index_template/stroom-twitter-v1")), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("enable-31", "t");
        Mockito.when(lookup.elasticIndexNames(new DocRef("Pipeline", PIPELINE_UUID)))
                .thenReturn(Set.of("ecs-acme-v1-{year}", "{feed}-events"));
        // A name that is all variable says nothing of what the template must be named.
        assertThat(userActions.check(withPipeline(es("t", "PUT _index_template/other-events")), NOW))
                .extracting(UserActionRecord::id)
                .containsExactly("enable-31");
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

    private static Map<String, Object> createQuery(final String id, final String name, final String query) {
        return Map.of("id", id, "action", UserActions.CREATE_QUERY, "name", name, "query", query,
                "description", "Who logged on");
    }

    private static Map<String, Object> createDashboard(final String id,
                                                       final String queryType,
                                                       final boolean preview) {
        return Map.of("id", id, "action", UserActions.CREATE_DASHBOARD, "name", "Windows users",
                "query", "from \"Windows events\" select User", "query_type", queryType, "preview", preview,
                "description", "Who logged on");
    }

    private static Map<String, Object> dashboardChange(final String id) {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("action", UserActions.APPLY_DASHBOARD_CHANGE);
        map.put("doc", doc("Dashboard", OTHER_UUID, "Something else"));
        map.put("component", "Events");
        map.put("component_id", "table-1");
        map.put("snapshot_hash", "abc123");
        map.put("changes", List.of(Map.of("op", "group", "column", "EventType", "level", 1),
                Map.of("op", "add_column", "name", "Count", "expression", "count()")));
        map.put("summary", "Counts events by type.");
        return map;
    }

    private static Map<String, Object> codeChange(final String id, final String element, final String proposed) {
        final Map<String, Object> map = new HashMap<>();
        map.put("id", id);
        map.put("action", UserActions.APPLY_CODE_CHANGE);
        map.put("doc", doc("XSLT", OTHER_UUID, "Something else"));
        map.put("pipeline", doc("Pipeline", PIPELINE_UUID, "Acme-Events"));
        map.put("element", element);
        map.put("request", proposed);
        map.put("base", "<a/>");
        map.put("base_hash", "a".repeat(64));
        map.put("added", 1);
        map.put("removed", 1);
        map.put("summary", "Lower-cases the user name.");
        return map;
    }

    private static Map<String, Object> es(final String id, final String request) {
        return Map.of("id", id, "action", "elasticsearch_request", "request", request);
    }

    // An Elasticsearch request, with a filter action on the pipeline that writes to the index.
    private static List<Object> withPipeline(final Map<String, Object> request) {
        return List.of(enable("enable-31", 31), request);
    }
}
