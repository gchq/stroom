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

import stroom.planb.shared.SnapshotSettings;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class SnapshotSettingsWidget extends AbstractSettingsWidget implements SnapshotSettingsView {

    private final Widget widget;

    @UiField
    CustomCheckBox useSnapshotsForLookup;
    @UiField
    CustomCheckBox useSnapshotsForGet;
    @UiField
    CustomCheckBox useSnapshotsForQuery;

    @Inject
    public SnapshotSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public SnapshotSettings getSnapshotSettings() {
        return new SnapshotSettings(
                useSnapshotsForLookup.getValue(),
                useSnapshotsForGet.getValue(),
                useSnapshotsForQuery.getValue());
    }

    @Override
    public void setSnapshotSettings(final SnapshotSettings snapshotSettings) {
        if (snapshotSettings != null) {
            final boolean lookupVal = !shardingEnabled && snapshotSettings.isUseSnapshotsForLookup();
            final boolean getVal = !shardingEnabled && snapshotSettings.isUseSnapshotsForGet();
            final boolean queryVal = !shardingEnabled && snapshotSettings.isUseSnapshotsForQuery();
            this.useSnapshotsForLookup.setValue(lookupVal);
            this.useSnapshotsForGet.setValue(getVal);
            this.useSnapshotsForQuery.setValue(queryVal);
        }
    }

    private boolean readOnly;
    private boolean shardingEnabled;

    public void setShardingEnabled(final boolean shardingEnabled) {
        this.shardingEnabled = shardingEnabled;
        updateStates();
    }

    private void updateStates() {
        final boolean enabled = !readOnly && !shardingEnabled;
        useSnapshotsForLookup.setEnabled(enabled);
        useSnapshotsForGet.setEnabled(enabled);
        useSnapshotsForQuery.setEnabled(enabled);

        if (shardingEnabled) {
            useSnapshotsForLookup.setValue(false);
            useSnapshotsForGet.setValue(false);
            useSnapshotsForQuery.setValue(false);
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
    }


    @UiHandler("useSnapshotsForLookup")
    public void onUseSnapshotsForLookup(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("useSnapshotsForGet")
    public void onUseSnapshotsForGet(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("useSnapshotsForQuery")
    public void onUseSnapshotsForQuery(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, SnapshotSettingsWidget> {

    }
}
