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

package stroom.planb.client.component;

import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.TemporalStateSettings;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The settings of a temporal state store ([TemporalStateSettings]), in the groups its template declares.
public class TemporalStateSettingsEditor extends PlanBTypeSettingsEditor {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    GeneralSettingsWidget general;
    @UiField
    SnapshotSettingsWidget snapshot;
    @UiField
    CondenseSettingsWidget condense;
    @UiField
    RetentionSettingsWidget retention;
    @UiField
    TemporalStateKeySchemaSettingsWidget keySchema;
    @UiField
    StateValueSchemaSettingsWidget valueSchema;

    /// Makes the settings, at their defaults.
    public TemporalStateSettingsEditor() {
        initWidget(BINDER.createAndBindUi(this));
        general.setOnChange(this::changed);
        snapshot.setOnChange(this::changed);
        condense.setOnChange(this::changed);
        retention.setOnChange(this::changed);
        keySchema.setOnChange(this::changed);
        valueSchema.setOnChange(this::changed);
    }

    @Override
    public void read(final AbstractPlanBSettings settings) {
        final TemporalStateSettings temporalStateSettings = settings instanceof final TemporalStateSettings given
                ? given
                : new TemporalStateSettings.Builder().build();
        general.setMaxStoreSize(temporalStateSettings.getMaxStoreSize());
        general.setSynchroniseMerge(temporalStateSettings.getSynchroniseMerge());
        general.setOverwrite(temporalStateSettings.getOverwrite());
        condense.setCondense(temporalStateSettings.getCondense());
        retention.setRetention(temporalStateSettings.getRetention());
        snapshot.setSnapshotSettings(temporalStateSettings.getSnapshotSettings());
        keySchema.setKeySchema(temporalStateSettings.getKeySchema());
        valueSchema.setValueSchema(temporalStateSettings.getValueSchema());
    }

    @Override
    public AbstractPlanBSettings write() {
        return new TemporalStateSettings.Builder()
                .maxStoreSize(general.getMaxStoreSize())
                .synchroniseMerge(general.getSynchroniseMerge())
                .overwrite(general.getOverwrite())
                .condense(condense.getCondense())
                .retention(retention.getRetention())
                .snapshotSettings(snapshot.getSnapshotSettings())
                .keySchema(keySchema.getKeySchema())
                .valueSchema(valueSchema.getValueSchema())
                .build();
    }

    @Override
    public GeneralSettingsWidget getGeneralSettings() {
        return general;
    }

    @Override
    public RetentionSettingsWidget getRetentionSettings() {
        return retention;
    }

    @Override
    public CondenseSettingsWidget getCondenseSettings() {
        return condense;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        general.setReadOnly(readOnly);
        snapshot.setReadOnly(readOnly);
        condense.setReadOnly(readOnly);
        retention.setReadOnly(readOnly);
        keySchema.setReadOnly(readOnly);
        valueSchema.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TemporalStateSettingsEditor> {

    }
}
