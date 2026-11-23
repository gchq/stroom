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

package stroom.ai.impl.tools.query;

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.tools.ChatToolCallContexts;
import stroom.ai.impl.tools.ToolCallContext;
import stroom.ai.impl.tools.ToolCalls;
import stroom.ai.impl.tools.ToolSession;
import stroom.ai.impl.tools.time.ChatBrowserZones;
import stroom.ai.impl.tools.time.ToolTimeZone;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.SessionWorkspace;
import stroom.docref.DocRef;
import stroom.query.api.DateTimeSettings;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.UserTimeZone;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.query.shared.QuerySearchRequest;
import stroom.resource.api.ResourceStore;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// run_stroomql refuses a query on a Lucene or Elastic index whose answer through Stroom would mislead, as
/// search_events refuses such a search; and runs in the call's time zone.
class TestRunStroomQlTool {

    private static final DocRef ELASTIC = new DocRef("ElasticIndex", "e", "web");
    private static final DocRef LUCENE = new DocRef("Index", "l", "events");
    private static final DocRef VIEW = new DocRef("View", "v", "Windows events");

    private static final DocRef HR = new DocRef("DataConnection", "h", "HR");
    private static final Function<DocRef, Map<String, String>> NO_LUCENE_TEXT = dataSource -> Map.of();

    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final StroomQlSources stroomQlSources = Mockito.mock(StroomQlSources.class);
    // The conditions the parser gives the first source's search.
    private ExpressionOperator expression;

    @Test
    void isNullOnElasticsearchIsRefusedSayingWhatToQueryInstead() {
        query(ExpressionOperator.builder().op(Op.AND).addTerm("tags", Condition.IS_NULL, null).build());

        assertThat(RunStroomQlTool.misleading(queryService, first(ELASTIC), NO_LUCENE_TEXT))
                .contains(MisleadingSearches.REFUSAL
                + "tags IS_NULL: Stroom matches every document with IS_NULL on Elasticsearch; check EQUALS '*' (any "
                + "document holding the field, an empty string included) with the expected count instead. In "
                + "StroomQL, EQUALS is = and NOT_EQUALS is !=.");
    }

    @Test
    void aWildcardOnAnIpFieldOfAnElasticIndexIsRefusedWithItsCidrRange() {
        query(ExpressionOperator.builder().addTerm("source.ip", Condition.EQUALS, "192.0.2.*").build());
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createIpV4Address("source.ip"))));

        assertThat(RunStroomQlTool.misleading(queryService, first(ELASTIC), NO_LUCENE_TEXT))
                .hasValueSatisfying(refusal -> assertThat(refusal).contains("use EQUALS '192.0.2.0/24'"));
    }

    @Test
    void inOnLuceneWithASpaceInAValueIsRefused() {
        // StroomQL gives IN's values separated by ", ".
        query(ExpressionOperator.builder().addTerm("Door", Condition.IN, "Main entrance, Server room").build());

        assertThat(RunStroomQlTool.misleading(queryService, first(LUCENE), NO_LUCENE_TEXT))
                .hasValueSatisfying(refusal -> assertThat(refusal).contains("Stroom finds nothing with IN on a "
                                                                            + "Lucene index when a value holds a "
                                                                            + "space"));
    }

    @Test
    void aQueryThatCanBeTrustedOrIsNotOnAnIndexOrCannotBeCheckedRuns() {
        query(ExpressionOperator.builder().addTerm("tags", Condition.IS_NULL, null).build());
        // On Lucene, IS_NULL works.
        assertThat(RunStroomQlTool.misleading(queryService, first(LUCENE), NO_LUCENE_TEXT)).isEmpty();
        // Not an index.
        assertThat(RunStroomQlTool.misleading(queryService, first(VIEW), NO_LUCENE_TEXT)).isEmpty();
        assertThat(RunStroomQlTool.misleading(queryService, first(null), NO_LUCENE_TEXT)).isEmpty();
        // A query Stroom can't parse is Stroom's to refuse, when it runs: it has no data sources to check.
        assertThat(RunStroomQlTool.misleading(queryService, List.of(), NO_LUCENE_TEXT)).isEmpty();
    }

    @Test
    void theSearchRunsInTheUsersZoneWithItsConditionsDatePointsInIt() {
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService, () -> stroomQlSources,
                () -> Mockito.mock(ResourceStore.class), Mockito.mock(AgentSearches.class));
        // The chat's browser sent the user's device's zone.
        final ChatBrowserZones browserZones = new ChatBrowserZones();
        browserZones.record(1, "Australia/Canberra");
        final ChatToolCallContexts contexts = ToolCalls.chatContexts(workspace);
        contexts.setBrowserZones(browserZones);

        tool.execute(ToolCalls.call(RunStroomQlTool.NAME,
                "{\"query\": \"from web where EventTime > day() eval D = day() eval H = formatDate(EventTime, 'HH') "
                + "select D, H\"}"), contexts.forChat(1));

        final ArgumentCaptor<QuerySearchRequest> captor = ArgumentCaptor.forClass(QuerySearchRequest.class);
        Mockito.verify(queryService).search(captor.capture());
        final DateTimeSettings settings = captor.getValue().getQueryContext().getDateTimeSettings();
        assertThat(settings.getTimeZone()).isEqualTo(UserTimeZone.fromId("Australia/Canberra"));
        assertThat(settings.getDateTimePattern()).isEqualTo(ToolTimeZone.DATE_TIME_PATTERN);
        assertThat(captor.getValue().getQuery()).contains("where EventTime > \"")
                .contains("[Australia/Canberra]\"").contains("eval D = day()")
                // Stroom's formatDate works in UTC unless given a zone, so it is given the call's.
                .contains("formatDate(EventTime, 'HH', 'Australia/Canberra')");
    }

    @Test
    void theResultSaysTheTimesItsConditionsDatePointsStoodFor() {
        assertThat(RunStroomQlTool.datePointsNote(List.of("day() = 2026-11-21T00:00:00+11:00",
                "week() = 2026-11-16T00:00:00+11:00")))
                .isEqualTo(" In its conditions day() = 2026-11-21T00:00:00+11:00, week() = "
                           + "2026-11-16T00:00:00+11:00.");
        assertThat(RunStroomQlTool.datePointsNote(List.of())).isEmpty();
    }

    @Test
    void aRefusedCallFreesThePathItHeldForItsResults() {
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(ELASTIC));
        query(ExpressionOperator.builder().addTerm("tags", Condition.IS_NOT_NULL, null).build());
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService, () -> stroomQlSources,
                () -> Mockito.mock(ResourceStore.class), Mockito.mock(AgentSearches.class));
        final SessionWorkspace sessionWorkspace = Mockito.spy(new SessionWorkspace(workspace, 1));

        final String result = tool.execute(ToolCalls.call(RunStroomQlTool.NAME,
                        "{\"query\": \"from web where tags is not null select tags\"}"),
                ToolCallContext.builder(ToolSession.chat(1)).workspace(sessionWorkspace).build()).text();

        assertThat(result).startsWith("Error: ");
        Mockito.verify(sessionWorkspace).newFile("/outputs/queries/query", ".md");
        Mockito.verify(sessionWorkspace).releaseHolds();
    }

    @Test
    void aMisleadingQueryIsRefusedBeforeItRuns() {
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(ELASTIC));
        query(ExpressionOperator.builder().addTerm("tags", Condition.IS_NOT_NULL, null).build());
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService, () -> stroomQlSources,
                () -> Mockito.mock(ResourceStore.class), Mockito.mock(AgentSearches.class));

        final String result = tool.execute(ToolCalls.call(RunStroomQlTool.NAME,
                        "{\"query\": \"from web where tags is not null select tags\"}"),
                ToolCalls.inChat(1, workspace)).text();

        assertThat(result).startsWith("Error: " + MisleadingSearches.REFUSAL + "tags IS_NOT_NULL");
        Mockito.verify(queryService, Mockito.never()).search(Mockito.any());
    }

    @Test
    void aRangeOnALuceneTextFieldIsRefusedBeforeItRuns() {
        // Found reviewing the tools: the index's analyzers weren't passed to the check, so this search ran, and
        // found nothing.
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(LUCENE));
        Mockito.when(stroomQlSources.of(Mockito.anyString())).thenReturn(List.of(new StroomQlSources.Source(LUCENE,
                ExpressionOperator.builder().addTerm("SrcIp", Condition.GREATER_THAN, "10.1.0.0").build(), false)));
        final LuceneTextFields luceneTextFields = Mockito.mock(LuceneTextFields.class);
        Mockito.when(luceneTextFields.of(LUCENE)).thenReturn(Map.of("SrcIp", "KEYWORD"));
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService, () -> stroomQlSources,
                () -> Mockito.mock(ResourceStore.class), Mockito.mock(AgentSearches.class));
        tool.setLuceneTextFields(luceneTextFields);

        final String result = tool.execute(ToolCalls.call(RunStroomQlTool.NAME,
                        "{\"query\": \"from events where SrcIp > '10.1.0.0' select SrcIp\"}"),
                ToolCalls.inChat(1, workspace)).text();

        assertThat(result).startsWith("Error: " + MisleadingSearches.REFUSAL + "SrcIp GREATER_THAN '10.1.0.0': SrcIp "
                                      + "is a text field in Lucene");
        Mockito.verify(queryService, Mockito.never()).search(Mockito.any());
    }

    @Test
    void aMisleadingSearchOfAJoinedSourceIsRefusedBeforeItRuns() {
        // Found reviewing the tools for StroomQL joins: only the first source's search was checked.
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(HR));
        Mockito.when(stroomQlSources.of(Mockito.anyString())).thenReturn(List.of(
                new StroomQlSources.Source(HR, null, false),
                new StroomQlSources.Source(ELASTIC, ExpressionOperator.builder()
                        .addTerm("tags", Condition.IS_NULL, null)
                        .build(), true)));
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService, () -> stroomQlSources,
                () -> Mockito.mock(ResourceStore.class), Mockito.mock(AgentSearches.class));

        final String result = tool.execute(ToolCalls.call(RunStroomQlTool.NAME,
                        "{\"query\": \"from HR where Name in (from web where tags is null select user) "
                        + "select Name\"}"),
                ToolCalls.inChat(1, workspace)).text();

        assertThat(result).startsWith("Error: " + MisleadingSearches.REFUSAL + "tags IS_NULL")
                .endsWith("They are in the search of the joined data source ElasticIndex 'web' (uuid e). In "
                          + "StroomQL, EQUALS is = and NOT_EQUALS is !=.");
        Mockito.verify(queryService, Mockito.never()).search(Mockito.any());
    }

    @Test
    void eachSourceIsCheckedWithItsOwnFields() {
        // A joined Lucene source is checked with its own text fields, not the first source's.
        final Map<DocRef, Map<String, String>> luceneText = Map.of(LUCENE, Map.of("SrcIp", "KEYWORD"));
        final List<StroomQlSources.Source> sources = List.of(
                new StroomQlSources.Source(VIEW, null, false),
                new StroomQlSources.Source(LUCENE, ExpressionOperator.builder()
                        .addTerm("SrcIp", Condition.GREATER_THAN, "10.1.0.0")
                        .build(), true));

        assertThat(RunStroomQlTool.misleading(queryService, sources, dataSource ->
                luceneText.getOrDefault(dataSource, Map.of())))
                .hasValueSatisfying(refusal -> assertThat(refusal)
                        .contains("SrcIp is a text field in Lucene")
                        .contains("the joined data source Index 'events'"));
    }

    private void query(final ExpressionOperator expression) {
        this.expression = expression;
        Mockito.when(stroomQlSources.of(Mockito.anyString()))
                .thenReturn(List.of(new StroomQlSources.Source(ELASTIC, expression, false)));
    }

    // The first source only, with the conditions given to query.
    private List<StroomQlSources.Source> first(final DocRef dataSource) {
        return List.of(new StroomQlSources.Source(dataSource, expression, false));
    }
}
