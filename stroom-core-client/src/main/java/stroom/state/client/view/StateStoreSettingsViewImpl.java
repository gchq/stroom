/*
 * Copyright 2016-2025 Crown Copyright
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

package stroom.state.client.view;

import stroom.item.client.SelectionBox;
import stroom.state.client.presenter.StateStoreSettingsPresenter.StateStoreSettingsView;
import stroom.state.client.presenter.StateStoreSettingsUiHandlers;
import stroom.state.shared.StateType;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.SimplePanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.View;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class StateStoreSettingsViewImpl
        extends ViewWithUiHandlers<StateStoreSettingsUiHandlers>
        implements StateStoreSettingsView {

    private final Widget widget;

    @UiField
    SimplePanel scyllaDBConnection;
    @UiField
    SelectionBox<StateType> stateType;
    @UiField
    FormGroup condensePanel;
    @UiField
    CustomCheckBox condense;
    @UiField
    FormGroup condenseAgePanel;
    @UiField
    ValueSpinner condenseAge;
    @UiField
    SelectionBox<TimeUnit> condenseTimeUnit;
    @UiField
    CustomCheckBox retainForever;
    @UiField
    FormGroup retainAgePanel;
    @UiField
    ValueSpinner retainAge;
    @UiField
    SelectionBox<TimeUnit> retainTimeUnit;

    private boolean readOnly;

    @Inject
    public StateStoreSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        stateType.addItem(StateType.STATE);
        stateType.addItem(StateType.RANGED_STATE);
        stateType.addItem(StateType.TEMPORAL_STATE);
        stateType.addItem(StateType.TEMPORAL_RANGED_STATE);
        stateType.addItem(StateType.SESSION);
        stateType.setValue(StateType.TEMPORAL_STATE);
        updateStateType();

        condenseAge.setMin(1);
        condenseAge.setMax(9999);
        condenseAge.setValue(1);

        condenseTimeUnit.addItem(TimeUnit.DAYS);
        condenseTimeUnit.addItem(TimeUnit.WEEKS);
        condenseTimeUnit.addItem(TimeUnit.MONTHS);
        condenseTimeUnit.addItem(TimeUnit.YEARS);
        condenseTimeUnit.setValue(TimeUnit.YEARS);

        retainAge.setMin(1);
        retainAge.setMax(9999);
        retainAge.setValue(1);

        retainTimeUnit.addItem(TimeUnit.DAYS);
        retainTimeUnit.addItem(TimeUnit.WEEKS);
        retainTimeUnit.addItem(TimeUnit.MONTHS);
        retainTimeUnit.addItem(TimeUnit.YEARS);
        retainTimeUnit.setValue(TimeUnit.YEARS);

        setCondenseEnabled(this.condense.getValue());
        setRetainEnabled(!retainForever.getValue());
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public void setClusterView(final View view) {
        scyllaDBConnection.setWidget(view.asWidget());
    }

    @Override
    public StateType getStateType() {
        return stateType.getValue();
    }

    @Override
    public void setStateType(final StateType stateType) {
        this.stateType.setValue(stateType);
        updateStateType();
    }

    private void updateStateType() {
        condensePanel.setVisible(
                StateType.TEMPORAL_STATE.equals(stateType.getValue()) ||
                        StateType.TEMPORAL_RANGED_STATE.equals(stateType.getValue()) ||
                        StateType.SESSION.equals(stateType.getValue()));
    }

    @Override
    public boolean isCondense() {
        return condense.getValue();
    }

    @Override
    public void setCondense(final boolean condense) {
        this.condense.setValue(condense);
        setCondenseEnabled(condense);
    }

    @Override
    public int getCondenseAge() {
        return condenseAge.getIntValue();
    }

    @Override
    public void setCondenseAge(final int age) {
        this.condenseAge.setValue(age);
    }

    @Override
    public TimeUnit getCondenseTimeUnit() {
        return condenseTimeUnit.getValue();
    }

    @Override
    public void setCondenseTimeUnit(final TimeUnit condenseTimeUnit) {
        this.condenseTimeUnit.setValue(condenseTimeUnit);
    }

    @Override
    public boolean isRetainForever() {
        return retainForever.getValue();
    }

    @Override
    public void setRetainForever(final boolean retainForever) {
        this.retainForever.setValue(retainForever);
        setRetainEnabled(!retainForever);
    }

    @Override
    public int getRetainAge() {
        return retainAge.getIntValue();
    }

    @Override
    public void setRetainAge(final int age) {
        this.retainAge.setValue(age);
    }

    @Override
    public TimeUnit getRetainTimeUnit() {
        return retainTimeUnit.getValue();
    }

    @Override
    public void setRetainTimeUnit(final TimeUnit retainTimeUnit) {
        this.retainTimeUnit.setValue(retainTimeUnit);
    }

    private void setCondenseEnabled(final boolean enabled) {
        if (!readOnly) {
            if (enabled) {
                condenseAgePanel.getElement().getStyle().setOpacity(1);
            } else {
                condenseAgePanel.getElement().getStyle().setOpacity(0.5);
            }
            condenseAge.setEnabled(enabled);
            condenseTimeUnit.setEnabled(enabled);
        }
    }

    private void setRetainEnabled(final boolean enabled) {
        if (!readOnly) {
            if (enabled) {
                retainAgePanel.getElement().getStyle().setOpacity(1);
            } else {
                retainAgePanel.getElement().getStyle().setOpacity(0.5);
            }
            retainAge.setEnabled(enabled);
            retainTimeUnit.setEnabled(enabled);
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        stateType.setEnabled(!readOnly);
        condense.setEnabled(!readOnly);
        condenseAge.setEnabled(!readOnly);
        condenseTimeUnit.setEnabled(!readOnly);
        retainForever.setEnabled(!readOnly);
        retainAge.setEnabled(!readOnly);
        retainTimeUnit.setEnabled(!readOnly);
    }

    @UiHandler("stateType")
    public void onStateType(final ValueChangeEvent<StateType> event) {
        updateStateType();
        getUiHandlers().onChange();
    }

    @UiHandler("condense")
    public void onCondense(final ValueChangeEvent<Boolean> event) {
        setCondenseEnabled(isCondense());
        getUiHandlers().onChange();
    }

    @UiHandler("condenseAge")
    public void onCondenseAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("condenseTimeUnit")
    public void onCondenseTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("retainForever")
    public void onRetainForever(final ValueChangeEvent<Boolean> event) {
        setRetainEnabled(!isRetainForever());
        getUiHandlers().onChange();
    }

    @UiHandler("retainAge")
    public void onRetainAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("retainTimeUnit")
    public void onRetainTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, StateStoreSettingsViewImpl> {

    }
}
