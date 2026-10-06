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
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;

import com.google.gwt.dom.client.PreElement;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;

/// Stories for Stroom's Ace based editor ([EditorPresenter] with `EditorViewImpl`), matching
/// `Widgets/Editors & Viewers/AceEditor` in the React Storybook.
///
/// The React port's props map to the presenter's options: `mode` → `setMode`, `readOnly` →
/// `setReadOnly`, `showGutter` → the line numbers option, `wrapLines` → the line wrap option,
/// `formatAvailable` → the format action and `annotations` → `setIndicators`.
public final class AceEditorStories {

    /// The name of the spy for React's `onChange` prop.
    static final String ON_CHANGE = "onChange";

    private static final String WIDTH = "720px";
    private static final String HEIGHT = "380px";

    static final String SAMPLE_XML = """
            <?xml version="1.0" encoding="UTF-8"?>
            <Events xmlns="event-logging:3">
              <Event>
                <EventTime><TimeCreated>2024-01-15T09:30:00.000Z</TimeCreated></EventTime>
                <EventSource>
                  <System><Name>Stroom</Name></System>
                  <Generator>Example Generator</Generator>
                </EventSource>
                <EventDetail><TypeId>login</TypeId></EventDetail>
              </Event>
            </Events>""";

    static final String SAMPLE_TEXT = """
            2024-01-15 09:30:00 INFO  Started stream processing
            2024-01-15 09:30:01 WARN  Slow response from upstream (1200ms)
            2024-01-15 09:30:02 ERROR Failed to parse record 42
            2024-01-15 09:30:03 INFO  Retrying...
            2024-01-15 09:30:04 INFO  Completed with 1 error""";

    private static final String SAMPLE_JAVASCRIPT = "function greet(name) {\n  return `Hello, ${name}!`;\n}\n\n"
                                                    + "console.log(greet(\"Stroom\"));";

    private AceEditorStories() {
        // Static utility
    }

    /// Adds the stories to the registry.
    ///
    /// @param registry The registry to add to.
    public static void addTo(final StoryRegistry registry) {
        registry.component("Widgets/Editors & Viewers/AceEditor", AceEditorStories.class)
                .layout(StoryLayout.CENTERED)
                // Read-only XML viewer, the DataPresenter default (gutter on so line numbers show)
                .story("XmlReadOnly", context -> {
                    final EditorPresenter editor = editor(context, SAMPLE_XML, AceEditorMode.XML, true);
                    return framed(editor);
                })
                // Editable XML editor, two-way bound via onChange; Ctrl+Shift+F formats
                .story("EditableXml", context -> {
                    final EditorPresenter editor = editor(context, "<root><a>1</a><b><c>2</c></b></root>",
                            AceEditorMode.XML, false);
                    // React's formatAvailable
                    editor.getFormatAction().setAvailable(true);
                    final Label note = new Label("Ctrl+Shift+F to format.");
                    note.getElement().getStyle().setProperty("marginTop", "8px");
                    note.getElement().getStyle().setProperty("fontSize", "12px");
                    return StoryPanels.column(0, framed(editor), note);
                })
                // Typing into the editor; the value is mirrored into a probe
                .story("EditableTyping", AceEditorStories::editableTyping)
                .withPlay(play -> {
                    final Query input = play.querySelector(".ace_text-input");
                    play.waitFor(() -> play.expect(input).toBeInTheDocument());
                    // React's typeIntoAce: focus Ace's hidden text area, then send real keystrokes
                    play.run("focus the editor", () -> input.element().get().focus());
                    play.keyboard("hello world");
                    // onChange fired, so the probe holds the typed text, and Ace's lines show it
                    play.waitFor(() -> play.expect(play.getByTestId("typed")).toHaveTextContent("hello world"));
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toMatch("hello world"));
                })
                // Plain text, read-only, not wrapped (no syntax highlighting)
                .story("PlainText", context -> {
                    final EditorPresenter editor = editor(context, SAMPLE_TEXT, AceEditorMode.TEXT, true);
                    editor.getLineWrapOption().setOn(false);
                    return framed(editor);
                })
                // Validation indicators: gutter glyphs plus the right bar's overview and markers
                .story("WithAnnotations", context -> {
                    final EditorPresenter editor = editor(context, SAMPLE_XML, AceEditorMode.XML, true);
                    editor.getIndicatorsOption().setAvailable(true);
                    editor.getIndicatorsOption().setOn(true);
                    editor.setIndicators(annotations());
                    return framed(editor);
                })
                // JavaScript mode, a different Ace syntax mode
                .story("JavaScriptMode", context -> {
                    final EditorPresenter editor = editor(context, SAMPLE_JAVASCRIPT, AceEditorMode.JAVASCRIPT,
                            false);
                    return framed(editor);
                });
    }

    private static Widget editableTyping(final StoryContext context) {
        final EditorPresenter editor = editor(context, "", AceEditorMode.TEXT, false);
        // React's <pre data-testid="typed">{value}</pre>
        final HTML probe = new HTML();
        probe.getElement().setAttribute("data-testid", "typed");
        final PreElement pre = PreElement.as(probe.getElement().appendChild(
                probe.getElement().getOwnerDocument().createPreElement()));
        editor.addValueChangeHandler(event -> pre.setInnerText(editor.getText()));
        return StoryPanels.column(0, framed(editor), probe);
    }

    /// The editor, as React's `<AceEditor text mode readOnly showGutter />`.
    private static EditorPresenter editor(final StoryContext context,
                                          final String text,
                                          final AceEditorMode mode,
                                          final boolean readOnly) {
        // Differs from React: no context menu, as only screen stories (with a popup manager) show
        // Stroom's menus
        final EditorPresenter editor = EditorWidgets.editorPresenter(new SimpleEventBus());
        editor.setMode(mode);
        editor.setReadOnly(readOnly);
        // React's showGutter
        editor.getLineNumbersOption().setOn(true);
        editor.setText(text);
        final Spy onChange = context.fn(ON_CHANGE);
        // The editor's change events have no value (Editor fires them with null), so read the text
        editor.addValueChangeHandler(event -> onChange.call(editor.getText()));
        return editor;
    }

    private static Widget framed(final EditorPresenter editor) {
        return EditorWidgets.frame(editor.getWidget(), WIDTH, HEIGHT);
    }

    /// React's annotations (rows are zero based, Stroom's lines one based).
    private static IndicatorLines annotations() {
        final Indicators indicators = new Indicators();
        indicators.add(new StoredError(Severity.ERROR, DefaultLocation.of(3, 5), null,
                "Unexpected element <EventTime>"));
        indicators.add(new StoredError(Severity.WARNING, DefaultLocation.of(6, 7), null,
                "Deprecated system name"));
        indicators.add(new StoredError(Severity.INFO, DefaultLocation.of(9, 5), null,
                "Consider adding a schema location"));
        return new IndicatorLines(indicators);
    }
}
