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
import stroom.planb.shared.TemporalRangeStateSettings;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The settings of a temporal ranged state store ([TemporalRangeStateSettings]), in the groups its template declares.
public class TemporalRangeStateSettingsEditor extends PlanBTypeSettingsEditor {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    GeneralSettingsForm general;
    @UiField
    SnapshotSettingsForm snapshot;
    @UiField
    CondenseSettingsForm condense;
    @UiField
    RetentionSettingsForm retention;
    @UiField
    TemporalRangeKeySchemaSettingsForm keySchema;
    @UiField
    StateValueSchemaSettingsForm valueSchema;

    /// Makes the settings, at their defaults.
    public TemporalRangeStateSettingsEditor() {
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
        final TemporalRangeStateSettings temporalRangeStateSettings =
                settings instanceof final TemporalRangeStateSettings given
                        ? given
                        : new TemporalRangeStateSettings.Builder().build();
        general.setMaxStoreSize(temporalRangeStateSettings.getMaxStoreSize());
        general.setSynchroniseMerge(temporalRangeStateSettings.getSynchroniseMerge());
        general.setOverwrite(temporalRangeStateSettings.getOverwrite());
        condense.setCondense(temporalRangeStateSettings.getCondense());
        retention.setRetention(temporalRangeStateSettings.getRetention());
        snapshot.setSnapshotSettings(temporalRangeStateSettings.getSnapshotSettings());
        keySchema.setKeySchema(temporalRangeStateSettings.getKeySchema());
        valueSchema.setValueSchema(temporalRangeStateSettings.getValueSchema());
    }

    @Override
    public AbstractPlanBSettings write() {
        return new TemporalRangeStateSettings.Builder()
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
    public GeneralSettingsForm getGeneralSettings() {
        return general;
    }

    @Override
    public RetentionSettingsForm getRetentionSettings() {
        return retention;
    }

    @Override
    public CondenseSettingsForm getCondenseSettings() {
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


    interface Binder extends UiBinder<Widget, TemporalRangeStateSettingsEditor> {

    }
}
