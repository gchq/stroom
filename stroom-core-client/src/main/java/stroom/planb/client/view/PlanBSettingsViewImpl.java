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

package stroom.planb.client.view;

import stroom.item.client.SelectionBox;
import stroom.planb.client.presenter.PlanBSettingsPresenter.PlanBSettingsView;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.shared.StateType;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class PlanBSettingsViewImpl
        extends ViewWithUiHandlers<PlanBSettingsUiHandlers>
        implements PlanBSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<StateType> stateType;
    @UiField
    SimplePanel settings;

    @Inject
    public PlanBSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        stateType.addItem(StateType.STATE);
        stateType.addItem(StateType.RANGED_STATE);
        stateType.addItem(StateType.TEMPORAL_STATE);
        stateType.addItem(StateType.TEMPORAL_RANGED_STATE);
        stateType.addItem(StateType.SESSION);
        stateType.addItem(StateType.HISTOGRAM);
        stateType.addItem(StateType.METRIC);
        stateType.addItem(StateType.TRACE);
        stateType.setValue(StateType.TEMPORAL_STATE);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public StateType getStateType() {
        return stateType.getValue();
    }

    @Override
    public void setStateType(final StateType stateType) {
        this.stateType.setValue(stateType);
    }

    @Override
    public void setSettingsView(final View view) {
        settings.setWidget(view.asWidget());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        stateType.setEnabled(!readOnly);
    }

    @UiHandler("stateType")
    public void onStateType(final ValueChangeEvent<StateType> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, PlanBSettingsViewImpl> {

    }
}
