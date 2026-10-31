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

package stroom.ai.impl.tools;

import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.docref.DocRef;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionOperator.Op;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.query.api.datasource.QueryField;
import stroom.query.impl.QueryService;
import stroom.resource.api.ResourceStore;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.Test;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// run_stroomql refuses a query on a Lucene or Elastic index whose answer through Stroom would mislead, as
/// search_events refuses such a search.
class TestRunStroomQlTool {

    private static final DocRef ELASTIC = new DocRef("ElasticIndex", "e", "web");
    private static final DocRef LUCENE = new DocRef("Index", "l", "events");
    private static final DocRef VIEW = new DocRef("View", "v", "Windows events");

    private final QueryService queryService = Mockito.mock(QueryService.class);

    @Test
    void isNullOnElasticsearchIsRefusedSayingWhatToQueryInstead() {
        query(ExpressionOperator.builder().op(Op.AND).addTerm("tags", Condition.IS_NULL, null).build());

        assertThat(RunStroomQlTool.misleading(queryService, "q", ELASTIC)).contains(MisleadingSearches.REFUSAL
                + "tags IS_NULL: Stroom matches every document with IS_NULL on Elasticsearch; check EQUALS '*' (any "
                + "value) with the expected count instead. In StroomQL, EQUALS is = and NOT_EQUALS is !=.");
    }

    @Test
    void aWildcardOnAnIpFieldOfAnElasticIndexIsRefusedWithItsCidrRange() {
        query(ExpressionOperator.builder().addTerm("source.ip", Condition.EQUALS, "192.0.2.*").build());
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createIpV4Address("source.ip"))));

        assertThat(RunStroomQlTool.misleading(queryService, "q", ELASTIC))
                .hasValueSatisfying(refusal -> assertThat(refusal).contains("use EQUALS '192.0.2.0/24'"));
    }

    @Test
    void inOnLuceneWithASpaceInAValueIsRefused() {
        // StroomQL gives IN's values separated by ", ".
        query(ExpressionOperator.builder().addTerm("Door", Condition.IN, "Main entrance, Server room").build());

        assertThat(RunStroomQlTool.misleading(queryService, "q", LUCENE))
                .hasValueSatisfying(refusal -> assertThat(refusal).contains("Stroom finds nothing with IN on a "
                                                                            + "Lucene index when a value holds a "
                                                                            + "space"));
    }

    @Test
    void aQueryThatCanBeTrustedOrIsNotOnAnIndexOrCannotBeCheckedRuns() {
        query(ExpressionOperator.builder().addTerm("tags", Condition.IS_NULL, null).build());
        // On Lucene, IS_NULL works.
        assertThat(RunStroomQlTool.misleading(queryService, "q", LUCENE)).isEmpty();
        // Not an index.
        assertThat(RunStroomQlTool.misleading(queryService, "q", VIEW)).isEmpty();
        assertThat(RunStroomQlTool.misleading(queryService, "q", null)).isEmpty();
        // A query Stroom can't parse is Stroom's to refuse, when it runs.
        Mockito.when(queryService.parseQuery(Mockito.anyString())).thenThrow(new RuntimeException("Bad query"));
        assertThat(RunStroomQlTool.misleading(queryService, "q", ELASTIC)).isEmpty();
    }

    @Test
    void aMisleadingQueryIsRefusedBeforeItRuns() {
        final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(queryService.getReferencedDataSource(Mockito.anyString())).thenReturn(Optional.of(ELASTIC));
        query(ExpressionOperator.builder().addTerm("tags", Condition.IS_NOT_NULL, null).build());
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService,
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
        query(ExpressionOperator.builder().addTerm("SrcIp", Condition.GREATER_THAN, "10.1.0.0").build());
        final LuceneTextFields luceneTextFields = Mockito.mock(LuceneTextFields.class);
        Mockito.when(luceneTextFields.of(LUCENE)).thenReturn(Map.of("SrcIp", "KEYWORD"));
        final RunStroomQlTool tool = new RunStroomQlTool(workspace, () -> queryService,
                () -> Mockito.mock(ResourceStore.class), Mockito.mock(AgentSearches.class));
        tool.setLuceneTextFields(luceneTextFields);

        final String result = tool.execute(ToolCalls.call(RunStroomQlTool.NAME,
                        "{\"query\": \"from events where SrcIp > '10.1.0.0' select SrcIp\"}"),
                ToolCalls.inChat(1, workspace)).text();

        assertThat(result).startsWith("Error: " + MisleadingSearches.REFUSAL + "SrcIp GREATER_THAN '10.1.0.0': SrcIp "
                                      + "is a text field in Lucene");
        Mockito.verify(queryService, Mockito.never()).search(Mockito.any());
    }

    private void query(final ExpressionOperator expression) {
        Mockito.when(queryService.parseQuery(Mockito.anyString())).thenReturn(expression);
    }
}
