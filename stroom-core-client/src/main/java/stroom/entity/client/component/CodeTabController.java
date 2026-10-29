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

package stroom.entity.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.editor.client.component.CodeEditorController;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Contexts;
import stroom.widget.component.client.Controller;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import edu.ycp.cs.dh.acegwt.client.ace.AceEditorMode;

import java.util.Objects;

/// A document's tab that is a code editor, e.g. an XSLT's or a Kafka configuration's: read only,
/// and with no Format, as its document is ([Contexts#READ_ONLY]).
public class CodeTabController extends Controller {

    private final CodeEditorController editor;
    private Runnable onChange = () -> {
    };

    /// Made by GIN, with the editor it shows and the editor's controller.
    ///
    /// @param eventBus The application's event bus.
    /// @param view     The code editor.
    /// @param editor   The code editor's controller, which this owns.
    @Inject
    public CodeTabController(final EventBus eventBus,
                             final CodeEditor view,
                             final CodeEditorController editor) {
        super(eventBus);
        initWidget(view);
        this.editor = own(editor).attach(view);
        own(editor.addValueChangeHandler(event -> onChange.run()));
        own(editor.addFormatListener(() -> onChange.run()));
        watch(Contexts.READ_ONLY, readOnly -> {
            editor.setReadOnly(readOnly);
            editor.getFormatAction().setAvailable(!readOnly);
        });
    }

    /// @param onChange What to run when the user changes the text, or formats it.
    public void setOnChange(final Runnable onChange) {
        this.onChange = Objects.requireNonNull(onChange, "onChange");
    }

    /// @param mode The language the text is in, e.g. XML.
    public void setMode(final AceEditorMode mode) {
        editor.setMode(mode);
    }

    /// @param text The text to show; null shows none.
    public void setText(final String text) {
        editor.setText(NullSafe.string(text));
    }

    /// @param text   The text to show; null shows none.
    /// @param format Whether to format it first, as Format does.
    public void setText(final String text, final boolean format) {
        editor.setText(NullSafe.string(text), format);
    }

    /// @return The text shown.
    public String getText() {
        return editor.getText();
    }

    /// @return The code editor's controller, e.g. to change its options.
    public CodeEditorController getEditor() {
        return editor;
    }
}
