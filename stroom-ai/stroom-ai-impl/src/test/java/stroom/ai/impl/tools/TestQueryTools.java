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

import stroom.ai.impl.AiAuditLog;
import stroom.ai.impl.AiWorkspaceConfig;
import stroom.ai.impl.UserActions;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.dashboard.shared.ValidateExpressionResult;
import stroom.docref.DocRef;
import stroom.docstore.api.DocDependencyService;
import stroom.docstore.api.DocFinder;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.shared.ExplorerNode;
import stroom.importexport.shared.Dependency;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.data.PipelineDataBuilder;
import stroom.pipeline.shared.data.PipelineProperty;
import stroom.pipeline.shared.data.PipelinePropertyValue;
import stroom.query.api.datasource.ConditionSet;
import stroom.query.api.datasource.FieldType;
import stroom.query.api.datasource.QueryField;
import stroom.query.common.v2.DataSourceProviderRegistry;
import stroom.query.impl.QueryService;
import stroom.search.elastic.shared.ElasticIndexDoc;
import stroom.security.api.SecurityContext;
import stroom.security.shared.DocumentPermission;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

/// The query tools: list_data_sources, describe_data_source, validate_stroomql and propose_query. Each checks Use
/// on the data source as the user, treats one they can't use as one that doesn't exist, and logs what it did.
class TestQueryTools {

    private static final int CHAT = 1;
    private static final DocRef WINDOWS = new DocRef("View", "windows-uuid", "Windows events");
    private static final DocRef SECRET = new DocRef("Index", "secret-uuid", "Secret events");
    private static final DocRef LINUX = new DocRef("Index", "linux-uuid", "Linux events");
    private static final String QUERY = "from \"Windows events\" select User";

    private final DataSourceProviderRegistry registry = Mockito.mock(DataSourceProviderRegistry.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final QueryService queryService = Mockito.mock(QueryService.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final DocumentAccess documentAccess = Mockito.mock(DocumentAccess.class);
    private final AgentInstructions agentInstructions = new AgentInstructions(() -> docFinder, documentAccess);
    private final DocDependencyService dependencyService = Mockito.mock(DocDependencyService.class);
    private final Set<String> usable = new HashSet<>();
    private final List<Map<String, Object>> offered = new ArrayList<>();
    private DataSourceAccess dataSourceAccess;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        dataSourceAccess = new DataSourceAccess(() -> registry, securityContext);
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(registry.getDataSourceDocRefs()).thenReturn(List.of(WINDOWS, SECRET, LINUX));
        Mockito.when(registry.findDataSourceByUuid(Mockito.anyString())).thenAnswer(invocation ->
                List.of(WINDOWS, SECRET, LINUX).stream()
                        .filter(docRef -> docRef.getUuid().equals(invocation.getArgument(0)))
                        .findFirst());
        Mockito.when(registry.fetchDocumentation(Mockito.any())).thenReturn(Optional.empty());
        Mockito.when(registry.getDataSourceProvider(Mockito.anyString())).thenReturn(Optional.empty());
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.USE)))
                .thenAnswer(invocation -> usable.contains(((DocRef) invocation.getArgument(0)).getUuid()));
        Mockito.when(securityContext.useAsReadResult(Mockito.any(Supplier.class)))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(0)).get());
        usable.add(WINDOWS.getUuid());
        usable.add(LINUX.getUuid());
    }

    @Test
    void listDataSourcesGivesOnlyThoseTheUserCanUse() {
        final String result = run(listTool(), "{}");

        assertThat(result)
                .contains("Data sources you can use (2)")
                .contains("Index 'Linux events' (uuid linux-uuid)")
                .contains("View 'Windows events' (uuid windows-uuid)")
                // With more than one, the model is reminded to ask the user which to use.
                .endsWith(ListDataSourcesTool.CHOOSE_NOTE)
                .doesNotContain("Secret");
        final ArgumentCaptor<List<DocRef>> logged = listCaptor();
        Mockito.verify(auditLog).documentsViewed(Mockito.eq(ToolSession.chat(CHAT)), logged.capture(),
                Mockito.contains(ListDataSourcesTool.NAME));
        assertThat(logged.getValue()).containsExactly(LINUX, WINDOWS);
    }

    @Test
    void listDataSourcesCanBeFiltered() {
        assertThat(run(listTool(), "{\"filter\":\"WIN\"}"))
                .contains("Windows events")
                .doesNotContain("Linux")
                // Just one fits, so there is nothing to ask.
                .doesNotContain(ListDataSourcesTool.CHOOSE_NOTE);
        assertThat(run(listTool(), "{\"filter\":\"secret\"}"))
                .isEqualTo("There are no data sources you can use matching 'secret'.");
    }

    @Test
    void describeDataSourceGivesItsFieldsAndLogsAView() {
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.builder().fldName("User").fldType(FieldType.TEXT)
                        .conditionSet(ConditionSet.DEFAULT_TEXT).queryable(true).build(),
                QueryField.builder().fldName("Raw").fldType(FieldType.TEXT).queryable(false).build())));

        final String result = run(describeTool(), "{\"data_source\":\"Windows events\"}");

        assertThat(result)
                .startsWith("View 'Windows events' (uuid windows-uuid)")
                .contains("Fields (2):")
                .contains("- User (")
                .contains("- Raw (")
                .contains("not queryable");
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(WINDOWS),
                Mockito.contains(DescribeDataSourceTool.NAME));
    }

    @Test
    void describeDataSourceSaysWhichSearchesOfAnIndexWouldMislead() {
        // As the Stroom MCP server's find_events refused them, merged into search_events.
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.createText("UserId"))));

        assertThat(run(describeTool(), "{\"data_source\":\"linux-uuid\"}"))
                .contains("Searching it through Stroom (Lucene): STARTS_WITH and ENDS_WITH find nothing")
                .contains("IN takes values separated by commas.");
        // Not an index: nothing said.
        assertThat(run(describeTool(), "{\"data_source\":\"windows-uuid\"}"))
                .doesNotContain("Searching it through Stroom");
        assertThat(DescribeDataSourceTool.searchingNote(MisleadingSearches.ELASTICSEARCH, List.of(
                QueryField.createIpV4Address("source.ip"), QueryField.createText("message"))))
                .contains("Elasticsearch): STARTS_WITH and CONTAINS find nothing")
                .contains("IS_NULL and IS_NOT_NULL match every document")
                .contains("on an ip field (source.ip) a wildcard finds nothing: use a CIDR range");
    }

    @Test
    void dataSourcesInFoldersAnAgentsDocIgnoresAreNotListedUnlessAskedFor() {
        agents("## Ignore\n- folders: Archive\n");
        Mockito.when(documentAccess.path(LINUX)).thenReturn("/Archive/2020");
        Mockito.when(documentAccess.path(WINDOWS)).thenReturn("/Views");

        assertThat(run(listTool(), "{}"))
                .contains("Data sources you can use (1)")
                .contains("Windows events")
                .doesNotContain("Linux events")
                .contains("1 data source(s) in folders an AGENTS doc says to ignore were left out: /Archive "
                          + "(ignored by the AGENTS doc in /). Pass includeIgnored only if the user asks for those.")
                .contains(AgentInstructions.FOLLOW);
        assertThat(run(listTool(),
                "{\"includeIgnored\":true}"))
                .contains("Linux events")
                .doesNotContain("were left out");
    }

    @Test
    void describeDataSourceGivesTheDocumentationOfThePipelinesThatFeedItAndItsAgentsDocs() {
        // As the Stroom MCP server documents a pipeline: a Documentation doc beside it, with its name.
        final DocRef pipeline = new DocRef("Pipeline", "pipe-uuid", "Linux indexing");
        final DocRef pipelineDoc = new DocRef(DocumentationDoc.TYPE, "pipe-doc-uuid", "Linux indexing");
        final DocRef hidden = new DocRef("Pipeline", "hidden-pipe-uuid", "Hidden indexing");
        Mockito.when(dependencyService.fetchDependencies(Mockito.argThat(criteria ->
                        criteria != null && "touuid:=linux-uuid".equals(criteria.getPartialName())), Mockito.any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(new Dependency(pipeline, LINUX, true),
                        new Dependency(hidden, LINUX, true))));
        Mockito.when(documentAccess.canView(pipeline)).thenReturn(true);
        Mockito.when(documentAccess.path(pipeline)).thenReturn("/Pipelines");
        Mockito.when(documentAccess.path(pipelineDoc)).thenReturn("/Pipelines");
        Mockito.when(docFinder.findByName(DocumentationDoc.TYPE, "Linux indexing", false))
                .thenReturn(List.of(pipelineDoc));
        Mockito.when(documentAccess.read(pipelineDoc, DocumentationDoc.class)).thenReturn(Optional.of(
                documentation("pipe-doc-uuid", "| document.description | The tweet's text |")));
        agents("## Fields\n- Use Initiator.Id for the actioning user.\n");
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.builder().fldName("document.description").fldType(FieldType.TEXT).queryable(true)
                        .build())));

        final String result = run(describeTool(), "{\"data_source\":\"linux-uuid\"}");

        assertThat(result)
                .contains("- document.description (")
                .contains("Documentation (what its fields hold")
                .contains("### [Linux indexing](?action=open-doc&docType=Documentation&docUuid=pipe-doc-uuid) "
                          + "(Documentation): the Documentation doc beside a pipeline that writes to it of Pipeline "
                          + "'Linux indexing'")
                .contains("| document.description | The tweet's text |")
                .doesNotContain("Hidden indexing")
                .contains("Use Initiator.Id for the actioning user.");
        // The documentation read is logged.
        Mockito.verify(documentAccess).logViews(Mockito.eq(ToolSession.chat(CHAT)), Mockito.eq(List.of(pipelineDoc)),
                Mockito.contains(DescribeDataSourceTool.NAME));
    }

    @Test
    void anElasticIndexIsDocumentedByThePipelineThatWritesToItByNameAndTheDocsInItsFolder() {
        // As an MCP build leaves it: the indexing pipeline names the Elasticsearch index and its cluster, not the
        // index doc, so the dependency graph has no link from the pipeline to the index doc. The pipeline's
        // documentation is in the index doc's folder, named after the pipeline.
        final DocRef cluster = new DocRef("ElasticCluster", "cluster-uuid", "ES_DEV");
        final DocRef index = new DocRef(ElasticIndexDoc.TYPE, "ecs-uuid", "TWITTER-ECS-v1");
        final DocRef folder = new DocRef("Folder", "indexing-uuid", "Indexing");
        final DocRef pipeline = new DocRef("Pipeline", "pipe-uuid", "Twitter-Indexing-Onboard-v1");
        final DocRef other = new DocRef("Pipeline", "other-pipe-uuid", "Other indexing");
        final DocRef pipelineDoc = new DocRef(DocumentationDoc.TYPE, "pipe-doc-uuid", "Twitter-Indexing-Onboard-v1");
        final DocRef survey = new DocRef(DocumentationDoc.TYPE, "survey-uuid", "Unrelated - Survey");
        Mockito.when(registry.getDataSourceDocRefs()).thenReturn(List.of(index));
        Mockito.when(registry.findDataSourceByUuid("ecs-uuid")).thenReturn(Optional.of(index));
        usable.add(index.getUuid());
        Mockito.when(documentAccess.read(index, ElasticIndexDoc.class)).thenReturn(Optional.of(ElasticIndexDoc.builder()
                .uuid("ecs-uuid").name("TWITTER-ECS-v1").indexName("stroom-ecs-twitter-v1").clusterRef(cluster)
                .build()));
        Mockito.when(dependencyService.fetchDependencies(Mockito.argThat(criteria ->
                        criteria != null && "touuid:=cluster-uuid".equals(criteria.getPartialName())), Mockito.any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(new Dependency(pipeline, cluster, true),
                        new Dependency(other, cluster, true))));
        Mockito.when(documentAccess.canView(Mockito.argThat(docRef -> docRef != null
                                                                    && docRef.getUuid().contains("pipe"))))
                .thenReturn(true);
        Mockito.when(documentAccess.read(pipeline, PipelineDoc.class))
                .thenReturn(Optional.of(writingTo(pipeline, "stroom-ecs-twitter-v1")));
        Mockito.when(documentAccess.read(other, PipelineDoc.class))
                .thenReturn(Optional.of(writingTo(other, "some-other-index")));
        Mockito.when(documentAccess.parent(index)).thenReturn(Optional.of(folder));
        Mockito.when(documentAccess.children(folder)).thenReturn(List.of(
                ExplorerNode.builder().docRef(pipelineDoc).build(), ExplorerNode.builder().docRef(survey).build(),
                ExplorerNode.builder().docRef(index).build()));
        Mockito.when(documentAccess.read(pipelineDoc, DocumentationDoc.class)).thenReturn(Optional.of(documentation(
                "pipe-doc-uuid", "| `create.document.description` | The tweet's text |\n\n<!-- stroom-mcp field "
                                 + "descriptions\n{\"hidden\": \"state\"}\n-->")));
        // A pipeline whose Documentation tab holds nothing but what the MCP server keeps hidden.
        Mockito.when(documentAccess.read(pipeline)).thenReturn(Optional.of(PipelineDoc.builder().uuid("pipe-uuid")
                .name("Twitter-Indexing-Onboard-v1").description("<!-- stroom-mcp agreed index template -->")
                .build()));
        Mockito.when(documentAccess.read(survey, DocumentationDoc.class))
                .thenReturn(Optional.of(documentation("survey-uuid", "Nothing about this index.")));
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                QueryField.builder().fldName("create.document.description").fldType(FieldType.KEYWORD)
                        .queryable(true).build())));

        final String result = run(describeTool(), "{\"data_source\":\"ecs-uuid\"}");

        assertThat(result)
                .contains("### [Twitter-Indexing-Onboard-v1](?action=open-doc&docType=Documentation&docUuid="
                          + "pipe-doc-uuid) (Documentation): the Documentation doc beside it")
                .contains("| `create.document.description` | The tweet's text |")
                .doesNotContain("Unrelated - Survey")
                // What the MCP server keeps hidden is left out.
                .doesNotContain("stroom-mcp")
                .doesNotContain("hidden")
                .doesNotContain("the Documentation tab of a pipeline that writes to it");
        // Given once, though both the folder and the pipeline lead to it.
        assertThat(result.split("The tweet's text", -1)).hasSize(2);
    }

    @Test
    void anElasticIndexNameWithWildCardsCoversTheIndexesAPipelineWritesTo() {
        final DocRef cluster = new DocRef("ElasticCluster", "cluster-uuid", "ES_DEV");
        final DocRef index = new DocRef(ElasticIndexDoc.TYPE, "ecs-uuid", "TWITTER-ECS");
        final DocRef pipeline = new DocRef("Pipeline", "pipe-uuid", "Twitter indexing");
        Mockito.when(registry.getDataSourceDocRefs()).thenReturn(List.of(index));
        Mockito.when(registry.findDataSourceByUuid("ecs-uuid")).thenReturn(Optional.of(index));
        usable.add(index.getUuid());
        Mockito.when(documentAccess.read(index, ElasticIndexDoc.class)).thenReturn(Optional.of(ElasticIndexDoc.builder()
                .uuid("ecs-uuid").name("TWITTER-ECS").indexName("stroom-ecs-twitter-*").clusterRef(cluster).build()));
        Mockito.when(dependencyService.fetchDependencies(Mockito.argThat(criteria ->
                        criteria != null && "touuid:=cluster-uuid".equals(criteria.getPartialName())), Mockito.any()))
                .thenReturn(ResultPage.createUnboundedList(List.of(new Dependency(pipeline, cluster, true))));
        Mockito.when(documentAccess.canView(pipeline)).thenReturn(true);
        Mockito.when(documentAccess.read(pipeline, PipelineDoc.class))
                .thenReturn(Optional.of(writingTo(pipeline, "STROOM-ECS-TWITTER-V2")));
        Mockito.when(documentAccess.read(pipeline)).thenReturn(Optional.of(PipelineDoc.builder().uuid("pipe-uuid")
                .name("Twitter indexing").description("Writes tweets: Text is the tweet's text.").build()));
        Mockito.when(queryService.findFields(Mockito.any())).thenReturn(new ResultPage<>(List.of()));

        assertThat(run(describeTool(), "{\"data_source\":\"ecs-uuid\"}"))
                .contains("(Pipeline): the Documentation tab of a pipeline that writes to it")
                .contains("Writes tweets: Text is the tweet's text.");
    }

    @Test
    void describeDataSourceRefusesOneTheUserCantUseWithoutSayingIfItExists() {
        assertThat(run(describeTool(), "{\"data_source\":\"secret-uuid\"}")).isEqualTo(DataSourceAccess.NOT_FOUND);
        assertThat(run(describeTool(), "{\"data_source\":\"Secret events\"}")).isEqualTo(DataSourceAccess.NOT_FOUND);
        assertThat(run(describeTool(), "{\"data_source\":\"no-such\"}")).isEqualTo(DataSourceAccess.NOT_FOUND);
        Mockito.verifyNoInteractions(queryService, auditLog);
    }

    @Test
    void validateStroomQlChecksTheQueryAndLogsAProcessEvent() {
        Mockito.when(queryService.getReferencedDataSource(QUERY)).thenReturn(Optional.of(WINDOWS));
        Mockito.when(queryService.validateQuery(QUERY)).thenReturn(new ValidateExpressionResult(true, QUERY, false));

        assertThat(run(validateTool(), "{\"query\":\"" + QUERY.replace("\"", "\\\"") + "\"}"))
                .startsWith("OK. The query is valid and reads View 'Windows events'");
        Mockito.verify(auditLog).processed(ToolSession.chat(CHAT), WINDOWS, ValidateStroomQlTool.NAME, QUERY, "OK",
                true);
    }

    @Test
    void validateStroomQlGivesTheParsersError() {
        final String bad = "from \"Windows events\" selectt User";
        Mockito.when(queryService.getReferencedDataSource(bad)).thenReturn(Optional.of(WINDOWS));
        Mockito.when(queryService.validateQuery(bad))
                .thenReturn(new ValidateExpressionResult(false, "Unexpected token 'selectt' at line 1:24", false));

        assertThat(run(validateTool(), "{\"query\":\"" + bad.replace("\"", "\\\"") + "\"}"))
                .contains("not valid: Unexpected token 'selectt' at line 1:24");
    }

    @Test
    void validateStroomQlRefusesADataSourceTheUserCantUse() {
        final String secret = "from \"Secret events\" select User";
        Mockito.when(queryService.getReferencedDataSource(secret)).thenReturn(Optional.of(SECRET));

        assertThat(run(validateTool(), "{\"query\":\"" + secret.replace("\"", "\\\"") + "\"}"))
                .isEqualTo(ValidateStroomQlTool.NO_DATA_SOURCE);
        Mockito.verify(queryService, Mockito.never()).validateQuery(Mockito.anyString());
        Mockito.verify(auditLog).processed(Mockito.eq(ToolSession.chat(CHAT)), Mockito.isNull(),
                Mockito.eq(ValidateStroomQlTool.NAME),
                Mockito.eq(secret), Mockito.anyString(), Mockito.eq(false));
    }

    @Test
    void proposeQueryOffersACreateQueryButton() {
        // The query is laid out a clause a line, as the new Query document will hold it, and checked as it is.
        final String formatted = "from \"Windows events\"\nselect User";
        Mockito.when(queryService.getReferencedDataSource(formatted)).thenReturn(Optional.of(WINDOWS));
        Mockito.when(queryService.validateQuery(formatted))
                .thenReturn(new ValidateExpressionResult(true, formatted, false));
        final ToolRunContext context = new ToolRunContext(null, null, () -> false);

        final String result = context.run(() -> run(proposeTool(), "{\"name\":\"Windows users\",\"query\":\""
                                                                   + QUERY.replace("\"", "\\\"")
                                                                   + "\",\"description\":\"Who logged on\"}"));

        assertThat(result).contains("offered a 'Create query…' button for 'Windows users'")
                .contains("Nothing is created until they click it");
        assertThat(takeUserActions()).singleElement().satisfies(action -> {
            assertThat(action).containsEntry("action", UserActions.CREATE_QUERY)
                    .containsEntry("name", "Windows users")
                    .containsEntry("query", formatted)
                    .containsEntry("description", "Who logged on");
        });
        // Taken once only.
        assertThat(takeUserActions()).isEmpty();
    }

    @Test
    void proposeQueryRefusesAnInvalidQueryOrOneTheUserCantRun() {
        final String bad = "from \"Windows events\" selectt";
        Mockito.when(queryService.getReferencedDataSource(bad)).thenReturn(Optional.of(WINDOWS));
        Mockito.when(queryService.validateQuery(bad)).thenReturn(new ValidateExpressionResult(false, "Bad", false));
        final String secret = "from \"Secret events\" select User";
        Mockito.when(queryService.getReferencedDataSource(StroomQlFormatter.format(secret)))
                .thenReturn(Optional.of(SECRET));
        final ToolRunContext context = new ToolRunContext(null, null, () -> false);

        assertThat(context.run(() -> run(proposeTool(), "{\"name\":\"n\",\"query\":\""
                                                         + bad.replace("\"", "\\\"") + "\"}")))
                .contains("not valid, so no button was offered");
        assertThat(context.run(() -> run(proposeTool(), "{\"name\":\"n\",\"query\":\""
                                                         + secret.replace("\"", "\\\"") + "\"}")))
                .isEqualTo(ValidateStroomQlTool.NO_DATA_SOURCE);
        assertThat(takeUserActions()).isEmpty();
    }

    private ListDataSourcesTool listTool() {
        return new ListDataSourcesTool(workspace, dataSourceAccess, agentInstructions, auditLog);
    }

    private DescribeDataSourceTool describeTool() {
        return new DescribeDataSourceTool(workspace, dataSourceAccess, () -> queryService, agentInstructions,
                new DataSourceDocumentation(documentAccess, dataSourceAccess, () -> docFinder,
                        () -> dependencyService), auditLog);
    }

    private ValidateStroomQlTool validateTool() {
        return new ValidateStroomQlTool(workspace, dataSourceAccess, () -> queryService, auditLog);
    }

    private ProposeQueryTool proposeTool() {
        return new ProposeQueryTool(workspace, dataSourceAccess, () -> queryService);
    }

    // Runs a call, keeping the user actions its result offers for takeUserActions.
    private String run(final StroomTool tool, final String arguments) {
        final ToolResult result = tool.execute(new ToolCall("c1", tool.definition().name(), arguments),
                ToolCalls.inChat(CHAT, workspace));
        result.userActions().forEach(action -> offered.add(action.toMap()));
        return result.text();
    }

    // The user actions offered by the calls since this was last called, as the chat shows them.
    private List<Map<String, Object>> takeUserActions() {
        final List<Map<String, Object>> taken = List.copyOf(offered);
        offered.clear();
        return taken;
    }

    @SuppressWarnings("unchecked")
    private static ArgumentCaptor<List<DocRef>> listCaptor() {
        return ArgumentCaptor.forClass((Class<List<DocRef>>) (Class<?>) List.class);
    }

    // An AGENTS doc at the top of the tree, which the user can view.
    private void agents(final String text) {
        final DocRef agents = new DocRef(DocumentationDoc.TYPE, "agents-uuid", AgentInstructions.DOC_NAME);
        Mockito.when(docFinder.findByName(DocumentationDoc.TYPE, AgentInstructions.DOC_NAME, true))
                .thenReturn(List.of(agents));
        Mockito.when(documentAccess.read(agents, DocumentationDoc.class))
                .thenReturn(Optional.of(documentation("agents-uuid", text)));
        Mockito.when(documentAccess.path(agents)).thenReturn("/");
    }

    private static DocumentationDoc documentation(final String uuid, final String text) {
        return new DocumentationDoc(uuid, "doc", null, null, null, null, null, null, text);
    }

    @Test
    void htmlCommentsAreLeftOutOfDocumentation() {
        assertThat(DataSourceDocumentation.withoutComments("a\n<!-- one -->\n\n\n\nb <!-- two\nlines --> c"))
                .isEqualTo("a\n\nb  c");
        assertThat(DataSourceDocumentation.withoutComments("a <!-- never closed")).isEqualTo("a");
        assertThat(DataSourceDocumentation.withoutComments(null)).isEmpty();
    }

    // A pipeline whose Elastic indexing filter writes to an Elasticsearch index by name.
    private static PipelineDoc writingTo(final DocRef pipeline, final String indexName) {
        return PipelineDoc.builder()
                .uuid(pipeline.getUuid())
                .name(pipeline.getName())
                .pipelineData(new PipelineDataBuilder()
                        .addProperty(new PipelineProperty("elasticIndexingFilter",
                                DataSourceDocumentation.INDEX_NAME_PROPERTY, new PipelinePropertyValue(indexName)))
                        .build())
                .build();
    }
}
