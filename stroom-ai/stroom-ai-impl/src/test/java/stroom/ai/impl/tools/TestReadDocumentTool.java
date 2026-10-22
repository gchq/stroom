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
import stroom.ai.impl.TabSnapshots;
import stroom.ai.impl.workspace.ChatWorkspace;
import stroom.ai.impl.workspace.WorkspacePath;
import stroom.ai.shared.AiTabSnapshotPart;
import stroom.dictionary.api.WordListProvider;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.dictionary.shared.WordList;
import stroom.docref.DocRef;
import stroom.docstore.api.ContentIndexable;
import stroom.docstore.api.DocumentActionHandler;
import stroom.docstore.api.DocumentTypeName;
import stroom.documentation.shared.DocumentationDoc;
import stroom.explorer.api.ExplorerNodeService;
import stroom.explorer.shared.ExplorerNode;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.api.SecurityContext;
import stroom.security.shared.AppPermission;
import stroom.security.shared.DocumentPermission;
import stroom.util.json.JsonUtil;
import stroom.util.shared.ResultPage;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.time.Duration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Supplier;

import static org.assertj.core.api.Assertions.assertThat;

class TestReadDocumentTool {

    private static final DocRef XSLT = new DocRef("XSLT", "xslt-uuid", "Event translation");
    private static final DocRef TOP = new DocRef(DictionaryDoc.TYPE, "top-uuid", "Malicious IPs");
    private static final DocRef MIDDLE = new DocRef(DictionaryDoc.TYPE, "middle-uuid", "Known bad");
    private static final DocRef BOTTOM = new DocRef(DictionaryDoc.TYPE, "bottom-uuid", "Botnets");
    private static final DocRef SECRET = new DocRef(DictionaryDoc.TYPE, "secret-uuid", "Secret IPs");
    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Firewall pipeline");
    private static final DocRef FEED = new DocRef("Feed", "feed-uuid", "FIREWALL-EVENTS");

    private final ExplorerNodeService explorerNodeService = Mockito.mock(ExplorerNodeService.class);
    private final SecurityContext securityContext = Mockito.mock(SecurityContext.class);
    private final AiAuditLog auditLog = Mockito.mock(AiAuditLog.class);
    private final ChatWorkspace workspace = Mockito.mock(ChatWorkspace.class);
    private final WordListProvider wordListProvider = Mockito.mock(WordListProvider.class);
    private final TabSnapshots tabSnapshots = Mockito.mock(TabSnapshots.class);
    private final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
    @SuppressWarnings("rawtypes")
    private final Map<DocumentTypeName, DocumentActionHandler> handlers = new HashMap<>();
    private final Map<String, DictionaryDoc> dictionaries = new HashMap<>();
    private final Set<String> viewable = new HashSet<>();
    private ReadDocumentTool tool;

    @BeforeEach
    @SuppressWarnings("unchecked")
    void setUp() {
        final DocumentAccess documentAccess = new DocumentAccess(() -> explorerNodeService, securityContext,
                () -> handlers, auditLog);
        final ProcessorFilterAccess processorFilterAccess = new ProcessorFilterAccess(securityContext,
                () -> processorFilterService, documentAccess);
        tool = new ReadDocumentTool(workspace, documentAccess, processorFilterAccess, () -> wordListProvider,
                tabSnapshots);
        Mockito.when(tabSnapshots.unsavedPart(Mockito.anyInt(), Mockito.any(), Mockito.any()))
                .thenReturn(Optional.empty());
        Mockito.when(tabSnapshots.forLatestQuestion(Mockito.anyInt())).thenReturn(Optional.empty());
        Mockito.when(workspace.limits()).thenReturn(new AiWorkspaceConfig());
        Mockito.when(workspace.withTimeLimit(Mockito.any(Duration.class), Mockito.any()))
                .thenAnswer(invocation -> ((Supplier<?>) invocation.getArgument(1)).get());
        Mockito.when(securityContext.hasDocumentPermission(Mockito.any(), Mockito.eq(DocumentPermission.VIEW)))
                .thenAnswer(invocation -> viewable.contains(((DocRef) invocation.getArgument(0)).getUuid()));
        Mockito.when(explorerNodeService.getNodeByUuid(Mockito.anyString())).thenAnswer(invocation -> {
            final String uuid = invocation.getArgument(0);
            return List.of(XSLT, TOP, MIDDLE, BOTTOM, SECRET).stream()
                    .filter(docRef -> docRef.getUuid().equals(uuid))
                    .findFirst()
                    .map(docRef -> ExplorerNode.builder().docRef(docRef).build());
        });
        Mockito.when(explorerNodeService.getPath(Mockito.any())).thenReturn(List.of());

        final DocumentActionHandler<?> dictionaryHandler = Mockito.mock(DocumentActionHandler.class);
        Mockito.when(dictionaryHandler.readDocument(Mockito.any())).thenAnswer(invocation ->
                dictionaries.get(((DocRef) invocation.getArgument(0)).getUuid()));
        handlers.put(new DocumentTypeName(DictionaryDoc.TYPE), dictionaryHandler);
    }

    @Test
    void readsTheCodeOfAnXslt() {
        viewable.add(XSLT.getUuid());
        xsltContent("<xsl:stylesheet/>");

        final String result = run("{\"uuid\":\"xslt-uuid\"}");

        assertThat(result)
                .contains("XSLT 'Event translation', /.")
                .contains("## Part 'xsl'\n<xsl:stylesheet/>");
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(1)), Mockito.eq(XSLT), Mockito.anyString());
        Mockito.verify(workspace, Mockito.never()).writeDocument(Mockito.anyInt(), Mockito.any(), Mockito.any(),
                Mockito.any());
    }

    @Test
    void aDocumentTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        xsltContent("<xsl:stylesheet/>");

        assertThat(run("{\"uuid\":\"xslt-uuid\"}")).startsWith(DocumentAccess.NO_PERMISSION);
        assertThat(run("{\"uuid\":\"no-such-uuid\"}")).startsWith(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aProcessorFilterIsReadWithWhatItSelectsAndIsLogged() {
        canManageProcessors();
        viewable.add(PIPELINE.getUuid());
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, FEED)
                .build();
        Mockito.when(processorFilterService.fetchByUuid("filter-uuid")).thenReturn(Optional.of(filter()
                .enabled(true)
                .priority(10)
                .maxProcessingTasks(5)
                .queryData(QueryData.builder().expression(expression).build())
                .minMetaCreateTimeMs(0L)
                .processorFilterTracker(ProcessorFilterTracker.builder()
                        .status(ProcessorFilterTrackerStatus.COMPLETE)
                        .metaCount(3L)
                        .build())
                .build()));

        final String result = run("{\"uuid\":\"filter-uuid\"}");

        assertThat(result)
                .isEqualTo("Processor filter 7 (uuid filter-uuid) of Pipeline [Firewall pipeline]"
                           + "(?action=open-doc&docType=Pipeline&docUuid=pipe-uuid) (uuid pipe-uuid, in /).\n"
                           + "Selects: streams where Feed is 'FIREWALL-EVENTS', created on or after "
                           + "1970-01-01T00:00:00.000Z.\n"
                           + "State: enabled, priority 10, Complete, 3 stream(s).\n"
                           + "At most 5 task(s) at a time.");
        final ArgumentCaptor<DocRef> logged = ArgumentCaptor.forClass(DocRef.class);
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(1)), logged.capture(), Mockito.anyString());
        assertThat(logged.getValue().getType()).isEqualTo(ProcessorFilter.ENTITY_TYPE);
        assertThat(logged.getValue().getUuid()).isEqualTo("filter-uuid");
    }

    @Test
    void aProcessorFilterThatSelectsEverythingSaysSo() {
        canManageProcessors();
        viewable.add(PIPELINE.getUuid());
        Mockito.when(processorFilterService.fetchByUuid("filter-uuid")).thenReturn(Optional.of(filter().build()));

        assertThat(run("{\"uuid\":\"filter-uuid\"}"))
                .contains("Selects: all streams.\n")
                .contains("State: disabled, priority");
    }

    @Test
    void aProcessorFilterNeedsManageProcessors() {
        viewable.add(PIPELINE.getUuid());
        Mockito.when(processorFilterService.fetchByUuid("filter-uuid")).thenReturn(Optional.of(filter().build()));

        assertThat(run("{\"uuid\":\"filter-uuid\"}"))
                .isEqualTo(DocumentAccess.NO_PERMISSION + " If it is a processor filter, reading one needs the "
                           + "Manage Processors permission, as the Processors tab does.");
        Mockito.verifyNoInteractions(processorFilterService, auditLog);
    }

    @Test
    void aProcessorFilterOfAPipelineTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        canManageProcessors();
        Mockito.when(processorFilterService.fetchByUuid("filter-uuid")).thenReturn(Optional.of(filter().build()));
        Mockito.when(processorFilterService.fetchByUuid("failing-uuid")).thenThrow(new IllegalStateException("no"));

        assertThat(run("{\"uuid\":\"filter-uuid\"}")).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(run("{\"uuid\":\"failing-uuid\"}")).isEqualTo(DocumentAccess.NO_PERMISSION);
        assertThat(run("{\"uuid\":\"no-such-uuid\"}")).isEqualTo(DocumentAccess.NO_PERMISSION);
        Mockito.verifyNoInteractions(auditLog);
    }

    @Test
    void aMissingPartSaysWhatThereIs() {
        viewable.add(XSLT.getUuid());
        xsltContent("<xsl:stylesheet/>");

        assertThat(run("{\"uuid\":\"xslt-uuid\",\"part\":\"json\"}"))
                .contains("has no part 'json'. Its parts are: xsl.");
    }

    @Test
    void aLargeDocumentIsSavedToTheWorkspaceWithAPreview() {
        viewable.add(XSLT.getUuid());
        final StringBuilder code = new StringBuilder();
        for (int i = 0; i < 2_000; i++) {
            code.append("<xsl:template match=\"e").append(i).append("\"/>\n");
        }
        xsltContent(code.toString());

        final String result = run("{\"uuid\":\"xslt-uuid\"}");

        final ArgumentCaptor<WorkspacePath> path = ArgumentCaptor.forClass(WorkspacePath.class);
        Mockito.verify(workspace).writeDocument(Mockito.eq(1), path.capture(), Mockito.eq(code.toString()),
                Mockito.eq(XSLT.getUuid()));
        assertThat(path.getValue().path()).isEqualTo("/documents/xslt-uuid/xsl.xsl");
        assertThat(result)
                .contains("too large to show in full")
                .contains("/documents/xslt-uuid/xsl.xsl")
                .contains("<xsl:template match=\"e0\"/>")
                .doesNotContain("e1999");
        assertThat(result.length()).isLessThan(new AiWorkspaceConfig().getMaxCharsPerRead());
    }

    @Test
    void aDictionarysOwnWordsOnly() {
        viewable.add(TOP.getUuid());
        dictionary(TOP, "1.2.3.4\n\n5.6.7.8\n", MIDDLE);

        final String result = run("{\"uuid\":\"top-uuid\",\"part\":\"own\"}");

        assertThat(result)
                .contains("2 word(s) of its own")
                .contains("Words:\n1.2.3.4\n5.6.7.8");
        Mockito.verifyNoInteractions(wordListProvider);
    }

    @Test
    void aDictionaryWithImportsNestedTwoDeepIsCombinedWithSources() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid(), BOTTOM.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "2.2.2.2", BOTTOM);
        dictionary(BOTTOM, "3.3.3.3\n1.1.1.1");
        // As Stroom combines them: imports first, duplicates removed.
        combined(TOP, List.of("3.3.3.3", "1.1.1.1", "2.2.2.2"), List.of(BOTTOM, BOTTOM, MIDDLE));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("It has 3 word(s) in all")
                .contains("1 are its own")
                .contains("- Dictionary 'Known bad' (uuid middle-uuid): 1 word(s) of its own\n"
                          + "  - Dictionary 'Botnets' (uuid bottom-uuid): 2 word(s) of its own")
                .contains("3.3.3.3  (from Dictionary 'Botnets')")
                .contains("1.1.1.1  (from Dictionary 'Botnets')")
                .contains("2.2.2.2  (from Dictionary 'Known bad')")
                .doesNotContain("incomplete");
        // The imports whose words are given are logged as viewed, as well as the dictionary asked for.
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(1)), Mockito.eq(TOP), Mockito.anyString());
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(1)), Mockito.eq(MIDDLE),
                Mockito.contains("as an import"));
        Mockito.verify(auditLog).documentViewed(Mockito.eq(ToolSession.chat(1)), Mockito.eq(BOTTOM),
                Mockito.contains("as an import"));
    }

    @Test
    void unsavedOwnWordsAreCombinedWithTheSavedImports() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "2.2.2.2\n4.4.4.4");
        combined(TOP, List.of("2.2.2.2", "4.4.4.4", "1.1.1.1"), List.of(MIDDLE, MIDDLE, TOP));
        // The dictionary is the tab the user attached: 1.1.1.1 has been removed, 3.3.3.3 and 4.4.4.4 added, unsaved.
        Mockito.when(tabSnapshots.unsavedPart(1, TOP, AiTabSnapshotPart.DATA)).thenReturn(Optional.of(
                new TabSnapshots.UnsavedPart("3.3.3.3\n4.4.4.4\n", false, "/tabs/3/words.txt")));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("its own words are as edited there (/tabs/3/words.txt), not as saved")
                .contains("The words of the dictionaries it imports are as saved.")
                .contains("It has 3 word(s) in all")
                .contains("2 are its own (unsaved)")
                .contains("2.2.2.2  (from Dictionary 'Known bad')")
                .contains("3.3.3.3\n")
                // A word in both is the dictionary's own, as the dictionary nearest the top wins.
                .endsWith("3.3.3.3\n4.4.4.4")
                .doesNotContain("4.4.4.4  (from")
                .doesNotContain("1.1.1.1");
    }

    @Test
    void unsavedOwnWordsTooManyToShareAreSaidSo() {
        viewable.add(TOP.getUuid());
        dictionary(TOP, "1.1.1.1");
        combined(TOP, List.of("1.1.1.1"), List.of(TOP));
        Mockito.when(tabSnapshots.unsavedPart(1, TOP, AiTabSnapshotPart.DATA)).thenReturn(Optional.of(
                new TabSnapshots.UnsavedPart("9.9.9.9", true, "/tabs/3/words.txt")));

        assertThat(run("{\"uuid\":\"top-uuid\"}"))
                .contains("too many to share, so its words as saved are used")
                .contains("1.1.1.1");
    }

    @Test
    void aDocumentWithUnsavedChangesSaysWhatWasReadIsAsSaved() {
        viewable.add(XSLT.getUuid());
        xsltContent("<xsl:stylesheet/>");
        Mockito.when(tabSnapshots.forLatestQuestion(1)).thenReturn(Optional.of(new TabSnapshots.StoredTabSnapshot(
                5, 3, XSLT, "/", "Event translation", null, true, null, "h", 0L, List.of(), List.of(), null)));

        assertThat(run("{\"uuid\":\"xslt-uuid\"}"))
                .contains("This is the document as saved")
                .contains("/tabs/3/");
    }

    @Test
    void aCircularImportIsOnlyFollowedOnce() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "2.2.2.2", TOP);
        combined(TOP, List.of("2.2.2.2", "1.1.1.1"), List.of(MIDDLE, TOP));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("- Dictionary 'Known bad' (uuid middle-uuid): 1 word(s) of its own\n"
                          + "  - Dictionary 'Malicious IPs': already included above")
                .contains("It has 2 word(s) in all");
    }

    @Test
    void duplicatesAcrossImportsKeepTheSourceStroomGivesThem() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid(), BOTTOM.getUuid()));
        dictionary(TOP, "9.9.9.9", MIDDLE, BOTTOM);
        dictionary(MIDDLE, "9.9.9.9");
        dictionary(BOTTOM, "9.9.9.9");
        combined(TOP, List.of("9.9.9.9"), List.of(MIDDLE));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("It has 1 word(s) in all")
                .contains("9.9.9.9  (from Dictionary 'Known bad')");
    }

    @Test
    void anImportTheUserCantViewIsReportedAsNotIncluded() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE, SECRET);
        dictionary(MIDDLE, "2.2.2.2");
        dictionary(SECRET, "6.6.6.6");
        // Stroom leaves it out without saying so.
        combined(TOP, List.of("2.2.2.2", "1.1.1.1"), List.of(MIDDLE, TOP));

        final String result = run("{\"uuid\":\"top-uuid\"}");

        assertThat(result)
                .contains("- Dictionary 'Secret IPs': not included: no permission")
                .contains("The list is incomplete: 1 import(s) couldn't be included")
                .doesNotContain("6.6.6.6");
        // Nothing is logged as viewed for an import that wasn't read.
        Mockito.verify(auditLog, Mockito.never()).documentViewed(Mockito.any(), Mockito.eq(SECRET),
                Mockito.anyString());
    }

    @Test
    void aLargeListIsWrittenAsATableWithItsSource() {
        viewable.addAll(Set.of(TOP.getUuid(), MIDDLE.getUuid()));
        dictionary(TOP, "1.1.1.1", MIDDLE);
        dictionary(MIDDLE, "many");
        final WordList.Builder builder = WordList.builder(true);
        for (int i = 0; i < 3_000; i++) {
            builder.addWord("10.0." + (i / 256) + "." + (i % 256), MIDDLE);
        }
        builder.addWord("1.1.1.1", TOP);
        Mockito.when(wordListProvider.getCombinedWordList(TOP)).thenReturn(builder.build());

        final String result = run("{\"uuid\":\"top-uuid\"}");

        final ArgumentCaptor<String> table = ArgumentCaptor.forClass(String.class);
        Mockito.verify(workspace).writeDocument(Mockito.eq(1),
                Mockito.eq(WorkspacePath.parse("/documents/top-uuid/words.md")), table.capture(),
                Mockito.eq(TOP.getUuid()));
        assertThat(table.getValue())
                .startsWith("| Word | Source |\n| --- | --- |\n| 10.0.0.0 | Dictionary 'Known bad' |\n")
                .contains("| 1.1.1.1 | Dictionary 'Malicious IPs' |");
        assertThat(result)
                .contains("It has 3001 word(s) in all")
                .contains("/documents/top-uuid/words.md")
                .contains("table tool");
    }

    @Test
    void aClustersCredentialsAreRedacted() {
        // As the Stroom MCP server redacted them: credentials in cluster documents are never given to the model.
        final DocRef cluster = new DocRef("ElasticCluster", "cluster-uuid", "ES_PROD");
        node(cluster);
        viewable.add(cluster.getUuid());
        indexable(cluster, Map.of("meta", """
                {"name": "ES_PROD", "connection": {"connectionUrls": ["https://es:9200"],
                 "apiKeySecret": "s3cret", "caCertificate": "-----BEGIN"}}"""), null);

        final String result = run("{\"uuid\":\"cluster-uuid\"}");

        assertThat(result)
                .contains("https://es:9200")
                .contains("\"apiKeySecret\" : \"<redacted>\"")
                .contains("\"caCertificate\" : \"<redacted>\"")
                .doesNotContain("s3cret")
                .doesNotContain("-----BEGIN");
    }

    @Test
    void anXsltsKeptMappingIsSummarisedAndTakenOutOfItsDescription() {
        // Seen with the Stroom MCP server: the whole kept mapping (1,400 lines) was spilled by the client to a
        // file, which the agent read in parts and then sent back whole to regenerate the XSLT.
        viewable.add(XSLT.getUuid());
        final String description = TestKeptMapping.described("Acme VPN events");
        indexable(XSLT, Map.of("xsl", "<xsl:stylesheet/>", "meta", JsonUtil.writeValueAsString(Map.of(
                        "name", XSLT.getName(), "description", description), true)),
                XsltDoc.builder().uuid(XSLT.getUuid()).name(XSLT.getName()).description(description).build());

        final String summary = run("{\"uuid\":\"xslt-uuid\"}");
        final String whole = run("{\"uuid\":\"xslt-uuid\",\"mapping\":true}");

        assertThat(summary)
                .contains("## Part 'xsl'\n<xsl:stylesheet/>")
                .contains("\"description\" : \"Acme VPN events\"")
                .contains("## Pending changes\n- Created")
                .contains("## Kept mapping")
                .contains("logon: Authenticate (`action` = login)")
                .contains("never send it back whole")
                .doesNotContain("<!-- stroom-mcp")
                .doesNotContain("EventSource/Generator");
        assertThat(whole)
                .contains("## Kept mapping")
                .contains("EventSource/Generator")
                .doesNotContain("<!-- stroom-mcp");
    }

    @Test
    void aDocumentationDocIsReadAPassageAtATime() {
        final DocRef manual = new DocRef(DocumentationDoc.TYPE, "manual-uuid", "Acme reference - events");
        node(manual);
        viewable.add(manual.getUuid());
        final String text = "# Events\nline\nEvent 4625: an account failed to log on (0xC000006A)\nline";
        indexable(manual, Map.of("txt", text), DocumentationDoc.builder().uuid(manual.getUuid())
                .name(manual.getName()).data(text).build());

        final String found = run("{\"uuid\":\"manual-uuid\",\"find\":\"0xc000006a\",\"contextLines\":0}");

        assertThat(JsonUtil.readValue(found, Map.class))
                .containsEntry("name", "Acme reference - events")
                .containsEntry("matches", 1)
                .containsEntry("passages", List.of(Map.of("lines", "3-3",
                        "text", "Event 4625: an account failed to log on (0xC000006A)")));
    }

    @Test
    void aLongDocumentationDocIsGivenWithItsOutline() {
        final DocRef manual = new DocRef(DocumentationDoc.TYPE, "manual-uuid", "Acme reference - events");
        node(manual);
        viewable.add(manual.getUuid());
        final StringBuilder text = new StringBuilder();
        for (int n = 0; n < 2_000; n++) {
            text.append(n % 100 == 0
                    ? "## Event " + n
                    : "line " + n + " " + "x".repeat(20)).append('\n');
        }
        indexable(manual, Map.of("txt", text.toString()), null);

        assertThat(run("{\"uuid\":\"manual-uuid\"}"))
                .contains("too large to show in full")
                .contains("Its outline:\n## Event 0\n## Event 100\n")
                .contains("Read the parts you need with find (an event id, a field name, a heading from the "
                          + "outline).");
    }

    @Test
    void aTypeThatDoesntMatchIsRefused() {
        viewable.add(XSLT.getUuid());
        xsltContent("<xsl:stylesheet/>");

        assertThat(run("{\"uuid\":\"xslt-uuid\",\"type\":\"Pipeline\"}"))
                .isEqualTo("Error: xslt-uuid is a XSLT, not a Pipeline.");
        assertThat(run("{\"uuid\":\"xslt-uuid\",\"type\":\"xslt\"}")).contains("## Part 'xsl'");
    }

    @Test
    void aPipelineIsGivenWithWhatItProcesses() {
        // Seen with the Stroom MCP server: asked which feeds a pipeline acts on, an agent found its filters
        // unreadable and searched by keyword.
        node(PIPELINE);
        viewable.add(PIPELINE.getUuid());
        indexable(PIPELINE, Map.of("json", "{\"elements\": {}}"), null);

        assertThat(run("{\"uuid\":\"pipe-uuid\"}"))
                .contains("## Processing\nIts processor filters (what it processes) need the Manage Processors "
                          + "permission");

        canManageProcessors();
        Mockito.when(processorFilterService.find(PIPELINE)).thenReturn(ResultPage.createUnboundedList(List.of()));
        assertThat(run("{\"uuid\":\"pipe-uuid\"}"))
                .contains("## Processing\nNo processor filters: this pipeline processes nothing on its own");

        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, FEED)
                .build();
        Mockito.when(processorFilterService.find(PIPELINE)).thenReturn(ResultPage.createUnboundedList(List.of(
                filter().enabled(true).priority(10).queryData(QueryData.builder().expression(expression).build())
                        .build())));
        assertThat(run("{\"uuid\":\"pipe-uuid\"}"))
                .contains("## Processing\n- Filter 7 (uuid filter-uuid): selects streams where Feed")
                .contains("FIREWALL-EVENTS")
                .contains("; enabled, priority 10");
    }

    private void node(final DocRef docRef) {
        Mockito.when(explorerNodeService.getNodeByUuid(docRef.getUuid()))
                .thenReturn(Optional.of(ExplorerNode.builder().docRef(docRef).build()));
    }

    private void indexable(final DocRef docRef, final Map<String, String> parts, final Object document) {
        final DocumentActionHandler<?> handler = Mockito.mock(DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ContentIndexable.class));
        Mockito.when(((ContentIndexable) handler).getIndexableData(docRef)).thenReturn(parts);
        Mockito.doReturn(document).when(handler).readDocument(docRef);
        handlers.put(new DocumentTypeName(docRef.getType()), handler);
    }

    private void canManageProcessors() {
        Mockito.when(securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION)).thenReturn(true);
    }

    private static ProcessorFilter.Builder filter() {
        return ProcessorFilter.builder()
                .id(7)
                .uuid("filter-uuid")
                .pipelineUuid(PIPELINE.getUuid())
                .pipelineName(PIPELINE.getName());
    }

    private String run(final String arguments) {
        return tool.execute(new ToolCall(null, ReadDocumentTool.NAME, arguments), ToolCalls.inChat(1, workspace))
                .text();
    }

    private void xsltContent(final String code) {
        final DocumentActionHandler<?> handler = Mockito.mock(DocumentActionHandler.class,
                Mockito.withSettings().extraInterfaces(ContentIndexable.class));
        Mockito.when(((ContentIndexable) handler).getIndexableData(XSLT)).thenReturn(Map.of("xsl", code));
        handlers.put(new DocumentTypeName("XSLT"), handler);
    }

    private void dictionary(final DocRef docRef, final String words, final DocRef... imports) {
        dictionaries.put(docRef.getUuid(), new DictionaryDoc(docRef.getUuid(), docRef.getName(), null, null, null,
                null, null, null, words, List.of(imports)));
    }

    private void combined(final DocRef docRef, final List<String> words, final List<DocRef> sources) {
        final WordList.Builder builder = WordList.builder(true);
        for (int i = 0; i < words.size(); i++) {
            builder.addWord(words.get(i), sources.get(i));
        }
        Mockito.when(wordListProvider.getCombinedWordList(docRef)).thenReturn(builder.build());
    }
}
