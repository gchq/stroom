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

import stroom.docref.DocRef;
import stroom.docstore.api.DocFinder;
import stroom.entity.shared.ExpressionCriteria;
import stroom.explorer.shared.ExplorerConstants;
import stroom.explorer.shared.ExplorerNode;
import stroom.feed.shared.FeedDoc;
import stroom.meta.api.MetaService;
import stroom.meta.shared.FindMetaCriteria;
import stroom.meta.shared.Meta;
import stroom.meta.shared.MetaFields;
import stroom.meta.shared.MetaRow;
import stroom.meta.shared.SelectionSummary;
import stroom.meta.shared.Status;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.api.ProcessorTaskService;
import stroom.processor.shared.FetchProcessorRequest;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.ProcessorFilterRow;
import stroom.processor.shared.ProcessorFilterTracker;
import stroom.processor.shared.ProcessorFilterTrackerStatus;
import stroom.processor.shared.ProcessorListRow;
import stroom.processor.shared.ProcessorTaskSummary;
import stroom.processor.shared.QueryData;
import stroom.processor.shared.TaskStatus;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;
import stroom.util.shared.Range;
import stroom.util.shared.ResultPage;

import event.logging.OtherObject;
import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.ArgumentCaptor;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

class TestFolderContentsTool {

    private static final DocRef SECURITY = folder("security-uuid", "Security");
    private static final DocRef FIREWALLS = folder("firewalls-uuid", "Firewalls");
    private static final DocRef SECRET_FOLDER = folder("secret-folder-uuid", "Secret");
    private static final DocRef FEED = new DocRef(FeedDoc.TYPE, "feed-uuid", "FIREWALL-EVENTS");
    private static final DocRef SECRET_FEED = new DocRef(FeedDoc.TYPE, "secret-feed-uuid", "SECRET-EVENTS");
    private static final DocRef PIPELINE = new DocRef(PipelineDoc.TYPE, "pipe-uuid", "Firewall pipeline");
    private static final DocRef SECRET_PIPELINE = new DocRef(PipelineDoc.TYPE, "secret-pipe-uuid", "Secret pipe");
    private static final DocRef XSLT = new DocRef("XSLT", "xslt-uuid", "Firewall XSLT");
    private static final DocRef HIDDEN_XSLT = new DocRef("XSLT", "hidden-xslt-uuid", "Hidden XSLT");

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final MetaService metaService = Mockito.mock(MetaService.class);
    private final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);
    private final ProcessorTaskService processorTaskService = Mockito.mock(ProcessorTaskService.class);
    private final DocFinder docFinder = Mockito.mock(DocFinder.class);
    private final Map<String, List<DocRef>> children = new HashMap<>();

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        fixture.add(SECURITY, null, true);
        fixture.add(FIREWALLS, null, true);
        fixture.add(SECRET_FOLDER, null, false);
        fixture.add(FEED, null, true);
        fixture.add(SECRET_FEED, null, false);
        fixture.add(PIPELINE, null, true);
        fixture.add(SECRET_PIPELINE, null, false);
        fixture.add(XSLT, null, true);
        fixture.add(HIDDEN_XSLT, null, true);
        children.put(ExplorerConstants.SYSTEM_DOC_REF.getUuid(), List.of(SECURITY));
        children.put(SECURITY.getUuid(), List.of(FIREWALLS, SECRET_FOLDER));
        children.put(FIREWALLS.getUuid(), List.of(XSLT, FEED, PIPELINE, SECRET_FEED));
        // Viewable, but in a folder the user can't view.
        children.put(SECRET_FOLDER.getUuid(), List.of(HIDDEN_XSLT));

        Mockito.when(fixture.explorerNodeService.getChildren(Mockito.any())).thenAnswer(invocation ->
                children.getOrDefault(invocation.<DocRef>getArgument(0).getUuid(), List.of()).stream()
                        .map(TestFolderContentsTool::node)
                        .toList());
        Mockito.when(fixture.explorerNodeService.getNodesByName(Mockito.any(), Mockito.anyString()))
                .thenAnswer(invocation -> children.getOrDefault(invocation.<ExplorerNode>getArgument(0).getUuid(),
                                List.of()).stream()
                        .filter(docRef -> docRef.getName().equals(invocation.getArgument(1)))
                        .map(TestFolderContentsTool::node)
                        .toList());
        fixture.folders.put(SECURITY.getUuid(), List.of());
        fixture.folders.put(FIREWALLS.getUuid(), List.of("Security"));
        fixture.folders.put(SECRET_FOLDER.getUuid(), List.of("Security"));
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(true);
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, FEED.getName())).thenReturn(List.of(FEED));
        Mockito.when(docFinder.findByName(FeedDoc.TYPE, SECRET_FEED.getName())).thenReturn(List.of(SECRET_FEED));
    }

    @Test
    void listsTheDocumentsTheUserCanViewAsLinks() {
        final String result = run("{\"folder\":\"firewalls-uuid\"}");

        assertThat(result)
                .startsWith("Folder 'Firewalls' (/Security/Firewalls, uuid firewalls-uuid, [Firewalls]"
                            + "(?action=open-doc&docType=Folder&docUuid=firewalls-uuid))")
                .contains("Documents: 3 — 1 Feed, 1 Pipeline, 1 XSLT")
                .contains("- [FIREWALL-EVENTS](?action=open-doc&docType=Feed&docUuid=feed-uuid) (Feed, uuid "
                          + "feed-uuid)")
                .contains("- [Firewall XSLT](?action=open-doc&docType=XSLT&docUuid=xslt-uuid) (XSLT, uuid "
                          + "xslt-uuid)")
                .doesNotContain("SECRET-EVENTS")
                .doesNotContain("secret-feed-uuid")
                // Nothing else was asked for.
                .doesNotContain("Streams")
                .doesNotContain("Processor filters");
        Mockito.verifyNoInteractions(metaService, processorFilterService, processorTaskService);
        final List<SearchEventAction> searches = fixture.logged(SearchEventAction.class);
        assertThat(searches).hasSize(1);
        assertThat(searches.getFirst().getQuery().getRaw())
                .isEqualTo("contents of Folder '/Security/Firewalls', showing documents, depth 1");
        assertThat(searches.getFirst().getResults().getObjects())
                .extracting(object -> ((OtherObject) object).getId())
                .containsExactly("feed-uuid", "pipe-uuid", "xslt-uuid");
    }

    @Test
    void foldersTheUserCantViewAreNotLookedIn() {
        final String result = run("{\"folder\":\"/Security\",\"depth\":3}");

        assertThat(result)
                .contains("Documents (3 levels down): 4 — 1 Feed, 1 Folder, 1 Pipeline, 1 XSLT")
                .contains("- [Firewalls](?action=open-doc&docType=Folder&docUuid=firewalls-uuid)")
                // One level further in.
                .contains("  - [Firewall XSLT]")
                .doesNotContain("Secret")
                .doesNotContain("Hidden XSLT")
                .contains("Documents in folders you can't view aren't listed.");
        Mockito.verify(fixture.explorerNodeService, Mockito.never()).getChildren(SECRET_FOLDER);
    }

    @Test
    void onlyTheTypesAskedForAreListedWithTheFoldersThatHoldThem() {
        final String result = run("{\"folder\":\"/\",\"depth\":3,\"types\":[\"XSLT\"]}");

        assertThat(result)
                .startsWith("The top of the Explorer tree (/)")
                .contains("Documents (3 levels down): 1 — 1 XSLT")
                .contains("- [Security]")
                .contains("  - [Firewalls]")
                .contains("    - [Firewall XSLT]")
                .doesNotContain("FIREWALL-EVENTS");
    }

    @Test
    void aFolderTheUserCantViewIsRefusedWithoutSayingIfItExists() {
        final String refused = run("{\"folder\":\"secret-folder-uuid\"}");

        assertThat(refused).startsWith("No folder was found");
        assertThat(run("{\"folder\":\"no-such-uuid\"}")).isEqualTo(refused);
        // Nor can it be reached by its path, nor anything below it.
        assertThat(run("{\"folder\":\"/Security/Secret\"}")).isEqualTo(refused);
        // A document isn't a folder.
        assertThat(run("{\"folder\":\"xslt-uuid\"}")).isEqualTo(refused);
        Mockito.verify(fixture.explorerNodeService, Mockito.never()).getChildren(Mockito.any());
        assertThat(fixture.logged(SearchEventAction.class)).isEmpty();
    }

    @Test
    void streamsAreCountedAndTheMostRecentListed() {
        Mockito.when(metaService.getSelectionSummary(Mockito.any())).thenReturn(new SelectionSummary(42, 1,
                Set.of("FIREWALL-EVENTS"), 2, Set.of("Raw Events", "Events"), 1, 1, 1, Set.of("Unlocked"),
                new Range<>(0L, 86_400_000L)));
        Mockito.when(metaService.findRows(Mockito.any())).thenReturn(new ResultPage<>(List.of(
                new MetaRow(meta(7, "Events"), PIPELINE, Map.of()),
                new MetaRow(meta(6, "Raw Events"), null, Map.of()))));

        final String result = run("{\"folder\":\"firewalls-uuid\",\"show\":[\"streams\"]}");

        assertThat(result)
                .contains("Streams (Unlocked, of feeds in the folder or produced by its pipelines): 42 in 1 "
                          + "feed(s) (FIREWALL-EVENTS), of 2 type(s) (Events, Raw Events), created "
                          + "1970-01-01T00:00:00.000Z to 1970-01-02T00:00:00.000Z.")
                .contains("- [stream 7](?action=open-stream&id=7) FIREWALL-EVENTS, Events, created "
                          + "1970-01-01T00:00:00.000Z, produced by pipeline 'Firewall pipeline'")
                .contains("- [stream 6](?action=open-stream&id=6) FIREWALL-EVENTS, Raw Events")
                .doesNotContain("Documents");
        final ArgumentCaptor<FindMetaCriteria> criteria = ArgumentCaptor.forClass(FindMetaCriteria.class);
        Mockito.verify(metaService).findRows(criteria.capture());
        assertThat(criteria.getValue().getExpression().toString())
                .contains("Feed in folder Firewalls")
                .contains("Pipeline in folder Firewalls")
                .contains("Status = Unlocked");
        assertThat(criteria.getValue().getSortList().getFirst().getId()).isEqualTo("Id");
        assertThat(criteria.getValue().getSortList().getFirst().isDesc()).isTrue();
        assertThat(criteria.getValue().getPageRequest().getLength()).isEqualTo(FolderContentsTool.MAX_STREAMS);
        assertThat(fixture.logged(SearchEventAction.class))
                .extracting(search -> search.getQuery().getRaw())
                .contains("streams of the feeds and pipelines in /Security/Firewalls");
    }

    @Test
    void theStreamsOfTheWholeTreeHaveNoFolderTerm() {
        Mockito.when(metaService.getSelectionSummary(Mockito.any())).thenReturn(new SelectionSummary(0, 0,
                Set.of(), 0, Set.of(), 0, 0, 0, Set.of(), null));

        assertThat(run("{\"show\":[\"streams\"]}")).contains("Streams (Unlocked, of feeds in the folder or "
                                                             + "produced by its pipelines): none you can see.");

        final ArgumentCaptor<FindMetaCriteria> criteria = ArgumentCaptor.forClass(FindMetaCriteria.class);
        Mockito.verify(metaService).getSelectionSummary(criteria.capture());
        assertThat(criteria.getValue().getExpression().toString())
                .doesNotContain("folder")
                .contains("Status = Unlocked");
        Mockito.verify(metaService, Mockito.never()).findRows(Mockito.any());
    }

    @Test
    void processorFiltersAreListedOnlyForPipelinesTheUserCanView() {
        final ProcessorFilterTracker tracker = ProcessorFilterTracker.builder()
                .status(ProcessorFilterTrackerStatus.COMPLETE)
                .metaCount(12L)
                .eventCount(3_400L)
                .build();
        final List<ProcessorListRow> rows = new ArrayList<>();
        rows.add(new ProcessorFilterRow(filter(1, PIPELINE).priority(20).enabled(true)
                .processorFilterTracker(tracker).build()));
        rows.add(new ProcessorFilterRow(filter(2, SECRET_PIPELINE).enabled(true).build()));
        rows.add(new ProcessorFilterRow(filter(3, PIPELINE).deleted(true).build()));
        Mockito.when(processorFilterService.find(Mockito.any(FetchProcessorRequest.class)))
                .thenReturn(ResultPage.createUnboundedList(rows));

        final String result = run("{\"folder\":\"firewalls-uuid\",\"show\":[\"processors\"]}");

        assertThat(result)
                .contains("Processor filters: 1\n- Filter 1 (uuid filter-1-uuid) of Pipeline [Firewall pipeline]"
                          + "(?action=open-doc&docType=Pipeline&docUuid=pipe-uuid): selects all streams; enabled, "
                          + "priority 20, Complete, 12 stream(s), 3400 event(s)")
                .doesNotContain("Filter 2")
                .doesNotContain("Secret pipe")
                .doesNotContain("Filter 3");
        final ArgumentCaptor<FetchProcessorRequest> request = ArgumentCaptor.forClass(FetchProcessorRequest.class);
        Mockito.verify(processorFilterService).find(request.capture());
        assertThat(request.getValue().getExpression().toString())
                .contains("Pipeline in folder Firewalls")
                .contains("Analytic Rule in folder Firewalls");
    }

    @Test
    void eachProcessorFilterSaysWhatItSelects() {
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, FEED)
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Raw Events")
                .build();
        final List<ProcessorListRow> rows = List.of(new ProcessorFilterRow(filter(4, PIPELINE).enabled(false)
                .queryData(QueryData.builder().expression(expression).build())
                .minMetaCreateTimeMs(0L)
                .maxMetaCreateTimeMs(86_400_000L)
                .build()));
        Mockito.when(processorFilterService.find(Mockito.any(FetchProcessorRequest.class)))
                .thenReturn(ResultPage.createUnboundedList(rows));

        assertThat(run("{\"folder\":\"firewalls-uuid\",\"show\":[\"processors\"]}"))
                .contains("- Filter 4 (uuid filter-4-uuid) of Pipeline [Firewall pipeline]"
                          + "(?action=open-doc&docType=Pipeline&docUuid=pipe-uuid): selects streams where Feed is "
                          + "'FIREWALL-EVENTS' and Type is 'Raw Events', created on or after "
                          + "1970-01-01T00:00:00.000Z, created on or before 1970-01-02T00:00:00.000Z; disabled, "
                          + "priority");
    }

    @Test
    void processorsAndTasksNeedTheManageProcessorsPermission() {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(false);

        assertThat(run("{\"folder\":\"firewalls-uuid\",\"show\":[\"processors\",\"tasks\"]}"))
                .contains("Processor filters: you need the Manage Processors permission")
                .contains("Processing tasks: you need the Manage Processors permission");

        // A check that fails is failed closed.
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenThrow(new IllegalStateException("no"));
        assertThat(run("{\"folder\":\"firewalls-uuid\",\"show\":[\"processors\"]}"))
                .contains("Processor filters: you need the Manage Processors permission");
        Mockito.verifyNoInteractions(processorFilterService, processorTaskService);
    }

    @Test
    void tasksAreCountedOnlyForPipelinesAndFeedsTheUserCanView() {
        Mockito.when(processorTaskService.findSummary(Mockito.any())).thenReturn(ResultPage.createUnboundedList(
                List.of(new ProcessorTaskSummary(PIPELINE, FEED.getName(), 10, TaskStatus.COMPLETE, 30L),
                        new ProcessorTaskSummary(PIPELINE, FEED.getName(), 10, TaskStatus.QUEUED, 4L),
                        new ProcessorTaskSummary(PIPELINE, SECRET_FEED.getName(), 10, TaskStatus.QUEUED, 9L),
                        new ProcessorTaskSummary(SECRET_PIPELINE, FEED.getName(), 10, TaskStatus.QUEUED, 5L),
                        new ProcessorTaskSummary(PIPELINE, "NO-SUCH-FEED", 10, TaskStatus.QUEUED, 2L))));

        final String result = run("{\"folder\":\"firewalls-uuid\",\"show\":[\"tasks\"]}");

        assertThat(result)
                .contains("Processing tasks: counts by status\n- Pipeline [Firewall pipeline](?action=open-doc"
                          + "&docType=Pipeline&docUuid=pipe-uuid), feed FIREWALL-EVENTS: 30 Complete, 4 Queued")
                .doesNotContain("SECRET")
                .doesNotContain("Secret")
                .doesNotContain("NO-SUCH-FEED");
        final ArgumentCaptor<ExpressionCriteria> criteria = ArgumentCaptor.forClass(ExpressionCriteria.class);
        Mockito.verify(processorTaskService).findSummary(criteria.capture());
        assertThat(criteria.getValue().getExpression().toString())
                .contains("Pipeline in folder Firewalls")
                .contains("Feed in folder Firewalls");
    }

    @Test
    void foldersAnAgentsDocIgnoresAreShownButNotLookedInAndItsInstructionsGiven() {
        final DocRef archive = fixture.add(folder("archive-uuid", "Archive"), null, true);
        final DocRef old = fixture.add(new DocRef("XSLT", "old-xslt-uuid", "Old XSLT"), null, true);
        fixture.folders.put(archive.getUuid(), List.of("Security"));
        children.put(SECURITY.getUuid(), List.of(FIREWALLS, SECRET_FOLDER, archive));
        children.put(archive.getUuid(), List.of(old));
        fixture.addInstructions("agents-uuid", "## Ignore\n- folders: Archive\n\n## Naming\n- Feeds are upper "
                                               + "case.", true, "Security");

        final String result = run("{\"folder\":\"/Security\",\"depth\":3}");

        assertThat(result)
                .contains("- [Archive](?action=open-doc&docType=Folder&docUuid=archive-uuid) (Folder, uuid "
                          + "archive-uuid) — not looked in: /Security/Archive (ignored by the AGENTS doc in "
                          + "/Security)")
                .doesNotContain("Old XSLT")
                .contains("pass includeIgnored only if the user asks for them")
                .contains(AgentInstructions.FOLLOW)
                .contains("Feeds are upper case.");
        Mockito.verify(fixture.explorerNodeService, Mockito.never()).getChildren(archive);

        // Asked for, it is looked in.
        assertThat(run("{\"folder\":\"/Security\",\"depth\":3,\"includeIgnored\":true}"))
                .contains("Old XSLT");
        // As is the ignored folder itself, when it is the one asked about.
        assertThat(run("{\"folder\":\"archive-uuid\"}"))
                .contains("It is in /Security/Archive (ignored by the AGENTS doc in /Security), so searches leave "
                          + "it out; it is listed as it was asked for.")
                .contains("Old XSLT");
    }

    @Test
    void somethingThatCantBeShownIsAnError() {
        assertThat(run("{\"show\":[\"documents\",\"secrets\"]}"))
                .isEqualTo("Error: can't show 'secrets'. Show any of: documents, streams, processors, tasks.");
    }

    private String run(final String arguments) {
        return tool().execute(ToolTestFixture.request(FolderContentsTool.NAME, arguments), ToolCalls.inChat(1,
                fixture.workspace)).text();
    }

    private FolderContentsTool tool() {
        return new FolderContentsTool(fixture.documentAccess(), fixture.processorFilterAccess(processorFilterService),
                () -> metaService,
                () -> processorFilterService, () -> processorTaskService, () -> docFinder,
                fixture.agentInstructions(), fixture.auditLog());
    }

    private static DocRef folder(final String uuid, final String name) {
        return new DocRef(ExplorerConstants.FOLDER_TYPE, uuid, name);
    }

    private static ExplorerNode node(final DocRef docRef) {
        return ExplorerNode.builder().docRef(docRef).build();
    }

    private static Meta meta(final long id, final String type) {
        return Meta.builder()
                .id(id)
                .feedName(FEED.getName())
                .typeName(type)
                .status(Status.UNLOCKED)
                .createMs(0L)
                .build();
    }

    private static ProcessorFilter.Builder filter(final int id, final DocRef pipeline) {
        return ProcessorFilter.builder()
                .id(id)
                .uuid("filter-" + id + "-uuid")
                .pipelineUuid(pipeline.getUuid())
                .pipelineName(pipeline.getName());
    }
}
