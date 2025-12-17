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
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.util.shared.ModelStringUtil;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

public class GeneralSettingsWidget extends AbstractSettingsWidget implements GeneralSettingsView {

    private final Widget widget;

    @UiField
    TextBox maxStoreSize;
    @UiField
    CustomCheckBox synchroniseMerge;
    @UiField
    CustomCheckBox overwrite;

    @Inject
    public GeneralSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setOverwrite(true);
    }

    @Override
    public void setUiHandlers(final PlanBSettingsUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public Long getMaxStoreSize() {
        final String value = maxStoreSize.getValue();
        try {
            final String string = value.trim();
            if (!string.isEmpty()) {
                return ModelStringUtil.parseIECByteSizeString(string);
            }
        } catch (final RuntimeException e) {
            // Ignore.
        }
        setMaxStoreSize(AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE);
        return AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE;
    }

    @Override
    public void setMaxStoreSize(final Long maxStoreSize) {
        this.maxStoreSize.setValue(ModelStringUtil.formatIECByteSizeString(
                maxStoreSize == null
                        ? AbstractPlanBSettings.DEFAULT_MAX_STORE_SIZE
                        : maxStoreSize,
                true,
                ModelStringUtil.DEFAULT_SIGNIFICANT_FIGURES));
    }

    @Override
    public Boolean getSynchroniseMerge() {
        return synchroniseMerge.getValue()
                ? Boolean.TRUE
                : null;
    }

    @Override
    public void setSynchroniseMerge(final Boolean synchroniseMerge) {
        this.synchroniseMerge.setValue(synchroniseMerge != null && synchroniseMerge);
    }

    @Override
    public Boolean getOverwrite() {
        return overwrite.getValue()
                ? null
                : overwrite.getValue();
    }

    @Override
    public void setOverwrite(final Boolean overwrite) {
        this.overwrite.setValue(overwrite == null || overwrite);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        maxStoreSize.setEnabled(!readOnly);
        synchroniseMerge.setEnabled(!readOnly);
        overwrite.setEnabled(!readOnly);
    }

    @UiHandler("maxStoreSize")
    public void onMaxStoreSize(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("synchroniseMerge")
    public void onSynchroniseMerge(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("overwrite")
    public void onOverwrite(final ValueChangeEvent<Boolean> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, GeneralSettingsWidget> {

    }
}
