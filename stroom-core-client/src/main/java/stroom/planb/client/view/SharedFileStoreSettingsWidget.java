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

import stroom.document.client.event.ChangeUiHandlers;
import stroom.widget.form.client.FormGroup;
import stroom.widget.tickbox.client.view.CustomCheckBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/**
 * Widget that owns the Enable Shared File Store checkbox, Path field and Shard Count spinner.
 *
 * <p>The checkbox is the single gate: when unchecked the path and shard count are disabled
 * and the shard count is reset to 0. When checked the path is enabled and the shard count
 * minimum becomes 1 (auto-set to 1 if it was 0).
 */
public class SharedFileStoreSettingsWidget extends AbstractSettingsWidget implements SharedFileStoreView {

    private final Widget widget;

    @UiField
    CustomCheckBox enableSharedFileStore;
    @UiField
    FormGroup sharedPathFormGroup;
    @UiField
    TextBox sharedPath;
    @UiField
    ValueSpinner shardCount;

    private boolean readOnly;
    private boolean shardCountLocked;
    private boolean sharedPathLocked;

    @Inject
    public SharedFileStoreSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        shardCount.setMin(0);
    }

    @Override
    public void setUiHandlers(final ChangeUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    // -----------------------------------------------------------------------
    // SharedFileStoreView — Enable Shared File Store
    // -----------------------------------------------------------------------

    @Override
    public boolean isEnableSharedFileStore() {
        return enableSharedFileStore.getValue();
    }

    @Override
    public void setEnableSharedFileStore(final boolean enable) {
        enableSharedFileStore.setValue(enable);
        updateStates();
    }

    // -----------------------------------------------------------------------
    // SharedFileStoreView — Shared Path
    // -----------------------------------------------------------------------

    @Override
    public String getSharedPath() {
        return isEnableSharedFileStore() ? sharedPath.getValue() : null;
    }

    @Override
    public void setSharedPath(final String path) {
        sharedPath.setValue(path == null ? "" : path);
    }

    @Override
    public void setSharedFileStorePathLocked(final boolean locked) {
        this.sharedPathLocked = locked;
        updateStates();
    }

    // -----------------------------------------------------------------------
    // SharedFileStoreView — Shard Count
    // -----------------------------------------------------------------------

    @Override
    public int getShardCount() {
        return shardCount.getIntValue();
    }

    @Override
    public void setShardCount(final int count) {
        this.shardCount.setValue(count);
        updateStates();
    }

    public void setShardCountLocked(final boolean locked) {
        this.shardCountLocked = locked;
        updateStates();
    }

    // -----------------------------------------------------------------------
    // State management
    // -----------------------------------------------------------------------

    private void updateStates() {
        final boolean enabled = isEnableSharedFileStore();
        // Disabled when read-only OR data has already been written to shards (cannot change backing store).
        enableSharedFileStore.setEnabled(!readOnly && !shardCountLocked);
        sharedPathFormGroup.getElement().getStyle().setOpacity(enabled ? 1 : 0.5);
        sharedPath.setEnabled(!readOnly && enabled && !sharedPathLocked);
        if (!enabled) {
            shardCount.setValue(1L);
            shardCount.setMin(1);
            shardCount.setEnabled(false);
        } else if (shardCountLocked) {
            shardCount.setMin(1);
            shardCount.setEnabled(false);
        } else {
            shardCount.setMin(1);
            if (shardCount.getIntValue() < 1) {
                shardCount.setValue(1L);
            }
            shardCount.setEnabled(!readOnly);
        }
    }

    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
    }

    // -----------------------------------------------------------------------
    // UiHandlers
    // -----------------------------------------------------------------------

    @UiHandler("enableSharedFileStore")
    public void onEnableSharedFileStore(final ValueChangeEvent<Boolean> event) {
        if (readOnly) {
            return;
        }
        updateStates();
        getUiHandlers().onChange();
    }

    @UiHandler("sharedPath")
    public void onSharedPath(final ValueChangeEvent<String> event) {
        if (readOnly) {
            return;
        }
        getUiHandlers().onChange();
    }

    @UiHandler("shardCount")
    public void onShardCount(final ValueChangeEvent<Long> event) {
        if (readOnly) {
            return;
        }
        updateStates();
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, SharedFileStoreSettingsWidget> {

    }
}
