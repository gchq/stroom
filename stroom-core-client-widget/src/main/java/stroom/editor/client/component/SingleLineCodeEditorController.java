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

import stroom.widget.component.client.Controller;

import com.google.gwt.event.logical.shared.ValueChangeHandler;
import com.google.gwt.event.shared.HandlerRegistration;
import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceCompletionProvider;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

/// A single line code editor's controller: it sets the text of its [SingleLineCodeEditor], with
/// the options a single line has no use for (e.g. Line Numbers) off, and its [AceEditorController]
/// sets the editor up as the user prefers.
///
/// It is work in progress: e.g. the Enter key isn't stopped from doing anything yet.
public class SingleLineCodeEditorController extends Controller {

    private final SingleLineCodeEditor view;
    private final CodeEditorOptions options;
    private final AceEditorController aceEditorController;

    /// Made by GIN, with its editor.
    ///
    /// @param eventBus            The application's event bus.
    /// @param view                The editor.
    /// @param aceEditorController Sets the editor up as the user prefers; this owns it.
    @Inject
    public SingleLineCodeEditorController(final EventBus eventBus,
                                          final SingleLineCodeEditor view,
                                          final AceEditorController aceEditorController) {
        super(eventBus);
        this.view = view;
        initWidget(view);
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
        this.aceEditorController = own(aceEditorController).attach(view.getEditor(), options);
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

    /// @param readOnly Whether the text is read only.
    public void setReadOnly(final boolean readOnly) {
        view.setReadOnly(readOnly);
    }

    /// Puts the focus on the text.
    public void focus() {
        view.focus();
    }

    /// Listens for the text changing, until the registration is removed.
    ///
    /// @param handler Told the text each time.
    /// @return The registration, for the handler's owner to own.
    public HandlerRegistration addValueChangeHandler(final ValueChangeHandler<String> handler) {
        return view.getEditor().addValueChangeHandler(handler);
    }

    /// @see AceEditorController#registerCompletionProviders(AceCompletionProvider...)
    public void registerCompletionProviders(final AceCompletionProvider... completionProviders) {
        aceEditorController.registerCompletionProviders(completionProviders);
    }
}
