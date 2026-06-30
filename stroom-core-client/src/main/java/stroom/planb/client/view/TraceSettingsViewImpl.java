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

import stroom.document.client.event.ChangeUiHandlers;
import stroom.planb.client.presenter.TraceSettingsPresenter.TraceSettingsView;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.RetentionSettings;
import stroom.planb.shared.SnapshotSettings;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class TraceSettingsViewImpl
        extends ViewWithUiHandlers<ChangeUiHandlers>
        implements TraceSettingsView {

    private final Widget widget;
    private final GeneralSettingsWidget generalSettingsWidget;
    private final SharedFileStoreSettingsWidget shardingSettingsWidget;
    private final SnapshotSettingsWidget snapshotSettingsWidget;
    private final RetentionSettingsWidget retentionSettingsWidget;
    private final ArchivalSettingsWidget archivalSettingsWidget;

    @UiField
    SettingsGroup generalPanel;
    @UiField
    SettingsGroup snapshotPanel;
    @UiField
    SettingsGroup retentionPanel;
    @UiField
    SettingsGroup shardingArchivingPanel;

    @Inject
    public TraceSettingsViewImpl(final Binder binder,
                                 final GeneralSettingsWidget generalSettingsWidget,
                                 final SharedFileStoreSettingsWidget shardingSettingsWidget,
                                 final SnapshotSettingsWidget snapshotSettingsWidget,
                                 final RetentionSettingsWidget retentionSettingsWidget,
                                 final ArchivalSettingsWidget archivalSettingsWidget) {
        widget = binder.createAndBindUi(this);
        this.generalSettingsWidget = generalSettingsWidget;
        this.shardingSettingsWidget = shardingSettingsWidget;
        this.snapshotSettingsWidget = snapshotSettingsWidget;
        this.retentionSettingsWidget = retentionSettingsWidget;
        this.archivalSettingsWidget = archivalSettingsWidget;
        generalPanel.add(generalSettingsWidget.asWidget());
        snapshotPanel.add(snapshotSettingsWidget.asWidget());
        retentionPanel.add(retentionSettingsWidget.asWidget());

        final FlowPanel shardingArchivingContent = new FlowPanel();
        shardingArchivingContent.add(shardingSettingsWidget.asWidget());
        shardingArchivingContent.add(archivalSettingsWidget.asWidget());
        shardingArchivingPanel.add(shardingArchivingContent);
    }

    @Override
    public void setUiHandlers(final ChangeUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        generalSettingsWidget.setUiHandlers(uiHandlers);
        // When the Enable Shared File Store checkbox or path changes, propagate the
        // enable state to the archival widget so the Archiving Enabled checkbox
        // is gated on the same condition.
        shardingSettingsWidget.setUiHandlers(() -> {
            archivalSettingsWidget.setHasSharedPath(
                    shardingSettingsWidget.isEnableSharedFileStore());
            if (uiHandlers != null) {
                uiHandlers.onChange();
            }
        });
        snapshotSettingsWidget.setUiHandlers(uiHandlers);
        retentionSettingsWidget.setUiHandlers(uiHandlers);
        archivalSettingsWidget.setUiHandlers(uiHandlers);
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
    public String getSharedPath() {
        return shardingSettingsWidget.getSharedPath();
    }

    @Override
    public void setSharedPath(final String sharedPath) {
        shardingSettingsWidget.setSharedPath(sharedPath);
    }

    @Override
    public boolean isEnableSharedFileStore() {
        return shardingSettingsWidget.isEnableSharedFileStore();
    }

    @Override
    public void setEnableSharedFileStore(final boolean enable) {
        shardingSettingsWidget.setEnableSharedFileStore(enable);
        // Archiving is only available when the shared file store is enabled —
        // propagate immediately so setArchival() doesn't need to worry about ordering.
        archivalSettingsWidget.setHasSharedPath(enable);
    }

    @Override
    public void setSharedFileStorePathLocked(final boolean locked) {
        shardingSettingsWidget.setSharedFileStorePathLocked(locked);
    }

    @Override
    public int getShardCount() {
        return shardingSettingsWidget.getShardCount();
    }

    @Override
    public void setShardCount(final int count) {
        shardingSettingsWidget.setShardCount(count);
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
    public ArchivalSettings getArchival() {
        return archivalSettingsWidget.getArchival();
    }

    @Override
    public void setArchival(final ArchivalSettings archival) {
        archivalSettingsWidget.setArchival(archival);
    }

    @Override
    public void setShardingEnabled(final boolean shardingEnabled) {
        generalSettingsWidget.setShardingEnabled(shardingEnabled);
        retentionSettingsWidget.setShardingEnabled(shardingEnabled);
        snapshotSettingsWidget.setShardingEnabled(shardingEnabled);
    }

    @Override
    public void setShardCountLocked(final boolean locked) {
        shardingSettingsWidget.setShardCountLocked(locked);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        generalSettingsWidget.onReadOnly(readOnly);
        shardingSettingsWidget.onReadOnly(readOnly);
        snapshotSettingsWidget.onReadOnly(readOnly);
        retentionSettingsWidget.onReadOnly(readOnly);
        archivalSettingsWidget.onReadOnly(readOnly);
    }

    public interface Binder extends UiBinder<Widget, TraceSettingsViewImpl> {

    }
}
