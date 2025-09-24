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

import stroom.planb.client.presenter.MetricSettingsPresenter.MetricSettingsView;
import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.client.presenter.TraceSettingsPresenter.TraceSettingsView;
import stroom.planb.shared.MetricKeySchema;
import stroom.planb.shared.MetricValueSchema;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SnapshotSettings;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TraceSettingsViewImpl
        extends ViewWithUiHandlers<PlanBSettingsUiHandlers>
        implements TraceSettingsView {

    private final Widget widget;
    private final GeneralSettingsWidget generalSettingsWidget;
    private final SnapshotSettingsWidget snapshotSettingsWidget;
    private final RetentionSettingsWidget retentionSettingsWidget;

    @UiField
    SettingsGroup generalPanel;
    @UiField
    SettingsGroup snapshotPanel;
    @UiField
    SettingsGroup retentionPanel;

    @Inject
    public TraceSettingsViewImpl(final Binder binder,
                                 final GeneralSettingsWidget generalSettingsWidget,
                                 final SnapshotSettingsWidget snapshotSettingsWidget,
                                 final RetentionSettingsWidget retentionSettingsWidget) {
        widget = binder.createAndBindUi(this);
        this.generalSettingsWidget = generalSettingsWidget;
        this.snapshotSettingsWidget = snapshotSettingsWidget;
        this.retentionSettingsWidget = retentionSettingsWidget;
        generalPanel.add(generalSettingsWidget.asWidget());
        snapshotPanel.add(snapshotSettingsWidget.asWidget());
        retentionPanel.add(retentionSettingsWidget.asWidget());
    }

    @Override
    public void setUiHandlers(final PlanBSettingsUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        generalSettingsWidget.setUiHandlers(uiHandlers);
        snapshotSettingsWidget.setUiHandlers(uiHandlers);
        retentionSettingsWidget.setUiHandlers(uiHandlers);
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
    public void onReadOnly(final boolean readOnly) {
        generalSettingsWidget.onReadOnly(readOnly);
        snapshotSettingsWidget.onReadOnly(readOnly);
        retentionSettingsWidget.onReadOnly(readOnly);
    }

    public interface Binder extends UiBinder<Widget, TraceSettingsViewImpl> {

    }
}
