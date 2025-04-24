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

import stroom.planb.client.presenter.PlanBSettingsUiHandlers;
import stroom.planb.client.presenter.RangedStateSettingsPresenter.RangedStateSettingsView;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class RangedStateSettingsViewImpl
        extends ViewWithUiHandlers<PlanBSettingsUiHandlers>
        implements RangedStateSettingsView {

    private final Widget widget;

    @UiField
    TextBox maxStoreSize;
    @UiField
    CustomCheckBox synchroniseMerge;
    @UiField
    CustomCheckBox overwrite;

    @UiField
    CustomCheckBox useSnapshotsForLookup;
    @UiField
    CustomCheckBox useSnapshotsForGet;
    @UiField
    CustomCheckBox useSnapshotsForQuery;

    @Inject
    public RangedStateSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setOverwrite(true);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public String getMaxStoreSize() {
        return maxStoreSize.getValue();
    }

    @Override
    public void setMaxStoreSize(final String maxStoreSize) {
        this.maxStoreSize.setValue(maxStoreSize);
    }

    @Override
    public boolean getSynchroniseMerge() {
        return synchroniseMerge.getValue();
    }

    @Override
    public void setSynchroniseMerge(final boolean synchroniseMerge) {
        this.synchroniseMerge.setValue(synchroniseMerge);
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
    public boolean isUseSnapshotsForLookup() {
        return useSnapshotsForLookup.getValue();
    }

    @Override
    public void setUseSnapshotsForLookup(final boolean useSnapshotsForLookup) {
        this.useSnapshotsForLookup.setValue(useSnapshotsForLookup);
    }

    @Override
    public boolean isUseSnapshotsForGet() {
        return useSnapshotsForGet.getValue();
    }

    @Override
    public void setUseSnapshotsForGet(final boolean useSnapshotsForGet) {
        this.useSnapshotsForGet.setValue(useSnapshotsForGet);
    }

    @Override
    public boolean isUseSnapshotsForQuery() {
        return useSnapshotsForQuery.getValue();
    }

    @Override
    public void setUseSnapshotsForQuery(final boolean useSnapshotsForQuery) {
        this.useSnapshotsForQuery.setValue(useSnapshotsForQuery);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        maxStoreSize.setEnabled(!readOnly);
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

    public interface Binder extends UiBinder<Widget, RangedStateSettingsViewImpl> {

    }
}
