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

package stroom.data.client.component;

import stroom.editor.client.Action;
import stroom.editor.client.component.CodeEditorController;
import stroom.editor.client.component.CodeEditorOptions;
import stroom.util.shared.TextRange;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.inject.Provider;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.List;
import java.util.Objects;

/// The text of a stream in the data viewer ([DataTextPane]): sets its code editor up to show data,
/// read only, wrapped and without line numbers or indicators, which are meaningless for data
/// previewed in formatted form, and with no Format.
public class DataTextPaneController extends Controller {

    private final CodeEditorController editor;
    private DataTextPane view;

    /// Made by GIN; [#attach(DataTextPane)] gives it its pane.
    ///
    /// @param eventBus                     The application's event bus.
    /// @param codeEditorControllerProvider Makes the code editor's controller.
    @Inject
    public DataTextPaneController(final EventBus eventBus,
                                  final Provider<CodeEditorController> codeEditorControllerProvider) {
        super(eventBus);
        editor = make(codeEditorControllerProvider);
    }

    /// Attaches this to a pane, e.g. one a template declares, and its code editor to the pane's
    /// editor, set up to show data. The pane is then this controller's widget.
    ///
    /// @param view The pane.
    /// @return This, for its owner to keep.
    public DataTextPaneController attach(final DataTextPane view) {
        this.view = Objects.requireNonNull(view, "view");
        initWidget(view);
        editor.attach(view.getEditor());

        final CodeEditorOptions options = editor.getOptions();
        options.getIndicatorsOption().setAvailable(false);
        options.getIndicatorsOption().setOn(false);

        // Previewing data in formatted form so line numbers are meaningless
        options.getLineNumbersOption().setAvailable(false);
        options.getLineNumbersOption().setOn(false);

        options.getLineWrapOption().setAvailable(true);
        options.getLineWrapOption().setOn(true);
        options.getShowInvisiblesOption().setAvailable(true);
        options.getUseVimBindingsOption().setAvailable(true);

        editor.setReadOnly(true);

        // Should not be formatting read-only content
        editor.getFormatAction().setAvailable(false);
        return this;
    }

    /// @return The editor's options, e.g. View As Hex, which its menu turns on and off.
    public CodeEditorOptions getOptions() {
        return editor.getOptions();
    }

    /// @return Format, which formats the text.
    public Action getFormatAction() {
        return editor.getFormatAction();
    }

    /// Puts the focus on the text.
    public void focus() {
        editor.focus();
    }

    /// @param text The text to show.
    public void setText(final String text) {
        editor.setText(text);
    }

    /// @param text   The text to show.
    /// @param format Whether to format it first, e.g. as XML.
    public void setText(final String text, final boolean format) {
        editor.setText(text, format);
    }

    /// Shows an error in place of the text.
    ///
    /// @param title     The error's title.
    /// @param errorText The error.
    public void setErrorText(final String title, final String errorText) {
        editor.setErrorText(title, errorText);
    }

    /// @param mode How the text is highlighted, e.g. as XML.
    public void setMode(final AceEditorMode mode) {
        editor.setMode(mode);
    }

    /// @param firstLineNumber The number of the text's first line, e.g. where a part of a stream
    ///                        starts.
    public void setFirstLineNumber(final int firstLineNumber) {
        editor.setFirstLineNumber(firstLineNumber);
    }

    /// @param highlights The ranges of text to highlight, e.g. a record.
    public void setHighlights(final List<TextRange> highlights) {
        editor.setHighlights(highlights);
    }

    /// @param readOnly Whether the text can't be changed.
    public void setReadOnly(final boolean readOnly) {
        editor.setReadOnly(readOnly);
    }

    /// Makes Format and every option available or not, as they are by default.
    public void setOptionsToDefaultAvailability() {
        editor.setOptionsToDefaultAvailability();
    }

    /// Shows or hides the controls over the text: the stepping button and the editor's own.
    ///
    /// @param controlsVisible Whether they are shown.
    public void setControlsVisible(final boolean controlsVisible) {
        view.setSteppingVisible(controlsVisible);
        editor.setControlsVisible(controlsVisible);
    }

    /// @param isWrapped Whether long lines are wrapped.
    public void setWrapLines(final boolean isWrapped) {
        editor.getOptions().getLineWrapOption().setOn(isWrapped);
    }

    /// @param onBeginStepping What to run when the user presses the stepping button.
    public void setOnBeginStepping(final Runnable onBeginStepping) {
        view.setOnBeginStepping(onBeginStepping);
    }
}
