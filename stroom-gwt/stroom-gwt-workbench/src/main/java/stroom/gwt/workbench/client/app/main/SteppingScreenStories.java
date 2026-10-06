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
import stroom.gwt.workbench.client.app.rest.JsonValues;
import stroom.gwt.workbench.client.app.rest.RecordedRequest;
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
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.presenter.SteppingPresenter;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// Stories matching `App/Main/SteppingScreen` in the React Storybook, showing Stroom's real
/// [SteppingPresenter] (a pipeline's stepper, as `PipelinePresenter.beginStepping` starts it) with
/// a fake stepping server.
///
/// | React | Stroom |
/// |---|---|
/// | `api.step` | `POST /stepping/v1/step` (a handler, see `step`) |
/// | `api.terminate` | `POST /stepping/v1/terminateStepping` |
/// | `api.findElementDoc` | `POST /stepping/v1/findElementDoc` |
/// | `structure` | `POST /pipeline/v1/fetchPipelineLayers`, `GET /pipeline/v1/propertyTypes` |
/// | (the stream) | `POST /data/v1/fetch`, `GET /meta/v1/1` |
///
/// `SteppingWithCode` is blocked (see `react-story-status.json`).
public final class SteppingScreenStories {

    private static final String STEP_PATH = "/stepping/v1/step";

    // The pipeline: Source -> xsltFilter -> xmlWriter (-> recordCount, for the full tree)
    private static final String LAYERS = """
            [{"sourcePipeline": {"type": "Pipeline", "uuid": "p1", "name": "P"},
              "pipelineData": {
                "elements": {"add": [{"id": "xsltFilter", "type": "XSLTFilter"},
                  {"id": "xmlWriter", "type": "XMLWriter"} EXTRA_ELEMENT]},
                "links": {"add": [{"from": "Source", "to": "xsltFilter"}, {"from": "xsltFilter", "to": "xmlWriter"}
                  EXTRA_LINK]}}}]""";

    private static final String STEP_LAYERS = LAYERS.replace("EXTRA_ELEMENT", "").replace("EXTRA_LINK", "");
    private static final String FULL_LAYERS = LAYERS
            .replace("EXTRA_ELEMENT", ", {\"id\": \"recordCount\", \"type\": \"XMLWriter\"}")
            .replace("EXTRA_LINK", ", {\"from\": \"xmlWriter\", \"to\": \"recordCount\"}");

    private static final String DATA = """
            {"type": "data", "feedName": "MY_FEED", "streamTypeName": "Raw Events",
              "sourceLocation": {"metaId": 1, "partIndex": 0, "recordIndex": 0,
                "dataRange": {"locationFrom": {"type": "default", "lineNo": 1, "colNo": 1}, "charOffsetFrom": 0,
                  "byteOffsetFrom": 0, "locationTo": {"type": "default", "lineNo": 1, "colNo": 5},
                  "charOffsetTo": 4, "byteOffsetTo": 4, "length": 5},
                "highlights": []},
              "itemRange": {"offset": 0, "length": 1}, "totalItemCount": {"count": 5, "exact": true},
              "totalCharacterCount": {"count": 5, "exact": true}, "totalBytes": 5,
              "availableChildStreamTypes": [null], "data": "<in/>", "html": false,
              "dataType": "NON_SEGMENTED", "displayMode": "TEXT"}""";

    private static final String STEP_RESULT = """
            {"sessionUuid": "sess-1", "complete": true, "foundRecord": true,
              "foundLocation": {"metaId": 1, "partIndex": 0, "recordIndex": RECORD},
              "stepData": {"sourceLocation": {"metaId": 1, "partIndex": 0, "recordIndex": RECORD},
                "elementMap": {
                  "xsltFilter": {"input": "<in record=\\"RECORD\\"/>", "output": "<out record=\\"RECORD\\"/>"},
                  "xmlWriter": {"input": "<out record=\\"RECORD\\"/>", "output": "record RECORD",
                    "indicators": {"errorCount": {"ERROR": 1, "WARNING": 1}, "errorList": [
                      {"type": "storedError", "severity": "ERROR", "errorType": "OUTPUT",
                        "message": "Invalid character", "location": {"type": "default", "lineNo": 3, "colNo": 7}},
                      {"type": "storedError", "severity": "WARNING", "errorType": "OUTPUT",
                        "message": "Deprecated element", "location": {"type": "default", "lineNo": 1, "colNo": 1}}
                    ]}}}}}""";

    private SteppingScreenStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("App/Main/SteppingScreen", SteppingScreenStories.class)
                .layout(StoryLayout.FULLSCREEN)
                // Stepping through records: the location label and the buttons follow the result
                .story("Stepping", context -> render(context, STEP_LAYERS))
                .withPlay(play -> {
                    // beginStepping refreshes on entry: the label settles at [1:1:1] (record 0)
                    play.waitFor(() -> play.expect(play.getAllByText("[1:1:1]").count()).toBeGreaterThan(0));
                    play.waitFor(() -> play.expect(play.getByTitle("Step Forward")).not().toHaveClass("disabled"));
                    // Differs from React: StepControlPresenter only knows a record is the first of the
                    // first stream of the stepper's stream list, which a stepper begun on one stream (as
                    // here) hasn't loaded, so the initial REFRESH leaves Back enabled
                    play.waitFor(() -> play.expect(play.getByTitle("Step Backward")).not().toHaveClass("disabled"));
                    // Step Forward twice -> [1:1:3]; Back is now enabled
                    play.click(play.getByTitle("Step Forward"));
                    play.findAllByText("[1:1:2]");
                    play.click(play.getByTitle("Step Forward"));
                    play.findAllByText("[1:1:3]");
                    play.waitFor(() -> play.expect(play.getByTitle("Step Backward")).not().toHaveClass("disabled"));
                    // Step To Last -> [1:1:5]
                    play.click(play.getByTitle("Step To Last"));
                    play.findAllByText("[1:1:5]");
                    play.waitFor(() -> play.expect(play.getByTitle("Step To Last")).toHaveClass("disabled"));
                    // Both elements are in the pipeline tree.
                    // Differs from React: GWT's stepper shows the pipeline's element tree, not a
                    // listbox of the elements that returned data
                    play.findByText("xsltFilter");
                    play.findByText("xmlWriter");
                    // Selecting the element with errors shows the log pane toggle with its count.
                    // Differs from React: GWT's log pane has no 'log' role (its markers are in the
                    // element's editors), so the toggle's title, which counts the errors, is checked
                    // rather than the pane being shown and hidden
                    play.click(play.getByText("xmlWriter"));
                    play.findByTitle(TextMatch.containing("1 Error"));
                    // Differs from React: GWT doesn't start a new session for each step; the session
                    // is dropped only when stepping (re)begins on a stream (SteppingPresenter
                    // .beginStepping), and each step polls with the session it has, so the three steps
                    // above were all sent with the server's session
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.expect("the steps sent with the session", () -> stepsWithSession(requests))
                            .toBeGreaterThanOrEqual(3);
                    expectNoProblems(play);
                })
                // With the pipeline structure, the left panel is the element tree with severities
                .story("SteppingWithTree", context -> render(context, STEP_LAYERS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Source");
                    play.findByText("xsltFilter");
                    play.findByText("xmlWriter");
                    // xmlWriter has an error: the box's hover tip and severity class
                    play.waitFor(() -> play.expect(play.getByTitle(TextMatch.containing("xmlWriter' has errors")))
                            .toHaveClass("pipelineElementBox-severityError"));
                    // Selecting it shows its log pane toggle
                    play.click(play.getByText("xmlWriter"));
                    play.findByTitle(TextMatch.containing("Toggle Log Pane (1 Error"));
                    // Clicking the location label opens the Set Location dialog
                    play.click(play.getAllByText("[1:1:1]").nth(0));
                    final Play jump = screen.within(
                            screen.findByText("Set Location", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(jump.getByRole("button", StroomDom.button("Cancel")));
                    expectNoProblems(play);
                })
                // Change Step Filters lists the whole pipeline, not just the elements with data
                .story("SteppingFilterFullTree", context -> render(context, FULL_LAYERS))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getAllByText("[1:1:1]").count()).toBeGreaterThan(0));
                    play.click(play.getByTitle("Manage Step Filters"));
                    final Play dialog = screen.within(
                            screen.findByText("Change Step Filters", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    // Differs from React: GWT's element list is the dialog's element chooser table
                    final Play list = dialog.within(dialog.querySelector(".pipelineElementChooser"));
                    // recordCount returned no step data, yet is listed
                    play.expect(list.getByText("recordCount")).toBeInTheDocument();
                    play.expect(list.getByText("xsltFilter")).toBeInTheDocument();
                    expectNoProblems(play);
                });
    }

    // The number of step requests sent with the server's session
    private static int stepsWithSession(final Spy requests) {
        int count = 0;
        for (final List<Object> call : requests.getCalls()) {
            final RecordedRequest request = RecordedRequest.parse(String.valueOf(call.get(0)));
            if ("POST".equals(request.getMethod()) && STEP_PATH.equals(request.getPath())
                && "sess-1".equals(((Map<?, ?>) JsonValues.parse(request.getBody())).get("sessionUuid"))) {
                count++;
            }
        }
        return count;
    }

    // The mock stepping server: the first poll of a step (no session) is incomplete; then FIRST ->
    // record 0, FORWARD -> +1, BACKWARD -> -1, LAST -> 4, REFRESH -> the same record
    private static RestReply step(final String body) {
        final Map<?, ?> request = (Map<?, ?>) JsonValues.parse(body);
        final Map<?, ?> location = (Map<?, ?>) request.get("stepLocation");
        final int current = location == null || location.get("recordIndex") == null
                ? 0
                : Math.max(0, Integer.parseInt(String.valueOf(location.get("recordIndex"))));
        if (request.get("sessionUuid") == null) {
            return RestReply.json("{\"sessionUuid\": \"sess-1\", \"complete\": false, "
                                  + "\"progressLocation\": {\"metaId\": 1, \"partIndex\": 0, \"recordIndex\": "
                                  + current + "}}");
        }
        final String stepType = String.valueOf(request.get("stepType"));
        final int record;
        if ("FIRST".equals(stepType)) {
            record = 0;
        } else if ("FORWARD".equals(stepType)) {
            record = current + 1;
        } else if ("BACKWARD".equals(stepType)) {
            record = Math.max(0, current - 1);
        } else if ("LAST".equals(stepType)) {
            record = 4;
        } else {
            record = current;
        }
        return RestReply.json(STEP_RESULT.replace("RECORD", String.valueOf(record)));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    private static RestFixtures fixtures(final String layers) {
        return RestFixtures.builder()
                .post(STEP_PATH, request -> step(request.getBody()))
                .post("/stepping/v1/terminateStepping", RestReply.json("true"))
                .post("/stepping/v1/findElementDoc", RestReply.json("null"))
                .get(PipelineFixtures.PROPERTY_TYPES_PATH, RestReply.json(PipelineFixtures.PROPERTY_TYPES))
                .post("/pipeline/v1/fetchPipelineLayers", RestReply.json(layers))
                .post("/data/v1/fetch", RestReply.json(DATA))
                .get("/data/v1/1/parts/0/child-types", RestReply.json("[null]"))
                .get("/meta/v1/1", RestReply.json("{\"id\": 1, \"feedName\": \"MY_FEED\", "
                        + "\"typeName\": \"Raw Events\", \"status\": \"UNLOCKED\", \"createMs\": 1700000000000}"))
                .build();
    }

    private static Widget render(final StoryContext context, final String layers) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures(layers))
                .injector(injector)
                .build();
        final DocRef pipeline = DocRef.builder().type(PipelineDoc.TYPE).uuid("p1").name("Test Pipeline").build();
        final PipelineDoc pipelineDoc = PipelineDoc.builder().uuid("p1").name("Test Pipeline").build();
        final Meta meta = Meta.builder().id(1L).feedName("MY_FEED").typeName("Raw Events").build();
        harness.afterStartUp(() -> {
            final SteppingPresenter stepping = injector.getSteppingPresenter();
            harness.addContent(stepping);
            // As PipelinePresenter loads the model and begins stepping a stream
            injector.getPipelineElementTypesFactory().get(stepping, types ->
                    injector.getPipelineModelFactory().get(stepping, pipeline, types, model -> {
                        stepping.setPipelineModel(model);
                        stepping.setPipelineDoc(pipelineDoc);
                        stepping.beginStepping(StepType.REFRESH, new StepLocation(1L, 0L, 0L), meta, null);
                    }));
        });
        return harness.asWidget();
    }
}
