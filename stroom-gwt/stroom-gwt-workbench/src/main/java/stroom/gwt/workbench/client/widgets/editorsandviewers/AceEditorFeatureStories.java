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

package stroom.gwt.workbench.client.widgets.editorsandviewers;

import stroom.editor.client.component.CodeEditorController;
import stroom.editor.client.view.IndicatorLines;
import stroom.editor.client.view.Marker;
import stroom.gwt.workbench.client.app.screen.StroomDom;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.util.shared.TextRange;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.EventBus;
import com.google.web.bindery.event.shared.SimpleEventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;
import edu.ycp.cs.dh.acegwt.client.ace.AceMarkerType;
import edu.ycp.cs.dh.acegwt.client.ace.AceRange;

import java.util.List;

/// Stories for the Ace editor features Stroom uses that [AceEditorStories] doesn't show: folding,
/// the search box, autocompletion, snippets, every annotation type (in the gutter and the
/// overview bar), stepping's and the query editor's markers, the context menu, formatting, Vim
/// keys and Stroom's own syntax modes. They show what an upgrade of Ace must keep working, for a
/// screenshot diff and the plays to compare before and after.
public final class AceEditorFeatureStories {

    private static final String WIDTH = "720px";
    // The tick of a check item that is on
    private static final String TICK = ".svg-image__tick";
    private static final String HEIGHT = "300px";

    private static final String SAMPLE_QUERY = """
            // Logins by hour for the last day
            from "Example Index"
            where EventTime > now() - 1d
            and UserId = "admin"
            eval hour = floorHour(EventTime)
            select hour, count() as Events
            sort by hour desc
            limit 100""";

    private static final String SAMPLE_EXPRESSION = """
            concat(upperCase(${Feed}), ' - ',
                formatDate(${EventTime}, 'yyyy-MM-dd', '+0000'),
                if(greaterThan(${Size}, 1000), ' (large)', ''))""";

    private static final String SAMPLE_DATA_SPLITTER = """
            <?xml version="1.1" encoding="UTF-8"?>
            <dataSplitter xmlns="data-splitter:3" version="3.0">
              <!-- Each line is a record of comma separated values -->
              <split delimiter="\\n">
                <group value="$1">
                  <split delimiter=",">
                    <var id="field" />
                  </split>
                  <data name="first" value="$field$1" />
                </group>
              </split>
            </dataSplitter>""";

    private static final String SAMPLE_HEX_DUMP = """
            00000000  3c 3f 78 6d 6c 20 76 65  72 73 69 6f 6e 3d 22 31  |<?xml version="1|
            00000010  2e 30 22 20 65 6e 63 6f  64 69 6e 67 3d 22 55 54  |.0" encoding="UT|
            00000020  46 2d 38 22 3f 3e 0a 3c  45 76 65 6e 74 73 3e 0a  |F-8"?>.<Events>.|""";

    private static final String SAMPLE_FRAGMENT = """
            <?xml version="1.1" encoding="UTF-8"?>
            <!DOCTYPE Records [
            <!ENTITY fragment SYSTEM "fragment">]>
            <Records
                xmlns="records:2"
                xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance"
                xsi:schemaLocation="records:2 file://records-v2.0.xsd"
                version="2.0">
            &fragment;
            </Records>""";

    private static final String SAMPLE_STEPPER_LOG = """
            2023-11-14T22:13:00.000Z ERROR xmlWriter > "Unexpected element" at 6:9
            2023-11-14T22:13:00.125Z WARN xsltFilter > 'Deprecated system name' at 4:7
            2023-11-14T22:13:00.250Z INFO dsParser > 3 records read in 1.5 seconds""";

    private static final String SAMPLE_WORDS = """
            Stroom streams events from feeds through pipelines.
            Streams are stored by feed.
            """;

    private AceEditorFeatureStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/AceEditorFeatures", AceEditorFeatureStories.class)
                .layout(StoryLayout.CENTERED)
                // Folding: the gutter's fold widget folds an element, leaving a fold marker
                .story("Folding", context -> framed(editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML,
                        true)))
                .withPlay(play -> {
                    final Query widget = play.querySelector(".ace_fold-widget");
                    play.waitFor(() -> play.expect(widget).toBeInTheDocument());
                    play.click(widget);
                    play.waitFor(() -> play.expect(play.querySelector(".ace_fold")).toBeInTheDocument());
                })
                // The search box (Ctrl+F), searching for a word
                .story("SearchBox", context -> framed(editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML,
                        false)))
                .withPlay(play -> {
                    focusEditor(play);
                    play.keyboard("{Control>}f{/Control}");
                    final Query field = play.querySelector(".ace_search_field");
                    play.waitFor(() -> play.expect(field).toBeVisible());
                    play.keyboard("Generator");
                    play.waitFor(() -> play.expect(field).toHaveValue("Generator"));
                })
                // Autocompletion (Ctrl+Space): the start of a word is offered the document's words
                .story("Autocomplete", context -> {
                    final CodeEditorController editor = editor(SAMPLE_WORDS, AceEditorMode.TEXT, false);
                    editor.getOptions().getBasicAutoCompletionOption().setAvailable(true);
                    editor.getOptions().getBasicAutoCompletionOption().setOn(true);
                    return framed(editor);
                })
                .withPlay(play -> {
                    focusEditor(play);
                    play.keyboard("{Control>}{End}{/Control}Str{Control>} {/Control}");
                    // Ace adds the popup to the page's body, outside the story
                    final Query popup = play.screen().querySelector(".ace_autocomplete");
                    play.waitFor(() -> play.expect(popup).toBeVisible());
                })
                // A snippet: Stroom's XSLT "apply" snippet, typed then expanded with Tab
                .story("Snippet", context -> {
                    final CodeEditorController editor = editor("", AceEditorMode.XML, false);
                    editor.getOptions().getSnippetsOption().setAvailable(true);
                    editor.getOptions().getSnippetsOption().setOn(true);
                    return framed(editor);
                })
                .withPlay(play -> {
                    focusEditor(play);
                    play.keyboard("apply{Tab}");
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toMatch("xsl:apply-templates"));
                })
                // Every severity Stroom shows in the gutter, including a fatal error
                .story("AllAnnotationTypes", context -> {
                    final CodeEditorController editor = editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML, true);
                    editor.getOptions().getIndicatorsOption().setAvailable(true);
                    editor.getOptions().getIndicatorsOption().setOn(true);
                    editor.setIndicators(allSeverities());
                    return framed(editor);
                })
                .withPlay(play -> {
                    // Each line's gutter cell has Stroom's class for its severity, so editor.css
                    // draws its icon there (lines 2, 4, 6 and 9)
                    for (final String type : List.of("stroom_info", "stroom_warning", "stroom_error",
                            "stroom_fatal_error")) {
                        play.waitFor(() -> play.expect(play.querySelector(".ace_gutter-cell." + type))
                                .toBeInTheDocument());
                    }
                    final Value<List<String>> cells = play.querySelectorAll(
                            ".ace_gutter-cell[class*='stroom_']").textContents();
                    play.waitFor(() -> play.expect("the annotated lines", () -> String.join(",", cells.get()))
                            .toBe("2,4,6,9"));
                    // Ace only labels its own annotation classes, so AceEditor labels Stroom's
                    play.waitFor(() -> play.expect(play.querySelector(
                                    ".ace_gutter-cell.stroom_fatal_error .ace_gutter_annotation"))
                            .toHaveAttribute("aria-label", "Fatal error, read annotations row 9"));
                    // Ace's tooltip, on hovering a marker, is checked with the real mouse
                    // (test-runner/lib/real-input/ace-editor.mjs)
                    // The overview bar (RightBar) has a marker for each line, and its summary shows
                    // the worst severity
                    play.waitFor(() -> play.expect(play.querySelectorAll(".code-editor__marker").count())
                            .toBe(4));
                    play.expect(play.querySelector(".code-editor__summary")).toHaveClass("code-editor__summary--fatal");
                    final Query fatalMarker = play.querySelector(".code-editor__marker--fatal");
                    play.expect(fatalMarker).toHaveAttribute("lineno", "9");
                    // Hovering a marker in the overview bar says where it is
                    play.hover(fatalMarker);
                    play.waitFor(() -> play.expect(play.screen().querySelector(".code-editor-indicator[role=tooltip]"))
                            .toHaveTextContent(TextMatch.containing("at line 9, click to navigate.")));
                })
                // Stepping's and the data viewer's highlight of the current record (a
                // "code-editor__highlight" marker)
                .story("Highlight", context -> {
                    final CodeEditorController editor = editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML, true);
                    editor.setHighlights(List.of(TextRange.of(DefaultLocation.of(5, 5),
                            DefaultLocation.of(8, 19))));
                    return framed(editor);
                })
                .withPlay(play -> play.waitFor(() -> play.expect(
                                play.querySelector(".ace_marker-layer .code-editor__highlight"))
                        .toBeInTheDocument()))
                // The query editor's error: a "code-editor__error-underline" marker under the bad token,
                // and its indicator
                .story("QueryErrorMarker", context -> {
                    final CodeEditorController editor = editor(SAMPLE_QUERY, AceEditorMode.STROOM_QUERY, false);
                    editor.getOptions().getIndicatorsOption().setAvailable(true);
                    editor.getOptions().getIndicatorsOption().setOn(true);
                    final Indicators indicators = new Indicators();
                    indicators.add(new StoredError(Severity.ERROR, DefaultLocation.of(5, 12), null,
                            "Unknown function 'floorHour'"));
                    editor.setIndicators(new IndicatorLines(indicators));
                    editor.setMarkers(List.of(new Marker(AceRange.create(4, 12, 4, 21), "code-editor__error-underline",
                            AceMarkerType.TEXT, true)));
                    return framed(editor);
                })
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(
                                    play.querySelector(".ace_marker-layer .code-editor__error-underline"))
                            .toBeInTheDocument());
                    play.waitFor(() -> play.expect(play.querySelector(".ace_gutter-cell.stroom_error"))
                            .toHaveTextContent("5"));
                })
                // The editor's context menu (a secondary mouse down): the edit items, Format, and the
                // editor's options in a sub menu, as check items, ticked while on (with no '(ON)' or
                // '(OFF)' in their text); turning the line numbers off hides the gutter and unticks them
                .story("ContextMenu", context -> {
                    final StoryPopups popups = StoryPopups.create(context).withMenus();
                    final CodeEditorController editor = editor(popups.getEventBus(), AceEditorStories.SAMPLE_XML,
                            AceEditorMode.XML, false);
                    editor.getFormatAction().setAvailable(true);
                    return framed(editor);
                })
                .withPlay(play -> {
                    final Play screen = play.screen();
                    final Query gutter = play.querySelector(".ace_gutter");
                    play.waitFor(() -> play.expect(gutter).toBeVisible());
                    play.rightClick(play.querySelector(".ace_content"));
                    final Value<List<String>> items = screen.querySelectorAll(StroomDom.MENU_ITEM_TEXT)
                            .textContents();
                    play.waitFor(() -> play.expect("the menu's items", () -> String.join(", ", items.get()))
                            .toBe("Undo, Redo, Cut, Copy, Paste, Delete, Select All, Format, Editor Options"));
                    play.click(screen.getByRole("menuitem", TextMatch.containing("Editor Options")));
                    play.waitFor(() -> play.expect("the menus' items", () -> String.join(", ", items.get()))
                            .toMatch("Editor Options, Syntax Highlighting, Line Numbers, Wrap Lines, "
                                     + "Show Indent Guides, Show Hidden Characters, "
                                     + "Highlight Active Line, Vim Key Bindings, "
                                     + "Basic Auto Completion, Snippets, "
                                     + "Live Auto Completion"));
                    // An option that is on is a ticked check item, one that is off an unticked one
                    final Query lineNumbers = screen.getByRole("menuitemcheckbox", "Line Numbers");
                    play.expect(lineNumbers).toHaveAttribute("aria-checked", "true");
                    play.expect(play.within(lineNumbers).querySelector(TICK)).toBeInTheDocument();
                    final Query wrapLines = screen.getByRole("menuitemcheckbox", "Wrap Lines");
                    play.expect(wrapLines).toHaveAttribute("aria-checked", "false");
                    play.expect(play.within(wrapLines).querySelector(TICK)).toBeNull();
                    // An action is a plain item
                    play.expect(screen.getByRole("menuitem", "Format")).not().toHaveAttribute("aria-checked");
                    play.expect(screen.getAllByRole("menuitemcheckbox")).toHaveLength(10);
                    play.click(lineNumbers);
                    play.waitFor(() -> play.expect(gutter).not().toBeVisible());
                    play.rightClick(play.querySelector(".ace_content"));
                    play.click(screen.findByRole("menuitem", TextMatch.containing("Editor Options")));
                    play.waitFor(() -> play.expect(screen.getByRole("menuitemcheckbox", "Line Numbers"))
                            .toHaveAttribute("aria-checked", "false"));
                })
                // Formatting JSON (js-beautify, as the editor isn't XML), from the context menu
                .story("FormatJson", context -> {
                    final StoryPopups popups = StoryPopups.create(context).withMenus();
                    final CodeEditorController editor = editor(popups.getEventBus(),
                            "{\"feed\":\"TEST-EVENTS\",\"streams\":[1,2]}", AceEditorMode.JSON, false);
                    editor.getFormatAction().setAvailable(true);
                    return framed(editor);
                })
                .withPlay(play -> {
                    play.rightClick(play.querySelector(".ace_content"));
                    play.click(play.screen().findByText("Format", StroomDom.MENU_ITEM_TEXT));
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toBe("{\n  \"feed\": \"TEST-EVENTS\",\n  \"streams\": [1, 2]\n}"));
                })
                // Vim keys: in normal mode, x deletes the character under the cursor
                .story("VimKeys", context -> {
                    final CodeEditorController editor = editor("first line\nsecond line", AceEditorMode.TEXT, false);
                    editor.getOptions().getUseVimBindingsOption().setAvailable(true);
                    editor.getOptions().getUseVimBindingsOption().setOn(true);
                    return framed(editor);
                })
                .withPlay(play -> {
                    focusEditor(play);
                    // Vim's keys load on demand; the editor is in normal mode once they have
                    final Query normalMode = play.querySelector(".ace_editor.normal-mode");
                    play.waitFor(() -> play.expect(normalMode).toBeInTheDocument());
                    play.keyboard("{Control>}{Home}{/Control}x");
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the first line", () -> lines.get().get(0))
                            .toBe("irst line"));
                    // Escape leaves insert mode first, and then, from normal mode, stops editing, so
                    // Tab can leave the editor
                    play.keyboard("i");
                    play.waitFor(() -> play.expect(normalMode).not().toBeInTheDocument());
                    play.keyboard("{Escape}");
                    play.waitFor(() -> play.expect(normalMode).toBeInTheDocument());
                    play.expect(play.querySelector(".ace_text-input")).toHaveFocus();
                    play.keyboard("{Escape}");
                    play.expect(play.querySelector(".ace_scroller")).toHaveFocus();
                })
                // Stroom's own syntax modes
                .story("StroomQueryMode", context -> framed(editor(SAMPLE_QUERY,
                        AceEditorMode.STROOM_QUERY, true)))
                .withPlay(play -> {
                    // The mode's tokens: the comment, keywords such as 'from', the data source and a string
                    play.waitFor(() -> play.expect(play.querySelector(".ace_comment"))
                            .toHaveTextContent("// Logins by hour for the last day"));
                    final Value<List<String>> keywords = play.querySelectorAll(".ace_keyword").textContents();
                    play.waitFor(() -> play.expect("the keywords", () -> String.join(",", keywords.get()))
                            .toMatch("from"));
                    play.expect(play.querySelector(".ace_support.ace_type")).toHaveTextContent("\"Example Index\"");
                    play.expect(play.querySelector(".ace_string")).toHaveTextContent("\"admin\"");
                })
                .story("StroomExpressionMode", context -> framed(editor(SAMPLE_EXPRESSION,
                        AceEditorMode.STROOM_EXPRESSION, true)))
                .story("StroomDataSplitterMode", context -> framed(editor(SAMPLE_DATA_SPLITTER,
                        AceEditorMode.STROOM_DATA_SPLITTER, true)))
                .story("StroomHexDumpMode", context -> framed(editor(SAMPLE_HEX_DUMP,
                        AceEditorMode.STROOM_HEX_DUMP, true)))
                // A text converter's XML fragment, and its fragment and combined parsers
                .story("StroomFragmentParserMode", context -> framed(editor(SAMPLE_FRAGMENT,
                        AceEditorMode.STROOM_FRAGMENT_PARSER, true)))
                .story("StroomCombinedParserMode", context -> framed(editor(SAMPLE_DATA_SPLITTER,
                        AceEditorMode.STROOM_COMBINED_PARSER, true)))
                // Stepping's log pane
                .story("StroomStepperMode", context -> framed(editor(SAMPLE_STEPPER_LOG,
                        AceEditorMode.STROOM_STEPPER, true)));
    }

    /// Focuses Ace's hidden text area, so that keys go to the editor.
    private static void focusEditor(final Play play) {
        final Query input = play.querySelector(".ace_text-input");
        play.waitFor(() -> play.expect(input).toBeInTheDocument());
        play.run("focus the editor", () -> input.element().get().focus());
    }

    /// The editor, with the given text, mode and read only state, and line numbers.
    static CodeEditorController editor(final String text, final AceEditorMode mode, final boolean readOnly) {
        return editor(new SimpleEventBus(), text, mode, readOnly);
    }

    /// The editor, on the given event bus (which shows its context menu), with the given text, mode
    /// and read only state, and line numbers.
    private static CodeEditorController editor(final EventBus eventBus,
                                          final String text,
                                          final AceEditorMode mode,
                                          final boolean readOnly) {
        final CodeEditorController editor = EditorWidgets.codeEditor(eventBus);
        editor.setMode(mode);
        editor.setReadOnly(readOnly);
        editor.getOptions().getLineNumbersOption().setOn(true);
        editor.setText(text);
        return editor;
    }

    static Widget framed(final CodeEditorController editor) {
        return EditorWidgets.frame(editor.asWidget(), WIDTH, HEIGHT);
    }

    /// One indicator of each severity (lines are one based).
    private static IndicatorLines allSeverities() {
        final Indicators indicators = new Indicators();
        indicators.add(new StoredError(Severity.INFO, DefaultLocation.of(2, 5), null,
                "Consider adding a schema location"));
        indicators.add(new StoredError(Severity.WARNING, DefaultLocation.of(4, 7), null,
                "Deprecated system name"));
        indicators.add(new StoredError(Severity.ERROR, DefaultLocation.of(6, 9), null,
                "Unexpected element"));
        indicators.add(new StoredError(Severity.FATAL_ERROR, DefaultLocation.of(9, 5), null,
                "Document is not well formed"));
        return new IndicatorLines(indicators);
    }
}
