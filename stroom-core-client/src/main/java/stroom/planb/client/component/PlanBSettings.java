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

package stroom.planb.client.component;

import stroom.planb.shared.StateType;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.selection.client.SelectionBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.IsWidget;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;

/// A Plan B store's Settings tab: the type of state it holds above the settings of that type
/// (shown here by its controller, which makes them again when the type changes).
public class PlanBSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    FormGroup stateTypeGroup;
    @UiField
    SelectionBox<StateType> stateType;
    @UiField
    SimplePanel settings;

    private Runnable onStateTypeChange = () -> {
    };

    /// Makes the tab, for a temporal state store.
    public PlanBSettings() {
        initWidget(BINDER.createAndBindUi(this));

        stateType.addItem(StateType.STATE);
        stateType.addItem(StateType.RANGED_STATE);
        stateType.addItem(StateType.TEMPORAL_STATE);
        stateType.addItem(StateType.TEMPORAL_RANGED_STATE);
        stateType.addItem(StateType.SESSION);
        stateType.addItem(StateType.HISTOGRAM);
        stateType.addItem(StateType.METRIC);
        stateType.setValue(StateType.TEMPORAL_STATE);
        stateType.addValueChangeHandler(e -> onStateTypeChange.run());
    }

    /// @return The type of state chosen.
    public StateType getStateType() {
        return stateType.getValue();
    }

    /// @param stateType The type of state.
    public void setStateType(final StateType stateType) {
        this.stateType.setValue(stateType);
    }

    /// @param visible Whether the state type can be seen (and so changed); not for a type that is
    ///                fixed, e.g. a Traces store's.
    public void setStateTypeVisible(final boolean visible) {
        stateTypeGroup.setVisible(visible);
    }

    /// Shows the settings of the store's type.
    ///
    /// @param editor The settings.
    public void setSettingsEditor(final IsWidget editor) {
        settings.setWidget(editor);
    }

    /// @param onStateTypeChange Run when the user chooses another type of state.
    public void setOnStateTypeChange(final Runnable onStateTypeChange) {
        this.onStateTypeChange = onStateTypeChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        stateType.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, PlanBSettings> {

    }
}
