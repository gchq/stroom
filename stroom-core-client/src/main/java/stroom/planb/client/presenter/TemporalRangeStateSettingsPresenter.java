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

package stroom.planb.client.presenter;

import stroom.entity.client.presenter.ReadOnlyChangeHandler;
import stroom.planb.client.presenter.TemporalRangeStateSettingsPresenter.TemporalRangeStateSettingsView;
import stroom.planb.client.view.CondenseSettingsView;
import stroom.planb.client.view.GeneralSettingsView;
import stroom.planb.client.view.RetentionSettingsView;
import stroom.planb.client.view.SnapshotSettingsView;
import stroom.planb.client.view.StateValueSchemaSettingsView;
import stroom.planb.client.view.TemporalRangeKeySchemaSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.TemporalRangeStateSettings;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class TemporalRangeStateSettingsPresenter
        extends AbstractPlanBSettingsPresenter<TemporalRangeStateSettingsView> {

    @Inject
    public TemporalRangeStateSettingsPresenter(
            final EventBus eventBus,
            final TemporalRangeStateSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final TemporalRangeStateSettings temporalRangeStateSettings) {
            read(temporalRangeStateSettings, readOnly);
        } else {
            read(new TemporalRangeStateSettings.Builder().build(), readOnly);
        }
    }

    private void read(final TemporalRangeStateSettings settings, final boolean readOnly) {
        setReadOnly(readOnly);
        getView().setMaxStoreSize(settings.getMaxStoreSize());
        getView().setSynchroniseMerge(settings.getSynchroniseMerge());
        getView().setOverwrite(settings.getOverwrite());
        getView().setCondense(settings.getCondense());
        getView().setRetention(settings.getRetention());
        getView().setSnapshotSettings(settings.getSnapshotSettings());
        getView().setKeySchema(settings.getKeySchema());
        getView().setValueSchema(settings.getValueSchema());
    }

    public AbstractPlanBSettings write() {
        return new TemporalRangeStateSettings.Builder()
                .maxStoreSize(getView().getMaxStoreSize())
                .synchroniseMerge(getView().getSynchroniseMerge())
                .overwrite(getView().getOverwrite())
                .condense(getView().getCondense())
                .retention(getView().getRetention())
                .snapshotSettings(getView().getSnapshotSettings())
                .keySchema(getView().getKeySchema())
                .valueSchema(getView().getValueSchema())
                .build();
    }

    public interface TemporalRangeStateSettingsView extends
            View,
            GeneralSettingsView,
            CondenseSettingsView,
            RetentionSettingsView,
            SnapshotSettingsView,
            TemporalRangeKeySchemaSettingsView,
            StateValueSchemaSettingsView,
            ReadOnlyChangeHandler,
            HasUiHandlers<PlanBSettingsUiHandlers> {

    }
}
