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

package stroom.dashboard.client.component;

import stroom.editor.client.component.CodeEditor;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// The content of a dashboard table column's expression editor: the query help beside the
/// expression's editor.
public class ColumnFunctionEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SimplePanel queryHelp;
    @UiField
    CodeEditor editor;

    /// Makes the content, with no query help until [#setQueryHelp(IsWidget)].
    public ColumnFunctionEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The expression's editor, for its controller to attach to.
    public CodeEditor getEditor() {
        return editor;
    }

    /// @param queryHelp The query help, beside the editor.
    public void setQueryHelp(final IsWidget queryHelp) {
        this.queryHelp.setWidget(queryHelp);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ColumnFunctionEditor> {

    }
}
