/*
 * Copyright 2016-2026 Crown Copyright
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

import stroom.planb.shared.SharedFileStoreSettings;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.TextBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;

/**
 * Where a store's data lives on the shared filesystem and how many ways it is split. Both decide
 * where written data already is, so both are fixed once there is any.
 *
 * <p>Holds nothing about bucketing or publishing: those differ per store type, so they belong to
 * that store type's own settings form.
 *
 * <p>The path is mandatory. A store type that offers this widget has no other backing store to fall
 * back to, so there is nothing to switch off; an empty path is a save-blocking error rather than a
 * mode.
 */
public class SharedFileStoreSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    TextBox sharedPath;
    @UiField
    ValueSpinner shardCount;

    private boolean locked;

    /// Makes the settings, at their defaults.
    public SharedFileStoreSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));

        shardCount.setValue(1);
    }

    public SharedFileStoreSettings getSharedFileStore() {
        return new SharedFileStoreSettings(
                shardCount.getIntValue(),
                sharedPath.getValue());
    }

    public void setSharedFileStore(final SharedFileStoreSettings settings) {
        final SharedFileStoreSettings sharedFileStore = settings != null
                ? settings
                : new SharedFileStoreSettings(1, null);
        sharedPath.setValue(sharedFileStore.getSharedPath() == null
                ? ""
                : sharedFileStore.getSharedPath());
        shardCount.setValue(Math.max(1, sharedFileStore.getShardCount()));
        updateStates();
    }

    /// Fixes the path and shard count, for a store that already holds data at that location.
    ///
    /// @param locked Whether the path and shard count can't be changed.
    public void setSharedFileStoreLocked(final boolean locked) {
        this.locked = locked;
        updateStates();
    }

    private void updateStates() {
        // Locking is independent of read only, which onReadOnlyChange() handles
        sharedPath.setEnabled(!locked);
        shardCount.setEnabled(!locked);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        sharedPath.setReadOnly(readOnly);
        shardCount.setReadOnly(readOnly);
    }

    @UiHandler("sharedPath")
    public void onSharedPath(final ValueChangeEvent<String> event) {
        changed();
    }

    @UiHandler("shardCount")
    public void onShardCount(final ValueChangeEvent<Long> event) {
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


    interface Binder extends UiBinder<Widget, SharedFileStoreSettingsForm> {

    }
}
