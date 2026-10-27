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
import stroom.gwt.workbench.client.app.rest.RequestMatcher;
import stroom.gwt.workbench.client.app.rest.RestFixtures;
import stroom.gwt.workbench.client.app.rest.RestReply;
import stroom.gwt.workbench.client.app.screen.ScreenHarness;
import stroom.gwt.workbench.client.app.screen.StoryDocumentPlugins;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.meta.shared.Meta;
import stroom.pipeline.shared.PipelineDoc;
import stroom.pipeline.shared.stepping.StepLocation;
import stroom.pipeline.shared.stepping.StepType;
import stroom.pipeline.stepping.client.component.SteppingScreenController;
import stroom.security.shared.DocumentPermission;
import stroom.task.client.DefaultTaskMonitorFactory;

import com.google.gwt.core.client.GWT;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;
import java.util.Map;

/// Stories of `App/Main/SteppingScreen`, showing Stroom's real [SteppingScreenController] (a pipeline's
/// stepper, as `PipelinePresenter.beginStepping` starts it) with a fake stepping server.
///
/// | Stroom endpoint | Used for |
/// |---|---|
/// | `POST /stepping/v1/step` (a handler, see `step`) | stepping |
/// | `POST /stepping/v1/terminateStepping` | terminating stepping |
/// | `POST /stepping/v1/findElementDoc` | an element's document |
/// | `GET`, `PUT /xslt/v1/x1` (through Stroom's `XsltPlugin`) | loading and saving an element's code |
/// | `POST /pipeline/v1/fetchPipelineLayers`, `GET /pipeline/v1/propertyTypes` | the pipeline's structure |
/// | `POST /data/v1/fetch`, `GET /meta/v1/1` | the stream |
///
/// `SteppingWithCode` registers Stroom's XSLT document plugin ([StoryDocumentPlugins]), through which
/// the element's code is loaded, as the app's plugins register themselves as it starts.
public final class SteppingScreenStories {

    private static final String STEP_PATH = "/stepping/v1/step";

    // SteppingWithCode: the xsltFilter's code is in the XSLT 'My XSLT'
    private static final String XSLT_PATH = "/xslt/v1/x1";
    private static final String XSLT_REF = """
            {"type": "XSLT", "uuid": "x1", "name": "My XSLT"}""";
    private static final String XSLT = """
            {"type": "XSLT", "uuid": "x1", "name": "My XSLT", "data": "ORIGINAL XSLT CODE"}""";

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

    // SteppingBranches: Source -> xsltFilter -> (xmlWriter -> appenderA, jsonWriter,
    // textWriter -> (appenderT1, appenderT2))
    private static final String BRANCH_LAYERS = """
            [{"sourcePipeline": {"type": "Pipeline", "uuid": "p1", "name": "P"},
              "pipelineData": {
                "elements": {"add": [{"id": "xsltFilter", "type": "XSLTFilter"},
                  {"id": "xmlWriter", "type": "XMLWriter"}, {"id": "jsonWriter", "type": "XMLWriter"},
                  {"id": "textWriter", "type": "XMLWriter"}, {"id": "appenderA", "type": "StreamAppender"},
                  {"id": "appenderT1", "type": "StreamAppender"}, {"id": "appenderT2", "type": "StreamAppender"}]},
                "links": {"add": [{"from": "Source", "to": "xsltFilter"},
                  {"from": "xsltFilter", "to": "xmlWriter"}, {"from": "xsltFilter", "to": "jsonWriter"},
                  {"from": "xsltFilter", "to": "textWriter"}, {"from": "xmlWriter", "to": "appenderA"},
                  {"from": "textWriter", "to": "appenderT1"}, {"from": "textWriter", "to": "appenderT2"}]}}}]""";

    // SteppingLongPipeline: Source -> f01 -> ... -> f30 -> xmlWriter, too long for the bar
    private static final int LONG_FILTER_COUNT = 30;

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

    // SteppingBranches: jsonWriter has a warning and appenderT2 an error
    private static final String BRANCH_STEP_RESULT = """
            {"sessionUuid": "sess-1", "complete": true, "foundRecord": true,
              "foundLocation": {"metaId": 1, "partIndex": 0, "recordIndex": RECORD},
              "stepData": {"sourceLocation": {"metaId": 1, "partIndex": 0, "recordIndex": RECORD},
                "elementMap": {
                  "xsltFilter": {"input": "<in/>", "output": "<out/>"},
                  "jsonWriter": {"input": "<out/>", "output": "{}",
                    "indicators": {"errorCount": {"WARNING": 1}, "errorList": [
                      {"type": "storedError", "severity": "WARNING", "errorType": "OUTPUT",
                        "message": "Deprecated element", "location": {"type": "default", "lineNo": 1, "colNo": 1}}
                    ]}},
                  "appenderT2": {"input": "<out/>", "output": "",
                    "indicators": {"errorCount": {"ERROR": 1}, "errorList": [
                      {"type": "storedError", "severity": "ERROR", "errorType": "OUTPUT",
                        "message": "Cannot append", "location": {"type": "default", "lineNo": 1, "colNo": 1}}
                    ]}}}}}""";

    // SteppingProblems: xsltFilter has a warning, appenderT2 an error, and jsonWriter (in a branch
    // not shown) a warning
    private static final String PROBLEMS_STEP_RESULT = BRANCH_STEP_RESULT.replace("""
                  "xsltFilter": {"input": "<in/>", "output": "<out/>"},""", """
                  "xsltFilter": {"input": "<in/>", "output": "<out/>",
                    "indicators": {"errorCount": {"WARNING": 1}, "errorList": [
                      {"type": "storedError", "severity": "WARNING", "errorType": "OUTPUT",
                        "message": "Deprecated function", "location": {"type": "default", "lineNo": 1, "colNo": 1}}
                    ]}},""");

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
                .story("Stepping", context -> render(context, STEP_LAYERS, null))
                .withPlay(play -> {
                    // beginStepping refreshes on entry: the label settles at [1:1:1] (record 0)
                    play.waitFor(() -> play.expect(play.getAllByText("[1:1:1]").count()).toBeGreaterThan(0));
                    play.waitFor(() -> play.expect(play.getByTitle("Step Forward"))
                            .not().toHaveAttribute("aria-disabled"));
                    // StepControlController only knows a record is the first of the first stream of the
                    // stepper's stream list, which a stepper begun on one stream (as here) hasn't
                    // loaded, so the initial REFRESH leaves Back enabled
                    play.waitFor(() -> play.expect(play.getByTitle("Step Backward"))
                            .not().toHaveAttribute("aria-disabled"));
                    // Step Forward twice -> [1:1:3]; Back is now enabled
                    play.click(play.getByTitle("Step Forward"));
                    play.findAllByText("[1:1:2]");
                    // Screen readers are told where the step got to, in words
                    play.waitFor(() -> play.expect(play.querySelector(".stepping-screen__progress [role='status']"))
                            .toHaveTextContent("Stream 1, part 1, record 2"));
                    play.click(play.getByTitle("Step Forward"));
                    play.findAllByText("[1:1:3]");
                    play.waitFor(() -> play.expect(play.getByTitle("Step Backward"))
                            .not().toHaveAttribute("aria-disabled"));
                    // Step To Last -> [1:1:5]
                    play.click(play.getByTitle("Step To Last"));
                    play.findAllByText("[1:1:5]");
                    play.waitFor(() -> play.expect(play.getByTitle("Step To Last"))
                            .toHaveAttribute("aria-disabled", "true"));
                    // Both elements are in the pipeline tree.
                    // The stepper shows the pipeline's element tree
                    play.findByText("xsltFilter");
                    play.findByText("xmlWriter");
                    // Selecting the element with errors shows the log pane toggle with its count.
                    // The log pane has no 'log' role (its markers are in the element's editors), so the
                    // toggle's title, which counts the errors, is checked rather than the pane being
                    // shown and hidden
                    play.click(play.getByText("xmlWriter"));
                    play.findByTitle(TextMatch.containing("1 Error"));
                    // GWT doesn't start a new session for each step; the session is dropped only when
                    // stepping (re)begins on a stream (SteppingScreenController.beginStepping), and each step
                    // polls with the session it has, so the three steps above were all sent with the
                    // server's session
                    final Spy requests = play.spy(ScreenHarness.REQUEST_SPY);
                    play.expect("the steps sent with the session", () -> stepsWithSession(requests))
                            .toBeGreaterThanOrEqual(3);
                    expectNoProblems(play);
                })
                // The pipeline is a bar of chevrons, coloured by the elements' severities
                .story("SteppingWithTree", context -> render(context, STEP_LAYERS, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.findByText("Source");
                    play.findByText("xsltFilter");
                    play.findByText("xmlWriter");
                    // xmlWriter has an error: the chevron's name says so
                    play.findByRole("button", TextMatch.startingWith("xmlWriter, XML Writer, has errors"));
                    // The pipeline's pane fits its chevrons, rather than cutting off their bottoms
                    final Value<Double> boxBottom = play.getByRole("button", TextMatch.startingWith("xmlWriter"))
                            .bottom();
                    final Value<Double> paneBottom = play.querySelector(".stepping-screen__pipeline").bottom();
                    play.waitFor(() -> play.expect("the box's bottom below its pane's",
                            () -> boxBottom.get() - paneBottom.get()).toBeLessThanOrEqual(0));
                    // Selecting it shows its log pane toggle
                    play.click(play.getByText("xmlWriter"));
                    play.findByTitle(TextMatch.containing("Log Pane (1 Error"));
                    // Its output's warning (line 1) is in the editor's gutter and overview bar; its
                    // error (line 3) is past the one line of output, so only the overview's summary,
                    // which shows the worst severity, has it
                    play.waitFor(() -> play.expect(play.querySelector(".ace_gutter-cell.stroom_warning"))
                            .toHaveTextContent("1"));
                    play.expect(play.querySelector(".code-editor__marker--warning"))
                            .toHaveAttribute("lineno", "1");
                    play.expect(play.querySelector(".code-editor__summary")).toHaveClass("code-editor__summary--error");
                    // The location is a button, named for what it does and where it is, that opens
                    // the Set Location dialog
                    play.click(play.getByRole("button",
                            "Set Location ([1:1:1], stream 1, part 1, record 1)"));
                    final Play jump = screen.within(
                            screen.findByText("Set Location", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    play.click(jump.getByRole("button", StroomDom.button("Cancel")));
                    expectNoProblems(play);
                })
                // Change Step Filters lists the whole pipeline, not just the elements with data
                .story("SteppingFilterFullTree", context -> render(context, FULL_LAYERS, null))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    play.waitFor(() -> play.expect(play.getAllByText("[1:1:1]").count()).toBeGreaterThan(0));
                    play.click(play.getByTitle("Manage Step Filters"));
                    final Play dialog = screen.within(
                            screen.findByText("Change Step Filters", StroomDom.DIALOG_TITLE).closest(StroomDom.DIALOG));
                    // The element list is the dialog's element chooser table
                    final Play list = dialog.within(dialog.querySelector(".stepping-filter-editor__elements"));
                    // recordCount returned no step data, yet is listed
                    play.expect(list.getByText("recordCount")).toBeInTheDocument();
                    play.expect(list.getByText("xsltFilter")).toBeInTheDocument();
                    expectNoProblems(play);
                })
                // A hasCode element loads its document's code into an editable pane
                .story("SteppingWithCode", context -> render(context, STEP_LAYERS, XSLT_REF))
                .withPlay(play -> {
                    play.findByText("xsltFilter");
                    play.click(play.getByText("xsltFilter"));
                    // The code pane loads the element's document with its document plugin
                    // (XsltPlugin) and shows its code.
                    // The code pane is an editor with no 'Code' heading; the play checks that it shows
                    // the code the plugin loaded
                    play.waitFor(() -> play.expect(play.spy(ScreenHarness.REQUEST_SPY)).toHaveBeenCalledWith(
                            RequestMatcher.get(XSLT_PATH).toSpyMatcher()));
                    final Query code = play.findByText("ORIGINAL XSLT CODE");
                    // The code pane has no 'Save code' button; the code is editable, and saved with the
                    // pipeline's Save (see PipelineEditor's MultiDocumentSave), so the play checks that
                    // the code can be edited
                    // Ace's text layer ignores the mouse: click its content to focus the editor
                    play.click(code.closest(".ace_content"));
                    play.keyboard("{Control>}{End}{/Control} EDITED");
                    play.findByText("ORIGINAL XSLT CODE EDITED");
                    expectNoProblems(play);
                })
                // A pipeline with branches: the bar shows one path, with a drop down at each fork
                .story("SteppingBranches", context -> render(context, BRANCH_LAYERS, null, BRANCH_STEP_RESULT))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    // Once stepped, the path follows the branch with the error, to appenderT2
                    play.findByRole("button", TextMatch.startingWith("appenderT2, Stream Appender, has errors"));
                    // The step's new problems are told to screen readers, in pipeline order; the new
                    // error pulses and the warning glows
                    final Query status = play.querySelector(".pipeline-path-bar ~ [role='status']");
                    play.waitFor(() -> play.expect(status)
                            .toHaveTextContent("jsonWriter has warnings. appenderT2 has errors."));
                    play.expect(play.getByRole("button", TextMatch.startingWith("appenderT2"))
                                    .closest(".pipeline-path-bar__segment"))
                            .toHaveClass("pipeline-path-bar__segment--error", "pipeline-path-bar__segment--pulse");
                    // jsonWriter's warning, in a branch not shown, is a warning icon by textWriter's drop
                    // down chevron
                    play.expect(play.querySelector(".pipeline-path-bar__segment[data-element-id='textWriter'] "
                                                   + ".pipeline-path-bar__branch-severity")).toBeInTheDocument();
                    // textWriter's drop down chevron is a menu button of its own (its chevron's button
                    // selects it), whose name says jsonWriter's branch, not shown, has a warning
                    play.expect(play.getByRole("button", TextMatch.startingWith("textWriter")))
                            .not().toHaveAttribute("aria-haspopup");
                    final Query textWriterBranches = play.getByRole("button",
                            TextMatch.regex("^Choose branch, textWriter is 2 of 3.*other branches have warnings"));
                    play.expect(textWriterBranches).toHaveAttribute("aria-haspopup", "menu");
                    play.expect(textWriterBranches).toHaveAttribute("aria-expanded", "false");
                    play.expect(play.queryByRole("button", TextMatch.startingWith("jsonWriter"))).toBeNull();

                    // Following jsonWriter's branch from the drop down selects it
                    play.click(textWriterBranches);
                    play.click(screen.findByText(TextMatch.startingWith("jsonWriter"), StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(play.getByRole("button", TextMatch.startingWith("jsonWriter")))
                            .toHaveAttribute("aria-current", "true"));
                    play.expect(play.queryByRole("button", TextMatch.startingWith("appenderT2"))).toBeNull();
                    // The error is now in a branch not shown
                    play.expect(play.getByRole("button",
                                    TextMatch.regex("^Choose branch, jsonWriter.*other branches have errors")))
                            .toBeInTheDocument();
                    play.waitFor(() -> play.expect(play.getByRole("button", TextMatch.startingWith("jsonWriter")))
                            .toHaveFocus());

                    // Down opens the drop down from the keyboard; Escape closes it, back on the chevron
                    play.keyboard("{ArrowDown}");
                    screen.findByText(TextMatch.startingWith("textWriter"), StroomDom.MENU_ITEM_TEXT);
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.getByRole("button", TextMatch.startingWith("jsonWriter")))
                            .toHaveFocus());

                    // Right moves to its drop down chevron, which Enter opens; the menu says which branch
                    // is the current one (its highlight is only seen). Escape closes it, back on the
                    // drop down chevron, which says it is expanded only while the menu is open
                    play.keyboard("{ArrowRight}");
                    final Query jsonWriterBranches = play.getByRole("button",
                            TextMatch.startingWith("Choose branch, jsonWriter"));
                    play.expect(jsonWriterBranches).toHaveFocus();
                    play.keyboard("{Enter}");
                    screen.findByText(", current branch");
                    // It opens on the current branch, as a menu that chooses one of its items does
                    play.waitFor(() -> play.expect(screen.querySelector("[role='menuitem']:focus"))
                            .toHaveTextContent(TextMatch.startingWith("jsonWriter, current branch")));
                    // The branch with the error (textWriter's, to appenderT2) has the error's icon, and
                    // says to screen readers that it is the branch (not textWriter itself) that has it
                    play.expect(screen.getByText("textWriter", StroomDom.MENU_ITEM_TEXT))
                            .toHaveTextContent(TextMatch.containing(", branch has errors"));
                    play.expect(screen.querySelectorAll(".pipeline-path-bar__menu-severity")).toHaveLength(2);
                    play.expect(play.getByRole("button", TextMatch.startingWith("Choose branch, jsonWriter")))
                            .toHaveAttribute("aria-expanded", "true");
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(play.getByRole("button",
                            TextMatch.startingWith("Choose branch, jsonWriter"))).toHaveFocus());
                    play.expect(play.getByRole("button", TextMatch.startingWith("Choose branch, jsonWriter")))
                            .toHaveAttribute("aria-expanded", "false");

                    // Left (twice, past jsonWriter's button) moves to xsltFilter and Enter selects it;
                    // the path still follows jsonWriter
                    play.keyboard("{ArrowLeft}");
                    play.keyboard("{ArrowLeft}");
                    play.expect(play.getByRole("button", TextMatch.startingWith("xsltFilter"))).toHaveFocus();
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(play.getByRole("button", TextMatch.startingWith("xsltFilter")))
                            .toHaveAttribute("aria-current", "true"));
                    play.expect(play.getByRole("button", TextMatch.startingWith("jsonWriter"))).toBeInTheDocument();

                    // The bar is one tab stop
                    play.expect(play.getByRole("toolbar", "Pipeline")).toBeInTheDocument();
                    play.expect(play.querySelectorAll(".pipeline-path-bar [tabindex='0']")).toHaveLength(1);

                    // Shift+F10 opens the element's context menu
                    play.keyboard("{Shift>}{F10}{/Shift}");
                    screen.findByText("Manage step filters", StroomDom.MENU_ITEM_TEXT);
                    play.keyboard("{Escape}");
                    expectNoProblems(play);
                })
                // Problems, with no play so that they can be looked at: xsltFilter has a warning's
                // border, appenderT2's new error's border pulses three times then stays, and a
                // warning icon by textWriter's drop down chevron shows jsonWriter's warning, in a branch
                // not shown
                .story("SteppingProblems", context -> render(context, BRANCH_LAYERS, null, PROBLEMS_STEP_RESULT))
                // A pipeline too long for the bar: the chevrons away from the selection collapse into …
                .story("SteppingLongPipeline", context -> render(context, longLayers(), null,
                        STEP_RESULT.replace("\"xsltFilter\":", "\"f01\":")))
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query ellipsis = play.findByRole("button", TextMatch.regex("^\\d+ more elements"));
                    play.expect(ellipsis).toHaveAttribute("aria-haspopup", "menu");
                    // The ends are always shown
                    play.expect(play.getByRole("button", TextMatch.startingWith("Source"))).toBeVisible();
                    play.expect(play.getByRole("button", TextMatch.startingWith("xmlWriter"))).toBeVisible();
                    // The bar doesn't overflow its pane
                    final Value<Double> lastRight = play.getByRole("button", TextMatch.startingWith("xmlWriter"))
                            .right();
                    final Value<Double> barRight = play.querySelector(".pipeline-path-bar").right();
                    play.expect("the last chevron's right beyond the bar's",
                            () -> lastRight.get() - barRight.get()).toBeLessThanOrEqual(0);
                    // Choosing a collapsed element from the … list selects and shows it
                    play.click(ellipsis);
                    play.click(screen.findByText("f25", StroomDom.MENU_ITEM_TEXT));
                    play.waitFor(() -> play.expect(play.getByRole("button", TextMatch.startingWith("f25")))
                            .toHaveAttribute("aria-current", "true"));
                    play.expect(play.getByRole("button", TextMatch.startingWith("f25"))).toBeVisible();
                    play.expect(play.getByRole("button", TextMatch.startingWith("Source"))).toBeVisible();
                    expectNoProblems(play);
                });
    }

    // Source -> f01 -> ... -> f30 -> xmlWriter
    private static String longLayers() {
        final StringBuilder elements = new StringBuilder();
        final StringBuilder links = new StringBuilder();
        String previous = "Source";
        for (int i = 1; i <= LONG_FILTER_COUNT; i++) {
            final String id = (i < 10
                    ? "f0"
                    : "f") + i;
            elements.append("{\"id\": \"").append(id).append("\", \"type\": \"XSLTFilter\"}, ");
            links.append("{\"from\": \"").append(previous).append("\", \"to\": \"").append(id).append("\"}, ");
            previous = id;
        }
        elements.append("{\"id\": \"xmlWriter\", \"type\": \"XMLWriter\"}");
        links.append("{\"from\": \"").append(previous).append("\", \"to\": \"xmlWriter\"}");
        return "[{\"sourcePipeline\": {\"type\": \"Pipeline\", \"uuid\": \"p1\", \"name\": \"P\"}, "
               + "\"pipelineData\": {\"elements\": {\"add\": [" + elements + "]}, "
               + "\"links\": {\"add\": [" + links + "]}}}]";
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
    private static RestReply step(final String body, final String stepResult) {
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
        return RestReply.json(stepResult.replace("RECORD", String.valueOf(record)));
    }

    private static void expectNoProblems(final Play play) {
        play.expect(play.spy(ScreenHarness.ALERT_SPY)).not().toHaveBeenCalled();
        play.expect(play.spy(ScreenHarness.UNHANDLED_REQUEST_SPY)).not().toHaveBeenCalled();
    }

    // The stepping server's fixtures; elementDoc is the document findElementDoc finds (the XSLT
    // of an element with code), or null for none
    private static RestFixtures fixtures(final String layers, final String elementDoc, final String stepResult) {
        final RestFixtures.Builder builder = RestFixtures.builder();
        if (elementDoc != null) {
            StoryDocumentPlugins.documentRoutes(builder, XSLT_PATH, XSLT);
        }
        return builder
                .post(STEP_PATH, request -> step(request.getBody(), stepResult))
                .post("/stepping/v1/terminateStepping", RestReply.json("true"))
                .post("/stepping/v1/findElementDoc", RestReply.json(elementDoc == null
                        ? "null"
                        : elementDoc))
                .get(PipelineFixtures.PROPERTY_TYPES_PATH, RestReply.json(PipelineFixtures.PROPERTY_TYPES))
                .post("/pipeline/v1/fetchPipelineLayers", RestReply.json(layers))
                .post("/data/v1/fetch", RestReply.json(DATA))
                .get("/data/v1/1/parts/0/child-types", RestReply.json("[null]"))
                .get("/meta/v1/1", RestReply.json("{\"id\": 1, \"feedName\": \"MY_FEED\", "
                        + "\"typeName\": \"Raw Events\", \"status\": \"UNLOCKED\", \"createMs\": 1700000000000}"))
                .build();
    }

    private static Widget render(final StoryContext context, final String layers, final String elementDoc) {
        return render(context, layers, elementDoc, STEP_RESULT);
    }

    private static Widget render(final StoryContext context,
                                 final String layers,
                                 final String elementDoc,
                                 final String stepResult) {
        final ProcessingScreenGinjector injector = GWT.create(ProcessingScreenGinjector.class);
        final ScreenHarness harness = ScreenHarness.builder(context, fixtures(layers, elementDoc, stepResult))
                .injector(injector)
                .build();
        if (elementDoc != null) {
            // As the app's plugins do as it starts: the XSLT plugin registers itself for its type,
            // so the element's code is loaded (and saved) through it
            harness.getSecurityContext().setDocumentPermission(DocumentPermission.EDIT);
            StoryDocumentPlugins.register(harness, injector.getDocumentPluginEventManager(),
                    injector.getXsltPlugin());
        }
        final DocRef pipeline = DocRef.builder().type(PipelineDoc.TYPE).uuid("p1").name("Test Pipeline").build();
        final PipelineDoc pipelineDoc = PipelineDoc.builder().uuid("p1").name("Test Pipeline").build();
        final Meta meta = Meta.builder().id(1L).feedName("MY_FEED").typeName("Raw Events").build();
        harness.afterStartUp(() -> {
            final SteppingScreenController stepping = injector.getSteppingScreen();
            harness.show(stepping);
            // As PipelinePresenter loads the model and begins stepping a stream
            final DefaultTaskMonitorFactory taskMonitor = new DefaultTaskMonitorFactory(stepping);
            injector.getPipelineElementTypesFactory().get(taskMonitor, types ->
                    injector.getPipelineModelFactory().get(taskMonitor, pipeline, types, model -> {
                        stepping.setPipelineModel(model);
                        stepping.setPipelineDoc(pipelineDoc);
                        stepping.beginStepping(StepType.REFRESH, new StepLocation(1L, 0L, 0L), meta, null);
                    }));
        });
        return harness.asWidget();
    }
}
