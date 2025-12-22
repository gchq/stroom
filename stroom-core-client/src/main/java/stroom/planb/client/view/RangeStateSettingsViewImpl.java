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

package stroom.planb.client.view;

import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.client.presenter.RangeStateSettingsPresenter.RangeStateSettingsView;
import stroom.planb.shared.RangeKeySchema;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SnapshotSettings;
import stroom.planb.shared.StateValueSchema;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class RangeStateSettingsViewImpl
        extends ViewWithUiHandlers<PlanBSettingsUiHandlers>
        implements RangeStateSettingsView {

    private final Widget widget;
    private final GeneralSettingsWidget generalSettingsWidget;
    private final SnapshotSettingsWidget snapshotSettingsWidget;
    private final RetentionSettingsWidget retentionSettingsWidget;
    private final RangeKeySchemaSettingsWidget rangeKeySchemaSettingsWidget;
    private final StateValueSchemaSettingsWidget stateValueSchemaSettingsWidget;

    @UiField
    SettingsGroup generalPanel;
    @UiField
    SettingsGroup snapshotPanel;
    @UiField
    SettingsGroup retentionPanel;
    @UiField
    SettingsGroup keySchemaPanel;
    @UiField
    SettingsGroup valueSchemaPanel;

    @Inject
    public RangeStateSettingsViewImpl(final Binder binder,
                                      final GeneralSettingsWidget generalSettingsWidget,
                                      final SnapshotSettingsWidget snapshotSettingsWidget,
                                      final RetentionSettingsWidget retentionSettingsWidget,
                                      final RangeKeySchemaSettingsWidget rangeKeySchemaSettingsWidget,
                                      final StateValueSchemaSettingsWidget stateValueSchemaSettingsWidget) {
        widget = binder.createAndBindUi(this);
        this.generalSettingsWidget = generalSettingsWidget;
        this.snapshotSettingsWidget = snapshotSettingsWidget;
        this.retentionSettingsWidget = retentionSettingsWidget;
        this.rangeKeySchemaSettingsWidget = rangeKeySchemaSettingsWidget;
        this.stateValueSchemaSettingsWidget = stateValueSchemaSettingsWidget;
        generalPanel.add(generalSettingsWidget.asWidget());
        snapshotPanel.add(snapshotSettingsWidget.asWidget());
        retentionPanel.add(retentionSettingsWidget.asWidget());
        keySchemaPanel.add(rangeKeySchemaSettingsWidget.asWidget());
        valueSchemaPanel.add(stateValueSchemaSettingsWidget.asWidget());
    }

    @Override
    public void setUiHandlers(final PlanBSettingsUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        generalSettingsWidget.setUiHandlers(uiHandlers);
        snapshotSettingsWidget.setUiHandlers(uiHandlers);
        retentionSettingsWidget.setUiHandlers(uiHandlers);
        rangeKeySchemaSettingsWidget.setUiHandlers(uiHandlers);
        stateValueSchemaSettingsWidget.setUiHandlers(uiHandlers);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Long getMaxStoreSize() {
        return generalSettingsWidget.getMaxStoreSize();
    }

    @Override
    public void setMaxStoreSize(final Long maxStoreSize) {
        generalSettingsWidget.setMaxStoreSize(maxStoreSize);
    }

    @Override
    public Boolean getSynchroniseMerge() {
        return generalSettingsWidget.getSynchroniseMerge();
    }

    @Override
    public void setSynchroniseMerge(final Boolean synchroniseMerge) {
        generalSettingsWidget.setSynchroniseMerge(synchroniseMerge);
    }

    @Override
    public Boolean getOverwrite() {
        return generalSettingsWidget.getOverwrite();
    }

    @Override
    public void setOverwrite(final Boolean overwrite) {
        generalSettingsWidget.setOverwrite(overwrite);
    }

    @Override
    public RetentionSettings getRetention() {
        return retentionSettingsWidget.getRetention();
    }

    @Override
    public void setRetention(final RetentionSettings retention) {
        retentionSettingsWidget.setRetention(retention);
    }

    @Override
    public SnapshotSettings getSnapshotSettings() {
        return snapshotSettingsWidget.getSnapshotSettings();
    }

    @Override
    public void setSnapshotSettings(final SnapshotSettings snapshotSettings) {
        snapshotSettingsWidget.setSnapshotSettings(snapshotSettings);
    }

    @Override
    public RangeKeySchema getKeySchema() {
        return rangeKeySchemaSettingsWidget.getKeySchema();
    }

    @Override
    public void setKeySchema(final RangeKeySchema keySchema) {
        rangeKeySchemaSettingsWidget.setKeySchema(keySchema);
    }

    @Override
    public StateValueSchema getValueSchema() {
        return stateValueSchemaSettingsWidget.getValueSchema();
    }

    @Override
    public void setValueSchema(final StateValueSchema valueSchema) {
        stateValueSchemaSettingsWidget.setValueSchema(valueSchema);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        generalSettingsWidget.onReadOnly(readOnly);
        snapshotSettingsWidget.onReadOnly(readOnly);
        retentionSettingsWidget.onReadOnly(readOnly);
        rangeKeySchemaSettingsWidget.onReadOnly(readOnly);
        stateValueSchemaSettingsWidget.onReadOnly(readOnly);
    }

    public interface Binder extends UiBinder<Widget, RangeStateSettingsViewImpl> {

    }
}
