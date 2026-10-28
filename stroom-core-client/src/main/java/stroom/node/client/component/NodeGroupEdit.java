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

package stroom.node.client.component;

import stroom.item.client.SelectionBox;
import stroom.node.shared.NodeInclusionBehaviour;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

import java.util.List;

/// A node group: its name, whether it is enabled, whether its ticked nodes are included or
/// excluded, and the list of nodes to tick.
public class NodeGroupEdit extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    TickBox enabled;
    @UiField
    SelectionBox<NodeInclusionBehaviour> nodeInclusionBehaviour;
    @UiField
    SimplePanel list;

    private Runnable onInvertSelectionChange = () -> {
    };

    /// Makes an empty form, offering both inclusion behaviours.
    public NodeGroupEdit() {
        initWidget(BINDER.createAndBindUi(this));
        nodeInclusionBehaviour.addValueChangeHandler(this::onNodeInclusionBehaviourChange);
        nodeInclusionBehaviour.addItems(List.of(
                NodeInclusionBehaviour.INCLUDE_SELECTED,
                NodeInclusionBehaviour.EXCLUDE_SELECTED));
    }

    /// Puts the focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @param name The group's name, which can't be changed here.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @return Whether Enabled is ticked.
    public boolean isEnabled() {
        return enabled.getValue();
    }

    /// @param enabled Whether Enabled is ticked.
    public void setEnabled(final boolean enabled) {
        this.enabled.setValue(enabled);
    }

    /// @return Whether the ticked nodes are excluded rather than included.
    public boolean isInvertSelection() {
        return NodeInclusionBehaviour.EXCLUDE_SELECTED.equals(nodeInclusionBehaviour.getValue());
    }

    /// @param invertSelection Whether the ticked nodes are excluded rather than included.
    public void setInvertSelection(final boolean invertSelection) {
        nodeInclusionBehaviour.setValue(invertSelection
                ? NodeInclusionBehaviour.EXCLUDE_SELECTED
                : NodeInclusionBehaviour.INCLUDE_SELECTED);
    }

    /// @param list The nodes to tick.
    public void setList(final IsWidget list) {
        this.list.setWidget(list.asWidget());
    }

    /// @param onInvertSelectionChange Called when the inclusion behaviour changes.
    public void setOnInvertSelectionChange(final Runnable onInvertSelectionChange) {
        this.onInvertSelectionChange = onInvertSelectionChange;
    }

    private void onNodeInclusionBehaviourChange(final ValueChangeEvent<NodeInclusionBehaviour> event) {
        onInvertSelectionChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, NodeGroupEdit> {

    }
}
