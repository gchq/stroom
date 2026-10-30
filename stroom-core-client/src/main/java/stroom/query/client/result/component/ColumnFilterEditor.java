/*
 * Copyright 2021 Crown Copyright
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

package stroom.query.client.result.component;

import stroom.editor.client.component.CodeEditor;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A table column's filter: an expression, e.g. `>100 and <200`, in a code editor.
public class ColumnFilterEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    CodeEditor editor;

    /// Makes an empty filter.
    public ColumnFilterEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The code editor, for its controller to attach to.
    public CodeEditor getEditor() {
        return editor;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ColumnFilterEditor> {

    }
}
