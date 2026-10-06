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

package stroom.gwt.workbench.client.app.main;

import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.client.presenter.ProcessorPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

/// Stories matching `App/Main/ProcessorFilterList` in the React Storybook, showing Stroom's real
/// [ProcessorPresenter] (a pipeline's 'Processors' tab: the processor and filter tree, as
/// `PipelinePresenter` shows it) with fake REST replies.
///
/// The React story's `ProcessorFilterApi.find` becomes `POST /processorFilter/v1/find`
/// (`ProcessorListRowResultPage`, whose rows are `processor` or `processorFilter` rows).
public final class ProcessorFilterListStories {

    private static final String FIND_PATH = "/processorFilter/v1/find";

    // One filter row, as the React story's makeApi(filter) builds it; FILTER_FIELDS and TRACKER
    // vary per story
    private static final String FILTER_ROWS = """
            {
              "values": [
                {
                  "type": "processorFilter",
                  "processorFilter": {
                    "id": 1, "priority": 10, "enabled": true, "maxProcessingTasks": 0,
                    "pipelineUuid": "p1", "pipelineName": "Events Pipeline",
                    FILTER_FIELDS
                    "processorFilterTracker": TRACKER
                  }
                }
              ],
              "pageResponse": {"offset": 0, "length": 1, "total": 1, "exact": true}
            }""";

    private static final String TRACKER = """
            {"status": "CREATED", "lastPollMs": 1700000000000, "lastPollTaskCount": 3, "metaCount": 12,
              "eventCount": 340}""";

    private static final String BACKOFF_TRACKER = """
            {"status": "CREATED", "lastPollMs": 1700000000000, "lastPollTaskCount": 0, "nextPollMs": 1700000600000,
              "metaCount": 12, "eventCount": 340}""";

    private static final String BACKOFF_ROWS = FILTER_ROWS
            .replace("FILTER_FIELDS", "\"maxTaskCreationDelay\": {\"time\": 5, \"timeUnit\": \"MINUTES\"},")
            .replace("TRACKER", BACKOFF_TRACKER);

    private static final String PLAIN_ROWS = FILTER_ROWS
            .replace("FILTER_FIELDS", "")
            .replace("TRACKER", TRACKER);

    // A processor (expanded) and its filter
    private static final String TREE_ROWS = """
            {
              "values": [
                {
                  "type": "processor",
                  "expander": {"depth": 0, "leaf": false, "expanded": true},
                  "processor": {"id": 7, "pipelineUuid": "p1", "pipelineName": "Events Pipeline", "enabled": true}
                },
                {
                  "type": "processorFilter",
                  "processorFilter": {
                    "id": 1, "priority": 10, "enabled": true, "pipelineUuid": "p1",
                    "pipelineName": "Events Pipeline",
                    "processorFilterTracker": {"status": "CREATED", "lastPollMs": 1700000000000,
                      "lastPollTaskCount": 0}
                  }
                }
              ],
              "pageResponse": {"offset": 0, "length": 2, "total": 2, "exact": true}
            }""";

    private ProcessorFilterListStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/ProcessorFilterList", ProcessorFilterListStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Task creation linear backoff: the filter Info popover's delay and next poll rows
                .story("InfoShowsBackoffRows", context -> render(context, BACKOFF_ROWS))
                .withPlay(play -> {
                    final Play screen = openInfo(play);
                    play.waitFor(() -> play.expect(screen.getByText("Max Task Creation Delay")).toBeInTheDocument());
                    // SimpleDuration.toLongString(): the title case unit, not the wire's MINUTES
                    play.expect(screen.getByText("5 Minutes")).toBeInTheDocument();
                    play.expect(screen.getByText("Next Poll")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A filter with no per-filter delay: GWT omits the delay row
                .story("InfoOmitsDelayRowWhenUnset", context -> render(context, PLAIN_ROWS))
                .withPlay(play -> {
                    final Play screen = openInfo(play);
                    // Differs from React: GWT's ProcessorInfoBuilder labels the row 'Max Concurrent
                    // Tasks', not 'Max Processing Tasks'
                    play.waitFor(() -> play.expect(screen.getByText("Max Concurrent Tasks")).toBeInTheDocument());
                    play.expect(screen.queryByText("Max Task Creation Delay")).toBeNull();
                    // Differs from React: GWT's addRowDateString leaves out the 'Next Poll' row when
                    // the filter is not backing off (no nextPollMs), rather than showing it blank
                    play.expect(screen.queryByText("Next Poll")).toBeNull();
                    play.expect(screen.getByText("Last Poll")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // 0 max processing tasks reads as "Unlimited" rather than "0"
                .story("InfoShowsUnlimitedTasks", context -> render(context, PLAIN_ROWS))
                .withPlay(play -> {
                    final Play screen = openInfo(play);
                    play.waitFor(() -> play.expect(screen.getByText("Unlimited")).toBeInTheDocument());
                    expectNoProblems(play);
                })
                // The expander column: GWT's ExpanderCell via TreeRowHandler
                .story("ExpanderTree", context -> render(context, TREE_ROWS))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW).count()).toBe(2));
                    // GWT's SVG expander, not a text triangle
                    play.expect(play.querySelectorAll(".expanderCell")).toHaveLength(2);
                    play.expect(play.body()).not().toHaveTextContent(TextMatch.containing("▾"));
                    play.expect(play.body()).not().toHaveTextContent(TextMatch.containing("▸"));
                    // The parent is an expanded arrow; the child is a leaf dot
                    play.expect(play.within(play.querySelectorAll(".expanderCell").nth(0))
                            .querySelector(".svg-image__arrow-down.expanderIcon.active")).not().toBeNull();
                    play.expect(play.within(play.querySelectorAll(".expanderCell").nth(1))
                            .querySelector(".svg-image__dot")).not().toBeNull();
                    // Depth 1 is indented by GWT's 20px per level
                    // Differs from React: GWT writes the style without spaces, so the padding is
                    // checked as a style rather than as the attribute's text
                    play.expect(play.querySelectorAll(".expanderCell").nth(1)).toHaveStyle("paddingLeft", "20px");
                    expectNoProblems(play);
                });
    }

    // Opens the filter row's Info popover (ProcessorInfoBuilder), shown on the page's body.
    // Differs from React: GWT's info cell is an SvgCell titled 'Info' (React's 'Processor filter
    // details')
    private static Play openInfo(final Play play) {
        play.click(play.findAllByTitle("Info").nth(0));
        return play.screen();
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final String rows) {
        final RestFixtures fixtures = RestFixtures.builder()
                .post(FIND_PATH, RestReply.json(rows))
                .build();
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        final DocRef pipelineRef = DocRef.builder().type(PipelineDoc.TYPE).uuid("p1").name("Events Pipeline").build();
        final PipelineDoc pipelineDoc = PipelineDoc.builder().uuid("p1").name("Events Pipeline").build();
        harness.afterStartUp(() -> {
            final ProcessorPresenter presenter = injector.getProcessorPresenter();
            // As PipelinePresenter's 'Processors' tab reads it
            presenter.read(pipelineRef, pipelineDoc, false);
            presenter.setIsAdmin(true);
            presenter.setAllowUpdate(true);
            harness.addContent(presenter);
        });
        return harness.asWidget();
    }
}
