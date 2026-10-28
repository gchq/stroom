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
import stroom.gwt.workbench.client.StoryPanels;
import stroom.gwt.workbench.client.widgets.StoryPopups;
import stroom.gwt.workbench.framework.client.play.Query;
import stroom.gwt.workbench.framework.client.play.Spy;
import stroom.gwt.workbench.framework.client.play.TextMatch;
import stroom.gwt.workbench.framework.client.play.Value;
import stroom.gwt.workbench.framework.client.story.StoryContext;
import stroom.gwt.workbench.framework.client.story.StoryLayout;
import stroom.gwt.workbench.framework.client.story.StoryRegistry;
import stroom.util.shared.DefaultLocation;
import stroom.util.shared.Indicators;
import stroom.util.shared.Severity;
import stroom.util.shared.StoredError;
import stroom.widget.form.client.TextBox;

import com.google.gwt.dom.client.PreElement;
import com.google.gwt.user.client.ui.HTML;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.web.bindery.event.shared.SimpleEventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;

/// Stories for Stroom's Ace based editor ([CodeEditorController] attached to its `CodeEditor`).
///
/// The stories set the presenter's options: `setMode`, `setReadOnly`, the line numbers and line
/// wrap options, the format action and `setIndicators`.
public final class AceEditorStories {

    /// The name of the spy for the editor's text changes.
    static final String ON_CHANGE = "onChange";

    // The name Ace gives the editor's tab stop in its keyboard accessibility mode
    private static final String CONTENT = "Editor content, press Enter to start editing, press Escape to exit";

    private static final String WIDTH = "720px";
    private static final String HEIGHT = "380px";
    private static final String ACE_CONTENT = ".ace_content";
    private static final String MENU_ITEM = "menuitem";

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
                // Read-only XML viewer, the DataViewerController default (gutter on so line numbers show)
                .story("XmlReadOnly", context -> {
                    final CodeEditorController editor = editor(context, SAMPLE_XML, AceEditorMode.XML, true);
                    return framed(editor);
                })
                // Editable XML editor, two-way bound via onChange; Ctrl+Shift+F formats
                .story("EditableXml", context -> {
                    final CodeEditorController editor = editor(context, "<root><a>1</a><b><c>2</c></b></root>",
                            AceEditorMode.XML, false);
                    // Make the format action available
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
                    // Focus Ace's hidden text area, then send real keystrokes
                    play.run("focus the editor", () -> input.element().get().focus());
                    play.keyboard("hello world");
                    // onChange fired, so the probe holds the typed text, and Ace's lines show it
                    play.waitFor(() -> play.expect(play.getByTestId("typed")).toHaveTextContent("hello world"));
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toMatch("hello world"));
                })
                // A right-click shows the edit items (gchq/stroom#5897), then Format and the editor's
                // options in a sub menu
                .story("ContextMenu", context -> menuEditor(context, false))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(ACE_CONTENT)).toBeInTheDocument());
                    play.rightClick(play.querySelector(ACE_CONTENT));
                    final Query menu = play.screen().findByRole("menu", "Editor");
                    for (final String item : List.of("Undo", "Redo", "Cut", "Copy", "Paste", "Delete",
                            "Select All", "Format", "Editor Options")) {
                        play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing(item)))
                                .toBeInTheDocument();
                    }
                    // Nothing is selected yet
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Copy")))
                            .toHaveAttribute("aria-disabled", "true");
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Editor Options")))
                            .toHaveAttribute("aria-haspopup", "menu");

                    // Select All, then Delete, empties the editor; Undo puts the text back
                    play.click(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Select All")));
                    play.waitFor(() -> play.expect(play.screen().queryByRole("menu")).toBeNull());
                    play.rightClick(play.querySelector(ACE_CONTENT));
                    play.click(play.screen().findByRole(MENU_ITEM, TextMatch.containing("Delete")));
                    final Value<List<String>> lines = play.querySelectorAll(".ace_line").textContents();
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toBe(""));
                    play.rightClick(play.querySelector(ACE_CONTENT));
                    play.click(play.screen().findByRole(MENU_ITEM, TextMatch.containing("Undo")));
                    play.waitFor(() -> play.expect("the editor's value", () -> String.join("\n", lines.get()))
                            .toMatch("<root>"));
                })
                // A read-only editor can only be copied from
                .story("ReadOnlyContextMenu", context -> menuEditor(context, true))
                .withPlay(play -> {
                    play.waitFor(() -> play.expect(play.querySelector(ACE_CONTENT)).toBeInTheDocument());
                    play.rightClick(play.querySelector(ACE_CONTENT));
                    final Query menu = play.screen().findByRole("menu", "Editor");
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Copy")))
                            .toBeInTheDocument();
                    play.expect(play.within(menu).getByRole(MENU_ITEM, TextMatch.containing("Select All")))
                            .toBeInTheDocument();
                    play.expect(play.within(menu).queryByRole(MENU_ITEM, TextMatch.containing("Paste")))
                            .toBeNull();
                    play.expect(play.within(menu).queryByRole(MENU_ITEM, TextMatch.containing("Cut")))
                            .toBeNull();
                })
                // Plain text, read-only, not wrapped (no syntax highlighting)
                .story("PlainText", context -> {
                    final CodeEditorController editor = editor(context, SAMPLE_TEXT, AceEditorMode.TEXT, true);
                    editor.getOptions().getLineWrapOption().setOn(false);
                    return framed(editor);
                })
                // Validation indicators: gutter glyphs plus the right bar's overview and markers
                .story("WithAnnotations", context -> {
                    final CodeEditorController editor = editor(context, SAMPLE_XML, AceEditorMode.XML, true);
                    editor.getOptions().getIndicatorsOption().setAvailable(true);
                    editor.getOptions().getIndicatorsOption().setOn(true);
                    editor.setIndicators(annotations());
                    return framed(editor);
                })
                // JavaScript mode, a different Ace syntax mode
                .story("JavaScriptMode", context -> {
                    final CodeEditorController editor = editor(context, SAMPLE_JAVASCRIPT, AceEditorMode.JAVASCRIPT,
                            false);
                    return framed(editor);
                })
                // The way out of an editable editor (Ace's keyboard accessibility mode): its content is
                // the tab stop, Enter starts editing (where Tab indents), Escape stops, and Tab then
                // leaves
                .story("KeyboardWayOut", context -> StoryPanels.column(10,
                        namedTextBox("Before"),
                        framed(editor(context, SAMPLE_XML, AceEditorMode.XML, false)),
                        namedTextBox("After")))
                .withPlay(play -> {
                    final Query before = play.getByRole("textbox", "Before");
                    final Query content = play.getByRole("group", CONTENT);
                    final Query input = play.querySelector(".ace_text-input");
                    play.waitFor(() -> play.expect(content).toBeInTheDocument());
                    // Ace's text input isn't a tab stop, and keeps Stroom's name before the cursor's row
                    play.expect(input).toHaveAttribute("tabindex", "-1");
                    play.expect(input).toHaveAttribute("aria-label", "Code editor, Cursor at row 1");
                    // Nor is its gutter, which is hidden from screen readers, as without the mode
                    play.expect(play.querySelector(".ace_gutter")).not().toHaveAttribute("tabindex");
                    play.expect(play.querySelector(".ace_gutter")).toHaveAttribute("aria-hidden", "true");
                    // One tab stop: the content
                    play.run("focus the field before", () -> before.element().get().focus());
                    play.tab();
                    play.expect(content).toHaveFocus();
                    // Enter starts editing, where Tab indents and keeps the focus
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(input).toHaveFocus());
                    play.keyboard("{Tab}");
                    play.expect(input).toHaveFocus();
                    play.waitFor(() -> play.expect(play.spy(ON_CHANGE)).toHaveBeenCalled());
                    // Escape stops editing, and Tab then leaves the editor
                    play.keyboard("{Escape}");
                    play.expect(content).toHaveFocus();
                    play.tab();
                    play.expect(play.getByRole("textbox", "After")).toHaveFocus();
                    // Shift+Tab comes back to the content, not into the text
                    play.tab(true);
                    play.expect(content).toHaveFocus();
                })
                // With the gutter no tab stop, its errors and folds have keys while editing: Alt+E
                // shows the next error's message under its line, and F2 folds the line's element
                .story("KeyboardErrorsAndFolds", context -> {
                    final CodeEditorController editor = editor(context, SAMPLE_XML, AceEditorMode.XML, false);
                    editor.getOptions().getIndicatorsOption().setAvailable(true);
                    editor.getOptions().getIndicatorsOption().setOn(true);
                    editor.setIndicators(annotations());
                    return framed(editor);
                })
                .withPlay(play -> {
                    final Query content = play.getByRole("group", CONTENT);
                    final Query input = play.querySelector(".ace_text-input");
                    play.waitFor(() -> play.expect(content).toBeInTheDocument());
                    play.run("focus the content", () -> content.element().get().focus());
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(input).toHaveFocus());
                    play.keyboard("{Alt>}e{/Alt}");
                    play.waitFor(() -> play.expect(play.querySelector(".error_widget"))
                            .toHaveTextContent(TextMatch.containing("Unexpected element <EventTime>")));
                    play.expect(input).toHaveFocus();
                    // The cursor is on the error's line, 3, whose element F2 folds (its fold widget)
                    play.keyboard("{F2}");
                    play.waitFor(() -> play.expect(play.querySelector(".ace_fold")).toBeInTheDocument());
                })
                // A read-only editor is reached the same way, Enter putting the caret in the text to
                // move through it
                .story("KeyboardReadOnly", context -> StoryPanels.column(10,
                        namedTextBox("Before"),
                        framed(editor(context, SAMPLE_XML, AceEditorMode.XML, true)),
                        namedTextBox("After")))
                .withPlay(play -> {
                    final Query content = play.getByRole("group", CONTENT);
                    final Query input = play.querySelector(".ace_text-input");
                    play.waitFor(() -> play.expect(content).toBeInTheDocument());
                    play.expect(input).toHaveAttribute("aria-readonly", "true");
                    play.run("focus the content", () -> content.element().get().focus());
                    play.keyboard("{Enter}");
                    play.waitFor(() -> play.expect(input).toHaveFocus());
                    play.keyboard("{Escape}");
                    play.expect(content).toHaveFocus();
                    play.tab();
                    play.expect(play.getByRole("textbox", "After")).toHaveFocus();
                    play.expect(play.spy(ON_CHANGE)).not().toHaveBeenCalled();
                });
    }

    private static TextBox namedTextBox(final String name) {
        final TextBox textBox = new TextBox();
        textBox.getElement().setAttribute("aria-label", name);
        return textBox;
    }

    // An editor that shows Stroom's menus, as in Stroom
    private static Widget menuEditor(final StoryContext context, final boolean readOnly) {
        final StoryPopups popups = StoryPopups.create(context).withMenus();
        final CodeEditorController editor = EditorWidgets.codeEditor(popups.getEventBus());
        editor.setMode(AceEditorMode.XML);
        editor.setReadOnly(readOnly);
        editor.getFormatAction().setAvailable(!readOnly);
        editor.setText("<root><a>1</a></root>");
        return framed(editor);
    }

    private static Widget editableTyping(final StoryContext context) {
        final CodeEditorController editor = editor(context, "", AceEditorMode.TEXT, false);
        // A probe showing the typed value
        final HTML probe = new HTML();
        probe.getElement().setAttribute("data-testid", "typed");
        final PreElement pre = PreElement.as(probe.getElement().appendChild(
                probe.getElement().getOwnerDocument().createPreElement()));
        editor.addValueChangeHandler(event -> pre.setInnerText(editor.getText()));
        return StoryPanels.column(0, framed(editor), probe);
    }

    /// The editor, with the given text, mode and read only state, and line numbers.
    private static CodeEditorController editor(final StoryContext context,
                                          final String text,
                                          final AceEditorMode mode,
                                          final boolean readOnly) {
        // No context menu, as only screen stories (with a popup manager) show
        // Stroom's menus
        final CodeEditorController editor = EditorWidgets.codeEditor(new SimpleEventBus());
        editor.setMode(mode);
        editor.setReadOnly(readOnly);
        // Show line numbers
        editor.getOptions().getLineNumbersOption().setOn(true);
        editor.setText(text);
        final Spy onChange = context.fn(ON_CHANGE);
        // The editor's change events have no value (Editor fires them with null), so read the text
        editor.addValueChangeHandler(event -> onChange.call(editor.getText()));
        return editor;
    }

    private static Widget framed(final CodeEditorController editor) {
        return EditorWidgets.frame(editor.asWidget(), WIDTH, HEIGHT);
    }

    /// The annotations, as Stroom's indicators (lines are one based).
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
