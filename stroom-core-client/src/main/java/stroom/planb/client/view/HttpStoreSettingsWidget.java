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

import stroom.planb.shared.SnapshotSettings;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/**
 * The settings of a store served over HTTP: whether reads come from snapshots pushed to other nodes,
 * and whether a write waits for the receiving node to merge the transferred part.
 *
 * <p>None of this applies to a store backed by a shared file store, which has no part transfer and no
 * snapshots — see {@link stroom.planb.shared.AbstractHttpStoreSettings}.
 */
public class HttpStoreSettingsWidget extends AbstractSettingsWidget implements HttpStoreSettingsView {

    private final Widget widget;

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

    private boolean readOnly;

    @Inject
    public HttpStoreSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);
        setOverwrite(true);
    }

    @Override
    Widget asWidget() {
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
            useSnapshotsForLookup.setValue(snapshotSettings.isUseSnapshotsForLookup());
            useSnapshotsForGet.setValue(snapshotSettings.isUseSnapshotsForGet());
            useSnapshotsForQuery.setValue(snapshotSettings.isUseSnapshotsForQuery());
        }
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

    private void updateStates() {
        final boolean enabled = !readOnly;
        synchroniseMerge.setEnabled(enabled);
        overwrite.setEnabled(enabled);
        useSnapshotsForLookup.setEnabled(enabled);
        useSnapshotsForGet.setEnabled(enabled);
        useSnapshotsForQuery.setEnabled(enabled);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
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

    public interface Binder extends UiBinder<Widget, HttpStoreSettingsWidget> {

    }
}
