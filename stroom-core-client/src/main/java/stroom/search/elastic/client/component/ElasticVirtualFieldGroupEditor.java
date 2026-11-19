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

package stroom.search.elastic.client.component;

import stroom.query.client.component.ExpressionEditor;
import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Function;

/// The virtual field group dialog's content: a group's name and the query run when users search
/// the virtual field for it. Its controller ([ElasticVirtualFieldGroupEditorController]) attaches the
/// query's editor.
public class ElasticVirtualFieldGroupEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    ExpressionEditor expressionEditor;

    /// Makes an empty form.
    public ElasticVirtualFieldGroupEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The query's editor, for its controller to attach to.
    public ExpressionEditor getExpressionEditor() {
        return expressionEditor;
    }

    /// @return The name as entered, without the space round it.
    public String getName() {
        return NullSafe.trim(name.getValue());
    }

    /// @param name The name to show, or null for none.
    public void setName(final String name) {
        this.name.setValue(NullSafe.string(name));
    }

    /// Checks the name: while it can't be used, the dialog's OK refuses and says why under it.
    ///
    /// @param invalidReason Given the name, says why it can't be used, or gives null if it can.
    public void setInvalidReason(final Function<String, String> invalidReason) {
        name.setInvalidReason(text -> invalidReason.apply(NullSafe.trim(text)));
    }

    /// Puts the focus in the name, with its text selected, unless it is read only.
    public void focus() {
        if (!isReadOnly()) {
            name.setFocus(true);
            name.selectAll();
        }
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        name.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ElasticVirtualFieldGroupEditor> {

    }
}
