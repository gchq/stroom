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
import com.google.gwt.user.client.ui.Widget;

/// A pathway's name, then its paths beside the constraints of the path chosen.
public class PathwayEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    PathwayTree tree;
    @UiField
    ConstraintList constraints;

    /// Makes the form, empty.
    public PathwayEditor() {
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

    /// @return The pathway's paths, which its controller attaches the graph's controller to.
    public PathwayTree getTree() {
        return tree;
    }

    /// @return The chosen path's constraints, which its controller attaches the list's controller
    ///         to.
    public ConstraintList getConstraints() {
        return constraints;
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PathwayEditor> {

    }
}
