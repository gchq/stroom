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

package stroom.processor.client.component;

import stroom.node.shared.NodeGroup;
import stroom.query.api.UserTimeZone;
import stroom.widget.component.client.Component;
import stroom.widget.datepicker.client.TimeZoneBox;
import stroom.widget.form.client.TextBox;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.selection.client.SelectionListModel;
import stroom.widget.selection.client.SimpleSelectionItemWrapper;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A processor profile: its name, node group, processing schedules and time zone.
public class ProcessorProfileEditor extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    TextBox name;
    @UiField
    SelectionBox<NodeGroup> nodeGroup;
    @UiField
    ProfilePeriodList list;
    @UiField
    TimeZoneBox timeZone;

    /// Makes an empty form, with no node groups or schedules until they are given.
    public ProcessorProfileEditor() {
        initWidget(BINDER.createAndBindUi(this));
    }

    /// Puts the focus on the name.
    public void focus() {
        name.setFocus(true);
    }

    /// @return The profile's name.
    public String getName() {
        return name.getText();
    }

    /// @param name The profile's name.
    public void setName(final String name) {
        this.name.setText(name);
    }

    /// @param model The node groups to choose from.
    public void setNodeGroupModel(final SelectionListModel<NodeGroup, SimpleSelectionItemWrapper<NodeGroup>> model) {
        nodeGroup.setModel(model);
    }

    /// @return The node group chosen, or `null` for none.
    public NodeGroup getNodeGroup() {
        return nodeGroup.getValue();
    }

    /// @param nodeGroup The node group chosen, or `null` for none.
    public void setNodeGroup(final NodeGroup nodeGroup) {
        this.nodeGroup.setValue(nodeGroup);
    }

    /// @return The processing schedules, which its controller attaches the list's controller to.
    public ProfilePeriodList getList() {
        return list;
    }

    /// @return The time zone the schedules are in.
    public UserTimeZone getUserTimeZone() {
        return timeZone.getUserTimeZone();
    }

    /// @param userTimeZone The time zone the schedules are in.
    public void setUserTimeZone(final UserTimeZone userTimeZone) {
        timeZone.setUserTimeZone(userTimeZone);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, ProcessorProfileEditor> {

    }
}
