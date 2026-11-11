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

import stroom.util.shared.NullSafe;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

import java.util.function.Function;

/// The virtual field name dialog's content: the name of a new virtual field, or a new name for
/// one. Its controller is [ElasticVirtualFieldNameController].
public class ElasticVirtualFieldName extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;

    /// Makes an empty form.
    public ElasticVirtualFieldName() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// @return The name as entered, without the space round it.
    public String getName() {
        return NullSafe.trim(name.getValue());
    }

    /// @param name The name to start with, or null for none.
    public void setName(final String name) {
        this.name.setValue(NullSafe.string(name));
    }

    /// Checks the name: while it can't be used, the dialog's OK refuses and says why under it.
    ///
    /// @param invalidReason Given the name, says why it can't be used, or gives null if it can.
    public void setInvalidReason(final Function<String, String> invalidReason) {
        name.setInvalidReason(text -> invalidReason.apply(NullSafe.trim(text)));
    }

    /// Puts the focus in the name, with its text selected.
    public void focus() {
        name.setFocus(true);
        name.selectAll();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ElasticVirtualFieldName> {

    }
}
