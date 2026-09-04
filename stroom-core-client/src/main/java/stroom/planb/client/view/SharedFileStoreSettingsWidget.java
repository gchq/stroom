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

package stroom.planb.client.view;

import stroom.planb.shared.SharedFileStoreSettings;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

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
public class SharedFileStoreSettingsWidget
        extends AbstractSettingsWidget
        implements SharedFileStoreSettingsView {

    private final Widget widget;

    @UiField
    TextBox sharedPath;
    @UiField
    ValueSpinner shardCount;

    private boolean readOnly;
    private boolean locked;

    @Inject
    public SharedFileStoreSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        shardCount.setMin(1);
        shardCount.setMax(999);
        shardCount.setValue(1);
    }

    @Override
    Widget asWidget() {
        return widget;
    }

    @Override
    public SharedFileStoreSettings getSharedFileStore() {
        return new SharedFileStoreSettings(
                shardCount.getIntValue(),
                sharedPath.getValue());
    }

    @Override
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

    @Override
    public void setSharedFileStoreLocked(final boolean locked) {
        this.locked = locked;
        updateStates();
    }

    private void updateStates() {
        final boolean editable = !readOnly;
        sharedPath.setEnabled(editable && !locked);
        shardCount.setEnabled(editable && !locked);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
    }

    @UiHandler("sharedPath")
    public void onSharedPath(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("shardCount")
    public void onShardCount(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, SharedFileStoreSettingsWidget> {

    }
}
