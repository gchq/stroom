/*
 * Copyright 2016 Crown Copyright
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

package stroom.editor.client.component;

import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;
import java.util.function.Consumer;

/// A single line code editor's controller: it sets the text of its [SingleLineCodeEditor], with
/// the options a single line has no use for (e.g. Line Numbers) off, and its [AceEditorController]
/// sets the editor up as the user prefers.
///
/// It is work in progress: e.g. the Enter key isn't stopped from doing anything yet.
public class SingleLineCodeEditorController extends Controller {

    private final AceEditorController aceEditorController;
    private SingleLineCodeEditor view;
    private CodeEditorOptions options;
    private Consumer<String> onChange = text -> {
    };

    /// Made by GIN; [#attach(SingleLineCodeEditor)] gives it its editor.
    ///
    /// @param eventBus            The application's event bus.
    /// @param aceEditorController Sets the editor up as the user prefers; this owns it.
    @Inject
    public SingleLineCodeEditorController(final EventBus eventBus,
                                          final AceEditorController aceEditorController) {
        super(eventBus);
        this.aceEditorController = own(aceEditorController);
        // Read only as whatever owns it is
        watch(Contexts.READ_ONLY, readOnly -> {
            if (view != null) {
                view.setReadOnly(readOnly);
            }
        });
    }

    /// Attaches this to an editor, e.g. one a template declares, setting it up: with the options a
    /// single line has no use for off, and as the user prefers.
    ///
    /// @param view The editor.
    /// @return This, for its owner to keep.
    public SingleLineCodeEditorController attach(final SingleLineCodeEditor view) {
        this.view = Objects.requireNonNull(view, "view");
        options = new CodeEditorOptions(view.getEditor(), showIndicators -> {
            // A single line has no overview bar to show them in
        });
        // Settings for our single line use case
        options.getLineWrapOption().setOff();
        options.getHighlightActiveLineOption().setOff();
        options.getLineNumbersOption().setOff();
        options.getShowIndentGuides().setOff();
        options.getShowInvisiblesOption().setOff();
        options.getViewAsHexOption().setOff();
        aceEditorController.attach(view.getEditor(), options);
        view.getEditor().addValueChangeHandler(event -> onChange.accept(event.getValue()));
        view.setReadOnly(Boolean.TRUE.equals(context(Contexts.READ_ONLY)));
        return this;
    }

    /// @return The editor this is attached to.
    public SingleLineCodeEditor getEditor() {
        return view;
    }

    /// @return The text.
    public String getText() {
        return view.getText();
    }

    /// @param text The text, or null for none.
    public void setText(final String text) {
        view.setText(text);
    }

    /// Sets the mode the text is in, e.g. one for Stroom's `${...}` parameters.
    ///
    /// @param mode The mode.
    public void setMode(final AceEditorMode mode) {
        options.setMode(mode);
    }

    /// Puts the focus on the text.
    public void focus() {
        view.focus();
    }

    /// Sets what is told when the user changes the text.
    ///
    /// @param onChange Given the text each time.
    public void setOnChange(final Consumer<String> onChange) {
        this.onChange = onChange;
    }

    /// @see AceEditorController#registerCompletionProviders(AceCompletionProvider...)
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        aceEditorController.registerCompletionProviders(completionProviders);
    }
}
