/*
 * Copyright 2025 Crown Copyright
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

import stroom.planb.shared.SnapshotSettings;
import stroom.widget.component.client.Component;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/**
 * Whether reads come from snapshots pushed to other nodes. Only a store served over HTTP has
 * snapshots, so this has no place in a trace store — see
 * {@link stroom.planb.shared.AbstractHttpStoreSettings}.
 */
public class SnapshotSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    TickBox useSnapshotsForLookup;
    @UiField
    TickBox useSnapshotsForGet;
    @UiField
    TickBox useSnapshotsForQuery;

    /// Makes the settings, at their defaults.
    public SnapshotSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
    }

    public SnapshotSettings getSnapshotSettings() {
        return new SnapshotSettings(
                useSnapshotsForLookup.getValue(),
                useSnapshotsForGet.getValue(),
                useSnapshotsForQuery.getValue());
    }

    public void setSnapshotSettings(final SnapshotSettings snapshotSettings) {
        if (snapshotSettings != null) {
            useSnapshotsForLookup.setValue(snapshotSettings.isUseSnapshotsForLookup());
            useSnapshotsForGet.setValue(snapshotSettings.isUseSnapshotsForGet());
            useSnapshotsForQuery.setValue(snapshotSettings.isUseSnapshotsForQuery());
        }
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        useSnapshotsForLookup.setReadOnly(readOnly);
        useSnapshotsForGet.setReadOnly(readOnly);
        useSnapshotsForQuery.setReadOnly(readOnly);
    }

    @UiHandler("useSnapshotsForLookup")
    public void onUseSnapshotsForLookup(final ValueChangeEvent<Boolean> event) {
        changed();
    }

    @UiHandler("useSnapshotsForGet")
    public void onUseSnapshotsForGet(final ValueChangeEvent<Boolean> event) {
        changed();
    }

    @UiHandler("useSnapshotsForQuery")
    public void onUseSnapshotsForQuery(final ValueChangeEvent<Boolean> event) {
        changed();
    }

    /// Sets what is told when the user changes a setting.
    ///
    /// @param onChange Run each time, or null for nothing.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange != null
                ? onChange
                : () -> {
                };
    }

    // Says that the user changed a setting
    private void changed() {
        onChange.run();
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, SnapshotSettingsForm> {

    }
}
