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

import stroom.editor.client.presenter.EditorPresenter;
import stroom.editor.client.view.IndicatorLines;
import stroom.gwt.workbench.framework.client.play.Play;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;

/// Stories for the Ace editor features Stroom uses that [AceEditorStories] doesn't show: folding,
/// the search box, autocompletion, snippets, every annotation type, Vim keys and Stroom's own
/// syntax modes. They show what an upgrade of Ace must keep working, for a screenshot diff and the
/// plays to compare before and after.
public final class AceEditorFeatureStories {

    private static final String WIDTH = "720px";
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
                    final EditorPresenter editor = editor(SAMPLE_WORDS, AceEditorMode.TEXT, false);
                    editor.getBasicAutoCompletionOption().setAvailable(true);
                    editor.getBasicAutoCompletionOption().setOn(true);
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
                    final EditorPresenter editor = editor("", AceEditorMode.XML, false);
                    editor.getSnippetsOption().setAvailable(true);
                    editor.getSnippetsOption().setOn(true);
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
                    final EditorPresenter editor = editor(AceEditorStories.SAMPLE_XML, AceEditorMode.XML, true);
                    editor.getIndicatorsOption().setAvailable(true);
                    editor.getIndicatorsOption().setOn(true);
                    editor.setIndicators(allSeverities());
                    return framed(editor);
                })
                // Vim keys: in normal mode, x deletes the character under the cursor
                .story("VimKeys", context -> {
                    final EditorPresenter editor = editor("first line\nsecond line", AceEditorMode.TEXT, false);
                    editor.getUseVimBindingsOption().setAvailable(true);
                    editor.getUseVimBindingsOption().setOn(true);
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
                })
                // Stroom's own syntax modes
                .story("StroomQueryMode", context -> framed(editor(SAMPLE_QUERY,
                        AceEditorMode.STROOM_QUERY, true)))
                .story("StroomExpressionMode", context -> framed(editor(SAMPLE_EXPRESSION,
                        AceEditorMode.STROOM_EXPRESSION, true)))
                .story("StroomDataSplitterMode", context -> framed(editor(SAMPLE_DATA_SPLITTER,
                        AceEditorMode.STROOM_DATA_SPLITTER, true)))
                .story("StroomHexDumpMode", context -> framed(editor(SAMPLE_HEX_DUMP,
                        AceEditorMode.STROOM_HEX_DUMP, true)));
    }

    /// Focuses Ace's hidden text area, so that keys go to the editor.
    private static void focusEditor(final Play play) {
        final Query input = play.querySelector(".ace_text-input");
        play.waitFor(() -> play.expect(input).toBeInTheDocument());
        play.run("focus the editor", () -> input.element().get().focus());
    }

    /// The editor, with the given text, mode and read only state, and line numbers.
    static EditorPresenter editor(final String text, final AceEditorMode mode, final boolean readOnly) {
        final EditorPresenter editor = EditorWidgets.editorPresenter(new SimpleEventBus());
        editor.setMode(mode);
        editor.setReadOnly(readOnly);
        editor.getLineNumbersOption().setOn(true);
        editor.setText(text);
        return editor;
    }

    static Widget framed(final EditorPresenter editor) {
        return EditorWidgets.frame(editor.getWidget(), WIDTH, HEIGHT);
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
