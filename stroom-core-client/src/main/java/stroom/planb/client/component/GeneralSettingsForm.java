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

import stroom.planb.shared.AbstractPlanBSettings;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.component.client.Component;
import stroom.widget.form.client.FormGroup;
import stroom.widget.form.client.TextBox;
import stroom.widget.tickbox.client.TickBox;

import com.google.gwt.core.client.GWT;
import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

public class GeneralSettingsForm extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    private Runnable onChange = () -> {
    };

    @UiField
    FormGroup maxStoreSizePanel;
    @UiField
    TextBox maxStoreSize;
    @UiField
    FormGroup synchroniseMergePanel;
    @UiField
    TickBox synchroniseMerge;
    @UiField
    FormGroup overwritePanel;
    @UiField
    TickBox overwrite;

    private boolean writeOptionsVisible = true;
    // The size last set, kept while the text entered isn't a size
    private Long maxStoreSizeSet = AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE;

    /// Makes the settings, at their defaults.
    public GeneralSettingsForm() {
        initWidget(BINDER.createAndBindUi(this));
        maxStoreSize.addValueChangeHandler(this::onMaxStoreSize);
        maxStoreSize.setInvalidReason(text -> text.trim().isEmpty() || parseSize(text.trim()) != null
                ? null
                : "Must be a size, e.g. 10 GiB");
        synchroniseMerge.setOnChange(value -> onSynchroniseMerge());
        overwrite.setOnChange(value -> onOverwrite());
        setOverwrite(true);
    }

    /// @return The size entered: the default if the box is blank or, while its text isn't a size
    /// (when Save refuses, so it is never saved), the size last set. Before, such text was replaced
    /// by the default, 10 GiB, and saved.
    public Long getMaxStoreSize() {
        final String text = maxStoreSize.getValue().trim();
        if (text.isEmpty()) {
            return AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE;
        }
        final Long size = parseSize(text);
        return size == null
                ? maxStoreSizeSet
                : size;
    }

    /// @param maxStoreSize The size to show, or null for the default.
    public void setMaxStoreSize(final Long maxStoreSize) {
        maxStoreSizeSet = maxStoreSize == null
                ? AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE
                : maxStoreSize;
        this.maxStoreSize.setValue(ModelStringUtil.formatIECByteSizeString(
                maxStoreSize == null
                        ? AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE
                        : maxStoreSize,
                true,
                ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
    }

    // A size, e.g. 10 GiB, or null if the text isn't one
    private static Long parseSize(final String text) {
        try {
            return ModelStringUtil.parseIECByteSizeString(text);
        } catch (final RuntimeException e) {
            return null;
        }
    }

    /**
     * Describe the max store size differently for a store that holds many parts, where the limit is
     * per part rather than the size of the whole store on disk.
     */
    public void setMaxStoreSizeHelpText(final String helpText) {
        maxStoreSizePanel.setHelpText(helpText);
    }

    public Boolean getSynchroniseMerge() {
        return synchroniseMerge.getValue()
                ? Boolean.TRUE
                : null;
    }

    public void setSynchroniseMerge(final Boolean synchroniseMerge) {
        this.synchroniseMerge.setValue(synchroniseMerge != null && synchroniseMerge);
    }

    public Boolean getOverwrite() {
        return overwrite.getValue()
                ? null
                : overwrite.getValue();
    }

    public void setOverwrite(final Boolean overwrite) {
        this.overwrite.setValue(overwrite == null || overwrite);
    }

    /**
     * Hide the write options for a store that cannot express them, i.e. one whose parts are not
     * transferred over HTTP and whose writes never overwrite an existing key.
     */
    public void setWriteOptionsVisible(final boolean visible) {
        this.writeOptionsVisible = visible;
        synchroniseMergePanel.setVisible(visible);
        overwritePanel.setVisible(visible);
    }

    private void updateStates() {
        // Whether the write options apply is independent of read only, which onReadOnlyChange() handles
        synchroniseMerge.setEnabled(writeOptionsVisible);
        overwrite.setEnabled(writeOptionsVisible);
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        maxStoreSize.setReadOnly(readOnly);
        synchroniseMerge.setReadOnly(readOnly);
        overwrite.setReadOnly(readOnly);
        updateStates();
    }

    private void onMaxStoreSize(final ValueChangeEvent<String> event) {
        changed();
    }

    private void onSynchroniseMerge() {
        changed();
    }

    private void onOverwrite() {
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


    interface Binder extends UiBinder<Widget, GeneralSettingsForm> {

    }
}
