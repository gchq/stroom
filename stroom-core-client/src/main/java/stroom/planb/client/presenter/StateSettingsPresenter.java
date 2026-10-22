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

package stroom.planb.client.presenter;

import stroom.document.client.event.ChangeUiHandlers;
import stroom.planb.client.presenter.StateSettingsPresenter.StateSettingsView;
import stroom.planb.client.view.GeneralSettingsView;
import stroom.planb.client.view.HttpStoreSettingsView;
import stroom.planb.client.view.RetentionSettingsView;
import stroom.planb.client.view.StateKeySchemaSettingsView;
import stroom.planb.client.view.StateValueSchemaSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.StateSettings;
import stroom.widget.presenter.client.HasUiHandlers;
import stroom.widget.presenter.client.View;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;

public class StateSettingsPresenter
        extends AbstractPlanBSettingsPresenter<StateSettingsView> {

    @Inject
    public StateSettingsPresenter(
            final EventBus eventBus,
            final StateSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings) {
        if (settings instanceof final StateSettings stateSettings) {
            read(stateSettings);
        } else {
            read(new StateSettings.Builder().build());
        }
    }

    private void read(final StateSettings settings) {
        getView().setMaxStoreSize(settings.getMaxStoreSize());
        getView().setSynchroniseMerge(settings.getSynchroniseMerge());
        getView().setOverwrite(settings.getOverwrite());
        getView().setRetention(settings.getRetention());
        getView().setSnapshotSettings(settings.getSnapshotSettings());
        getView().setKeySchema(settings.getKeySchema());
        getView().setValueSchema(settings.getValueSchema());
    }

    public AbstractPlanBSettings write() {
        return new StateSettings.Builder()
                .maxStoreSize(getView().getMaxStoreSize())
                .synchroniseMerge(getView().getSynchroniseMerge())
                .overwrite(getView().getOverwrite())
                .retention(getView().getRetention())
                .snapshotSettings(getView().getSnapshotSettings())
                .keySchema(getView().getKeySchema())
                .valueSchema(getView().getValueSchema())
                .build();
    }


    public interface StateSettingsView extends
            View,
            GeneralSettingsView,
            RetentionSettingsView,
            HttpStoreSettingsView,
            StateKeySchemaSettingsView,
            StateValueSchemaSettingsView,
            HasUiHandlers<ChangeUiHandlers> {

        /// Shows whether the user may change what this shows.
        ///
        /// @param readOnly Whether it may not be changed.
        void onReadOnly(boolean readOnly);
    }
}
