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

import stroom.ai.impl.workspace.WorkspaceException;
import stroom.dictionary.shared.DictionaryDoc;
import stroom.docref.DocRef;
import stroom.feed.shared.FeedDoc;
import stroom.meta.shared.MetaFields;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.TextConverterDoc;
import stroom.pipeline.shared.XsltDoc;
import stroom.processor.api.ProcessorFilterService;
import stroom.processor.shared.ProcessorFilter;
import stroom.processor.shared.QueryData;
import stroom.query.api.ExpressionOperator;
import stroom.query.api.ExpressionTerm.Condition;
import stroom.security.shared.AppPermission;

import event.logging.MultiObject;
import event.logging.OtherObject;
import event.logging.SearchEventAction;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.mockito.Mockito;

import java.nio.file.Path;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TestDocumentLinksTool {

    @TempDir
    Path tempDir;

    private ToolTestFixture fixture;
    private final ProcessorFilterService processorFilterService = Mockito.mock(ProcessorFilterService.class);

    @BeforeEach
    void setUp() {
        fixture = new ToolTestFixture(tempDir);
        final DocRef feed = fixture.add(new DocRef(FeedDoc.TYPE, "feed", "WINDOWS-EVENTS"), null, true);
        final DocRef hiddenFeed = fixture.add(new DocRef(FeedDoc.TYPE, "hidden-feed", "SECRET-EVENTS"), null, false);
        final DocRef pipeline = fixture.add(new DocRef(PipelineDoc.TYPE, "pipeline", "Windows pipeline"), null,
                true);
        final DocRef hiddenPipeline = fixture.add(new DocRef(PipelineDoc.TYPE, "hidden-pipeline", "Secret pipeline"),
                null, false);
        final DocRef xslt = fixture.add(new DocRef(XsltDoc.TYPE, "xslt", "Windows XSLT"), null, true);
        final DocRef failingXslt = fixture.add(new DocRef(XsltDoc.TYPE, "failing-xslt", "Failing XSLT"), null,
                true);
        fixture.failing.add("failing-xslt");
        final DocRef textConverter = fixture.add(new DocRef(TextConverterDoc.TYPE, "converter", "Secret converter"),
                null, false);
        final DocRef dictionary = fixture.add(new DocRef(DictionaryDoc.TYPE, "dictionary", "Admins"), null, true);
        // Processor filters aren't documents: the graph names them by UUID only.
        final DocRef filter = new DocRef(ProcessorFilter.ENTITY_TYPE, "filter", null);
        final DocRef hiddenFilter = new DocRef(ProcessorFilter.ENTITY_TYPE, "hidden-filter", null);

        fixture.link(filter, feed);
        fixture.link(filter, hiddenFeed);
        fixture.link(filter, pipeline);
        fixture.link(hiddenFilter, feed);
        fixture.link(hiddenFilter, hiddenPipeline);
        fixture.link(pipeline, xslt);
        fixture.link(pipeline, failingXslt);
        fixture.link(pipeline, textConverter);
        fixture.link(xslt, dictionary);
    }

    @Test
    void dependantsOfAFeedLeadThroughItsFiltersToWhatProcessesIt() {
        final String result = run("""
                {"uuids":["feed"],"direction":"dependants","depth":3,"types":["Pipeline","XSLT"]}""");

        assertThat(result)
                .contains("2 document(s) found of the types asked for")
                .contains("- Pipeline 'Windows pipeline' (pipeline) in /Lookups: 2 link(s) away, via Feed "
                          + "'WINDOWS-EVENTS' → used by Processor filter → runs Pipeline 'Windows pipeline'")
                .contains("- XSLT 'Windows XSLT' (xslt) in /Lookups: 3 link(s) away, via Feed 'WINDOWS-EVENTS' → "
                          + "used by Processor filter → runs Pipeline 'Windows pipeline' → uses XSLT "
                          + "'Windows XSLT'");
        // Nothing the user can't view, or whose check fails, is returned or hinted at.
        assertThat(result)
                .doesNotContain("Secret")
                .doesNotContain("hidden")
                .doesNotContain("Failing");
    }

    @Test
    void aProcessorFilterIsShownWithItsPipelineOnlyIfThePipelineIsVisible() {
        final String result = run("{\"uuids\":[\"feed\"],\"depth\":1}");

        assertThat(result)
                .contains("1 document(s) found")
                .contains("- Processor filter (filter) of pipeline 'Windows pipeline' (pipeline): 1 link(s) away")
                .doesNotContain("hidden-filter");
    }

    @Test
    void aPipelinesFiltersSayWhatTheySelectToAUserWhoMayManageProcessors() {
        canManageProcessors(true);
        final ExpressionOperator expression = ExpressionOperator.builder()
                .addDocRefTerm(MetaFields.FEED, Condition.IS_DOC_REF, new DocRef(FeedDoc.TYPE, "feed",
                        "WINDOWS-EVENTS"))
                .addTextTerm(MetaFields.TYPE, Condition.EQUALS, "Raw Events")
                .build();
        Mockito.when(processorFilterService.fetchByUuid("filter")).thenReturn(Optional.of(filter()
                .enabled(true)
                .queryData(QueryData.builder().expression(expression).build())
                .build()));

        final String result = run("{\"uuids\":[\"pipeline\"],\"depth\":1}");

        assertThat(result)
                .contains("1 document(s) found")
                .contains("- Processor filter (filter) of pipeline 'Windows pipeline' (pipeline): 1 link(s) away, "
                          + "via Pipeline 'Windows pipeline' → used by Processor filter. It selects streams where "
                          + "Feed is 'WINDOWS-EVENTS' and Type is 'Raw Events'; enabled.");
    }

    @Test
    void aFilterThatSelectsEverythingSaysSo() {
        canManageProcessors(true);
        Mockito.when(processorFilterService.fetchByUuid("filter")).thenReturn(Optional.of(filter().build()));

        assertThat(run("{\"uuids\":[\"pipeline\"],\"depth\":1}"))
                .contains("used by Processor filter. It selects all streams; disabled.");
    }

    @Test
    void withoutManageProcessorsAFilterGivesOnlyTheFeedsItLinksToThatTheUserCanView() {
        final String result = run("{\"uuids\":[\"pipeline\"],\"depth\":1}");

        assertThat(result)
                .contains("used by Processor filter. It links to Feed 'WINDOWS-EVENTS'; what it selects needs the "
                          + "Manage Processors permission, as the Processors tab does.")
                .doesNotContain("SECRET")
                .doesNotContain("hidden-feed")
                .doesNotContain("selects streams");
        Mockito.verifyNoInteractions(processorFilterService);
    }

    @Test
    void aFilterThatCantBeReadFallsBackToItsVisibleFeeds() {
        canManageProcessors(true);
        Mockito.when(processorFilterService.fetchByUuid("filter")).thenThrow(new IllegalStateException("no"));

        assertThat(run("{\"uuids\":[\"pipeline\"],\"depth\":1}"))
                .contains("It links to Feed 'WINDOWS-EVENTS'; what it selects couldn't be read.")
                .doesNotContain("SECRET");
    }

    @Test
    void dependenciesAreFollowedToTheGivenDepth() {
        final String result = run("{\"uuids\":[\"pipeline\"],\"direction\":\"dependencies\",\"depth\":2}");

        assertThat(result)
                .contains("2 document(s) found")
                .contains("XSLT 'Windows XSLT' (xslt) in /Lookups: 1 link(s) away")
                .contains("Dictionary 'Admins' (dictionary) in /Lookups: 2 link(s) away, via Pipeline "
                          + "'Windows pipeline' → uses XSLT 'Windows XSLT' → uses Dictionary 'Admins'")
                .doesNotContain("converter")
                .doesNotContain("Failing");
    }

    @Test
    void aDocumentTheUserCantViewIsTreatedAsMissing() {
        assertThatThrownBy(() -> run("{\"uuids\":[\"hidden-pipeline\"]}"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessage(DocumentLinksTool.NOT_FOUND);
        assertThatThrownBy(() -> run("{\"uuids\":[\"no-such-doc\"]}"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessage(DocumentLinksTool.NOT_FOUND);
        assertThat(run("{\"uuids\":[\"hidden-pipeline\", \"feed\"],\"depth\":1}"))
                .contains("Not found, or you don't have permission to view them: hidden-pipeline.");
    }

    @Test
    void theSearchIsLoggedNamingTheDocumentsReturned() {
        run("{\"uuids\":[\"feed\"],\"depth\":3,\"types\":[\"XSLT\"]}");

        final List<SearchEventAction> events = fixture.logged(SearchEventAction.class);
        assertThat(events).hasSize(1);
        assertThat(events.getFirst().getQuery().getRaw())
                .isEqualTo("dependants of Feed 'WINDOWS-EVENTS' (feed), depth 3");
        final MultiObject results = events.getFirst().getResults();
        assertThat(results.getObjects())
                .map(object -> ((OtherObject) object).getId())
                .containsExactly("xslt");
    }

    @Test
    void theLimitCutsWhatIsShownAndOutWritesItAll() {
        final String result = run("{\"uuids\":[\"feed\"],\"depth\":3,\"limit\":1,\"out\":\"/outputs/links.txt\"}");

        assertThat(result)
                .contains("3 document(s) found")
                .contains("(2 more not shown")
                .contains("Wrote them all to /outputs/links.txt");
        assertThat(fixture.written.get("/outputs/links.txt").lines()).hasSize(3);
    }

    @Test
    void aBadDirectionIsRefused() {
        assertThatThrownBy(() -> run("{\"uuids\":[\"feed\"],\"direction\":\"sideways\"}"))
                .isInstanceOf(WorkspaceException.class)
                .hasMessageContaining("dependants or dependencies");
    }

    private void canManageProcessors(final boolean can) {
        Mockito.when(fixture.securityContext.hasAppPermission(AppPermission.MANAGE_PROCESSORS_PERMISSION))
                .thenReturn(can);
    }

    private static ProcessorFilter.Builder filter() {
        return ProcessorFilter.builder()
                .id(7)
                .uuid("filter")
                .pipelineUuid("pipeline")
                .pipelineName("Windows pipeline");
    }

    private String run(final String arguments) {
        final DocumentLinksTool tool = new DocumentLinksTool(fixture.workspace, () -> fixture.dependencyService,
                fixture.documentAccess(), fixture.processorFilterAccess(processorFilterService),
                fixture.auditLog());
        return tool.run(ToolTestFixture.request(DocumentLinksTool.NAME, arguments), 1);
    }
}
