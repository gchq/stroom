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

package stroom.pathways.client.component;

import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A pathway's name, then its paths beside the constraints of the path chosen.
public class PathwayEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    SimplePanel tree;
    @UiField
    SimplePanel constraints;

    /// Makes the form, empty.
    public PathwayEdit() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the keyboard focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The name typed.
    public String getName() {
        return name.getText();
    }

    /// @param name The pathway's name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @param tree The pathway's paths, a controller of their own.
    public void setTree(final IsWidget tree) {
        this.tree.setWidget(tree.asWidget());
    }

    /// @param constraints The chosen path's constraints, a controller of their own.
    public void setConstraints(final IsWidget constraints) {
        this.constraints.setWidget(constraints.asWidget());
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PathwayEdit> {

    }
}
