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
import stroom.planb.shared.HoldingAreaSettings;
import stroom.planb.shared.TraceSettings;
import stroom.util.shared.time.SimpleDuration;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// The settings of a traces store ([TraceSettings]), in the groups its template declares. A trace
/// store is only ever served from a shared file store, so it offers no snapshot or part-transfer
/// settings, nor the general settings' write options (see `AbstractHttpStoreSettings` for the
/// store types that have them).
public class TraceSettingsEditor extends PlanBTypeSettingsEditor {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    SharedFileStoreSettingsWidget sharedFileStore;
    @UiField
    PublishingSettingsWidget publishing;
    @UiField
    GeneralSettingsWidget general;
    @UiField
    TraceGeneralSettingsWidget traceGeneral;
    @UiField
    RetentionSettingsWidget retention;

    // Held from the most recent read so that write round-trips them. Neither has an editor: the
    // query range limit because nothing offers one yet, the compaction frequency because reclaiming
    // pages in the holding area is internal housekeeping an operator has no reason to set. Without
    // this a save would silently clear whatever was configured
    private SimpleDuration maxQueryTimeRange;
    private SimpleDuration compactionFrequency;

    /// Makes the settings, at their defaults.
    public TraceSettingsEditor() {
        initWidget(BINDER.createAndBindUi(this));
        sharedFileStore.setOnChange(this::changed);
        publishing.setOnChange(this::changed);
        general.setOnChange(this::changed);
        traceGeneral.setOnChange(this::changed);
        retention.setOnChange(this::changed);
    }

    @Override
    public void read(final AbstractPlanBSettings settings) {
        final TraceSettings traceSettings = settings instanceof final TraceSettings given
                ? given
                : new TraceSettings.Builder().build();
        maxQueryTimeRange = traceSettings.getMaxQueryTimeRange();
        compactionFrequency = traceSettings.getHoldingArea().getCompactionFrequency();
        general.setMaxStoreSize(traceSettings.getMaxStoreSize());
        traceGeneral.setMaxSpansPerTrace(traceSettings.getMaxSpansPerTrace());
        publishing.setGranularity(traceSettings.getGranularity());
        publishing.setMaxWaitForData(traceSettings.getHoldingArea().getMaxWaitForData());
        retention.setRetention(traceSettings.getRetention());
        sharedFileStore.setSharedFileStore(traceSettings.getSharedFileStore());
    }

    @Override
    public AbstractPlanBSettings write() {
        return new TraceSettings.Builder()
                .maxStoreSize(general.getMaxStoreSize())
                .maxSpansPerTrace(traceGeneral.getMaxSpansPerTrace())
                .granularity(publishing.getGranularity())
                .holdingArea(new HoldingAreaSettings.Builder()
                        .maxWaitForData(publishing.getMaxWaitForData())
                        .compactionFrequency(compactionFrequency)
                        .build())
                .retention(retention.getRetention())
                .sharedFileStore(sharedFileStore.getSharedFileStore())
                .maxQueryTimeRange(maxQueryTimeRange)
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
    public SharedFileStoreSettingsWidget getSharedFileStoreSettings() {
        return sharedFileStore;
    }

    // Neither write option reaches the shared file store, so the general settings don't show them
    @Override
    public boolean hasWriteOptions() {
        return false;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        sharedFileStore.setReadOnly(readOnly);
        publishing.setReadOnly(readOnly);
        general.setReadOnly(readOnly);
        traceGeneral.setReadOnly(readOnly);
        retention.setReadOnly(readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, TraceSettingsEditor> {

    }
}
