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

import stroom.query.client.component.ExpressionEditor;
import stroom.widget.component.client.Component;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The filter dialogs' content (Filter Streams, Filter Processors and the like): an expression
/// editor, filling the dialog.
public class ExpressionDialog extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    ExpressionEditor expressionEditor;

    /// Makes the content.
    public ExpressionDialog() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The expression's editor, which its controller attaches the editor's controller to.
    public ExpressionEditor getExpressionEditor() {
        return expressionEditor;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ExpressionDialog> {

    }
}
