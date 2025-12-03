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
            this.useSnapshotsForLookup.setValue(snapshotSettings.isUseSnapshotsForLookup());
            this.useSnapshotsForGet.setValue(snapshotSettings.isUseSnapshotsForGet());
            this.useSnapshotsForQuery.setValue(snapshotSettings.isUseSnapshotsForQuery());
        }
    }

    public void onReadOnly(final boolean readOnly) {
        useSnapshotsForLookup.setEnabled(!readOnly);
        useSnapshotsForGet.setEnabled(!readOnly);
        useSnapshotsForQuery.setEnabled(!readOnly);
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
