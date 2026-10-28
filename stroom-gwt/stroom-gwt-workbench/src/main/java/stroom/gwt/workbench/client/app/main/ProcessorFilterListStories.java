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

import stroom.data.client.ShowDataEvent;
import stroom.docref.DocRef;
import stroom.gwt.workbench.client.app.gin.processing.ProcessingScreenGinjector;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.pipeline.shared.PipelineDoc;
import stroom.processor.client.component.ProcessorsController;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Consumer;

/// Stories of `App/Main/ProcessorFilterList`, showing Stroom's real [ProcessorsController] (a
/// pipeline's 'Processors' tab: the processor and filter tree, as `PipelineEditor` shows it)
/// with fake REST replies.
///
/// The rows come from `POST /processorFilter/v1/find` (`ProcessorListRowResultPage`, whose rows are
/// `processor` or `processorFilter` rows).
public final class ProcessorFilterListStories {

    private static final String FIND_PATH = "/processorFilter/v1/find";

    // One filter row; FILTER_FIELDS and TRACKER vary per story
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

    // No status (ProcessorStatusUtil once read it without a null check)
    private static final String TRACKER = """
            {"lastPollMs": 1700000000000, "lastPollTaskCount": 3, "metaCount": 12,
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

    // The tree, its filter's tracker naming the first stream it read (a link in the filter's details)
    private static final String LINKED_TREE_ROWS = TREE_ROWS.replace(
            "\"lastPollTaskCount\": 0}",
            "\"lastPollTaskCount\": 0, \"minMetaId\": 42}");

    // A stream link's open icon (OpenLinkUtil)
    private static final String STREAM_LINK_OPEN = "[link-type=\"stream\"] .docRefLinkOpen";

    // Told each time a stream is asked to be opened
    private static final String SHOW_DATA_SPY = "showData";

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
                    // ProcessorInfoBuilder labels the row 'Max Concurrent Tasks'
                    play.waitFor(() -> play.expect(screen.getByText("Max Concurrent Tasks")).toBeInTheDocument());
                    play.expect(screen.queryByText("Max Task Creation Delay")).toBeNull();
                    // addRowDateString leaves out the 'Next Poll' row when the filter is not backing off
                    // (no nextPollMs)
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
                    play.expect(play.querySelectorAll(".expander-cell")).toHaveLength(2);
                    play.expect(play.body()).not().toHaveTextContent(TextMatch.containing("▾"));
                    play.expect(play.body()).not().toHaveTextContent(TextMatch.containing("▸"));
                    // The parent is an expanded arrow; the child is a leaf dot
                    play.expect(play.within(play.querySelectorAll(".expander-cell").nth(0))
                            .querySelector(".svg-image__arrow-down.expander-cell__icon--active"))
                            .not().toBeNull();
                    play.expect(play.within(play.querySelectorAll(".expander-cell").nth(1))
                            .querySelector(".svg-image__dot")).not().toBeNull();
                    // Depth 1 is indented by GWT's 20px per level
                    // GWT writes the style without spaces, so the padding is checked as a style rather
                    // than as the attribute's text
                    play.expect(play.querySelectorAll(".expander-cell").nth(1)).toHaveStyle("paddingLeft", "20px");
                    expectNoProblems(play);
                })
                // Regression: each row selected added another handler for the details' links, so one
                // click on a link opened its stream once for every selection made
                .story("DetailsLinkOpensOnce", context -> {
                    final Spy showData = context.fn(SHOW_DATA_SPY);
                    return render(context, LINKED_TREE_ROWS, harness -> harness.getEventBus()
                            .addHandler(ShowDataEvent.getType(), event -> showData.call()));
                })
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelectorAll(StroomDom.GRID_ROW).count()).toBe(2));
                    // The filter, the processor, then the filter again, each by its pipeline's name (the
                    // middle of a row is the priority spinner, which keeps its clicks)
                    for (final int row : new int[]{1, 0, 1}) {
                        play.click(play.within(play.querySelectorAll(StroomDom.GRID_ROW).nth(row))
                                .getByText("Events Pipeline"));
                    }
                    // The filter's details, with its first stream as a link (the grid's pipelines have
                    // links of their own)
                    play.waitFor(() -> play.expect(play.getByText("Min Stream Id")).toBeInTheDocument());
                    play.click(play.querySelector(STREAM_LINK_OPEN));
                    play.expect(play.spy(SHOW_DATA_SPY)).toHaveBeenCalledTimes(1);
                    expectNoProblems(play);
                });
    }

    // Opens the filter row's Info popover (ProcessorInfoBuilder), shown on the page's body.
    // The info cell is an IconCell titled 'Info'
    private static Play openInfo(final Play play) {
        play.click(play.findAllByTitle("Info").nth(0));
        return play.screen();
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static Widget render(final StoryContext context, final String rows) {
        return render(context, rows, harness -> {
        });
    }

    private static Widget render(final StoryContext context,
                                 final String rows,
                                 final Consumer<ScreenHarness> beforeShow) {
        final RestFixtures fixtures = RestFixtures.builder()
                .post(FIND_PATH, RestReply.json(rows))
                .build();
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures)
                .injector(injector)
                .build();
        final DocRef pipelineRef = DocRef.builder().type(PipelineDoc.TYPE).uuid("p1").name("Events Pipeline").build();
        final PipelineDoc pipelineDoc = PipelineDoc.builder().uuid("p1").name("Events Pipeline").build();
        beforeShow.accept(harness);
        harness.afterStartUp(() -> {
            final ProcessorsController processors = injector.getProcessors();
            // As PipelineEditor's 'Processors' tab reads it
            processors.read(pipelineRef, pipelineDoc);
            processors.setIsAdmin(true);
            processors.setAllowUpdate(true);
            harness.show(processors);
        });
        return harness.asWidget();
    }
}
