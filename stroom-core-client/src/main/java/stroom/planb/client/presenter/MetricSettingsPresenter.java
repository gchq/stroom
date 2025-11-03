/*
 * Copyright 2017-2024 Crown Copyright
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
import stroom.planb.client.presenter.MetricSettingsPresenter.MetricSettingsView;
import stroom.planb.client.view.GeneralSettingsView;
import stroom.planb.client.view.MetricKeySchemaSettingsView;
import stroom.planb.client.view.MetricValueSchemaSettingsView;
import stroom.planb.client.view.RetentionSettingsView;
import stroom.planb.client.view.SnapshotSettingsView;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.MetricSettings;

import com.google.inject.Inject;
import com.google.web.bindery.event.shared.EventBus;
import com.gwtplatform.mvp.client.HasUiHandlers;
import com.gwtplatform.mvp.client.View;

public class MetricSettingsPresenter
        extends AbstractPlanBSettingsPresenter<MetricSettingsView> {

    @Inject
    public MetricSettingsPresenter(
            final EventBus eventBus,
            final MetricSettingsView view) {
        super(eventBus, view);
        view.setUiHandlers(this);
    }

    public void read(final AbstractPlanBSettings settings, final boolean readOnly) {
        if (settings instanceof final MetricSettings metricSettings) {
            read(new MetricSettings.Builder(metricSettings).build(), readOnly);
        } else {
            read(new MetricSettings.Builder().build(), readOnly);
        }
    }

    private void read(final MetricSettings settings, final boolean readOnly) {
        setReadOnly(readOnly);
        getView().setMaxStoreSize(settings.getMaxStoreSize());
        getView().setSynchroniseMerge(settings.getSynchroniseMerge());
        getView().setOverwrite(settings.getOverwrite());
        getView().setRetention(settings.getRetention());
        getView().setSnapshotSettings(settings.getSnapshotSettings());
        getView().setKeySchema(settings.getKeySchema());
        getView().setValueSchema(settings.getValueSchema());
    }

    public AbstractPlanBSettings write() {
        return new MetricSettings.Builder()
                .maxStoreSize(getView().getMaxStoreSize())
                .synchroniseMerge(getView().getSynchroniseMerge())
                .overwrite(getView().getOverwrite())
                .retention(getView().getRetention())
                .snapshotSettings(getView().getSnapshotSettings())
                .keySchema(getView().getKeySchema())
                .valueSchema(getView().getValueSchema())
                .build();
    }

    public interface MetricSettingsView extends
            View,
            GeneralSettingsView,
            RetentionSettingsView,
            SnapshotSettingsView,
            MetricKeySchemaSettingsView,
            MetricValueSchemaSettingsView,
            ReadOnlyChangeHandler,
            HasUiHandlers<PlanBSettingsUiHandlers> {
    }
}
