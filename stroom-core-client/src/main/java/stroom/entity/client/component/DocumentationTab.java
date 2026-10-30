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

package stroom.entity.client.component;

import stroom.iframe.client.component.IFrame;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A document's documentation: its rendered Markdown or, in edit mode, the Markdown being edited
/// beside its preview.
public class DocumentationTab extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    MarkdownEditor editor;
    @UiField
    IFrame preview;

    /// Makes empty documentation, showing the rendered Markdown.
    public DocumentationTab() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The Markdown editor, for its controller to attach to.
    public MarkdownEditor getEditor() {
        return editor;
    }

    /// @return The frame the Markdown is rendered in when not editing.
    public IFrame getPreview() {
        return preview;
    }

    /// Shows the editor, or the rendered Markdown.
    ///
    /// @param editing True to show the editor.
    public void setEditing(final boolean editing) {
        preview.setVisible(!editing);
        editor.setVisible(editing);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, DocumentationTab> {

    }
}
