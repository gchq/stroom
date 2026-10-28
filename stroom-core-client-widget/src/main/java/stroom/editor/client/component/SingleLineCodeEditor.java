/*
 * Copyright 2024 Crown Copyright
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

import stroom.editor.client.view.Editor;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.tab.client.GlobalResizeObserver;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A single line code editor (BEM block `code-editor`, `code-editor.css`): one line of text in Ace
/// ([Editor]), e.g. a property's value with `${...}` parameters in it. Its
/// [SingleLineCodeEditorController] sets it up.
public class SingleLineCodeEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel content;
    @UiField
    Editor editor;

    /// Makes an empty editor.
    public SingleLineCodeEditor() {
        initWidget(BINDER.createAndBindUi(this));
        // The first line is line 1
        editor.setFirstLineNumber(1);
        // Laid out again as its text's panel changes size
        content.addAttachHandler(event -> {
            if (event.isAttached()) {
                GlobalResizeObserver.addListener(content.getElement(), element -> editor.onResize());
            } else {
                GlobalResizeObserver.removeListener(content.getElement());
            }
        });
    }

    /// @return The Ace editor the text is in, for the controller to set it up and act on it.
    public Editor getEditor() {
        return editor;
    }

    /// @param text The text, or null for none.
    public void setText(final String text) {
        editor.setText(NullSafe.string(text));
    }

    /// @return The text.
    public String getText() {
        return editor.getText();
    }

    /// Puts the focus on the text.
    public void focus() {
        editor.focus();
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        editor.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SingleLineCodeEditor> {

    }
}
