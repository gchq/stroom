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

package stroom.document.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.iframe.client.component.IFrame;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// Markdown being edited: the text in a code editor beside its rendered preview.
public class MarkdownEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    CodeEditor editor;
    @UiField
    IFrame preview;

    /// Makes an empty editor and preview.
    public MarkdownEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The code editor, for its controller to attach to.
    public CodeEditor getEditor() {
        return editor;
    }

    /// @return The frame the Markdown is rendered in.
    public IFrame getPreview() {
        return preview;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, MarkdownEditor> {

    }
}
