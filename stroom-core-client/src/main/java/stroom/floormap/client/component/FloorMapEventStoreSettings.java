/*
 * Copyright 2026 Crown Copyright
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

package stroom.floormap.client.component;

import stroom.floormap.shared.FloorMapEventStoreDoc;
import stroom.planb.client.component.CondenseSettingsForm;
import stroom.planb.client.component.GeneralSettingsForm;
import stroom.planb.client.component.RetentionSettingsForm;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.component.client.Component;
import stroom.widget.selection.client.SelectionBox;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.core.client.GWT;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.Widget;

/// A floor map event store's Settings tab: how long an entity stays on the map, and the Plan B
/// settings ([GeneralSettingsForm], [CondenseSettingsForm] and [RetentionSettingsForm]) a user
/// may safely change once data exists, so a store's general settings look and behave as they do
/// everywhere else. The key and value schema widgets are simply not declared: that is the whole
/// mechanism by which they stop being editable here.
public class FloorMapEventStoreSettings extends Component {

    private static final Binder BINDER = GWT.create(Binder.class);

    @UiField
    GeneralSettingsForm generalSettings;
    @UiField
    CondenseSettingsForm condenseSettings;
    @UiField
    RetentionSettingsForm retentionSettings;
    @UiField
    ValueSpinner expiryTime;
    @UiField
    SelectionBox<TimeUnit> expiryTimeUnit;

    private Runnable onChange = () -> {
    };

    /// The settings as read, so that writing back preserves what this tab does not show.
    ///
    /// A `TemporalStateSettings` carries more than the five fields edited here —
    /// `snapshotSettings` among them, which decides whether a query reads live data or a
    /// periodically refreshed copy. Building a fresh object from the widgets would reset every one of
    /// them on every save, silently.
    private TemporalStateSettings readSettings = new TemporalStateSettings.Builder().build();

    /// Makes an empty tab.
    public FloorMapEventStoreSettings() {
        initWidget(BINDER.createAndBindUi(this));

        expiryTime.setValue(24);
        // Every unit a SimpleDuration can carry, not just the plausible ones. A document arriving by
        // import or REST with WEEKS would otherwise display under whatever unit the box happened to
        // show and be saved back under that - silently changing what the setting means.
        expiryTimeUnit.addItem(TimeUnit.SECONDS);
        expiryTimeUnit.addItem(TimeUnit.MINUTES);
        expiryTimeUnit.addItem(TimeUnit.HOURS);
        expiryTimeUnit.addItem(TimeUnit.DAYS);
        expiryTimeUnit.addItem(TimeUnit.WEEKS);
        expiryTimeUnit.addItem(TimeUnit.MONTHS);
        expiryTimeUnit.addItem(TimeUnit.YEARS);
        expiryTimeUnit.setValue(TimeUnit.HOURS);

        generalSettings.setOnChange(() -> onChange.run());
        condenseSettings.setOnChange(() -> onChange.run());
        retentionSettings.setOnChange(() -> onChange.run());
        expiryTime.addValueChangeHandler(event -> onChange.run());
        expiryTimeUnit.addValueChangeHandler(event -> onChange.run());
    }

    /// Shows a store's settings.
    ///
    /// @param doc The store.
    public void read(final FloorMapEventStoreDoc doc) {
        final TemporalStateSettings temporalStateSettings =
                doc.getSettings() instanceof final TemporalStateSettings existing
                        ? existing
                        : new TemporalStateSettings.Builder().build();
        readSettings = temporalStateSettings;
        generalSettings.setMaxStoreSize(temporalStateSettings.getMaxStoreSize());
        generalSettings.setSynchroniseMerge(temporalStateSettings.getSynchroniseMerge());
        generalSettings.setOverwrite(temporalStateSettings.getOverwrite());
        condenseSettings.setCondense(temporalStateSettings.getCondense());
        retentionSettings.setRetention(temporalStateSettings.getRetention());

        // The resolved value, not the raw one: an unset document shows 24 hours rather than an
        // empty box, and saving then writes that 24 hours down explicitly. Adopting the default on
        // first save is the intended behaviour - the alternative is a control that displays a
        // number the document does not hold.
        final SimpleDuration eventExpiry = doc.getEventExpiryOrDefault();
        if (eventExpiry != null) {
            expiryTime.setValue(eventExpiry.getTime());
            if (eventExpiry.getTimeUnit() != null) {
                expiryTimeUnit.setValue(eventExpiry.getTimeUnit());
            }
        }
    }

    /// Writes the settings shown into a copy of a store.
    ///
    /// @param doc The store.
    /// @return A copy of it with the settings shown.
    public FloorMapEventStoreDoc write(final FloorMapEventStoreDoc doc) {
        // Built from what was read, so unedited fields survive; the schemas are deliberately absent
        // because the document applies its own on construction and would discard anything sent here.
        final TemporalStateSettings settings = new TemporalStateSettings.Builder(readSettings)
                .maxStoreSize(generalSettings.getMaxStoreSize())
                .synchroniseMerge(generalSettings.getSynchroniseMerge())
                .overwrite(generalSettings.getOverwrite())
                .condense(condenseSettings.getCondense())
                .retention(retentionSettings.getRetention())
                .build();
        return doc.copyEventStore()
                .settings(settings)
                .eventExpiry(SimpleDuration
                        .builder()
                        .time(expiryTime.getValue())
                        .timeUnit(expiryTimeUnit.getValue())
                        .build())
                .build();
    }

    /// @param onChange What to run when the user changes a setting.
    public void setOnChange(final Runnable onChange) {
        this.onChange = onChange;
    }

    @Override
    protected void onReadOnlyChange(final boolean readOnly) {
        generalSettings.setReadOnly(readOnly);
        condenseSettings.setReadOnly(readOnly);
        retentionSettings.setReadOnly(readOnly);
        expiryTime.setEnabled(!readOnly);
        expiryTimeUnit.setEnabled(!readOnly);
    }


    // --------------------------------------------------------------------------------


    interface Binder extends UiBinder<Widget, FloorMapEventStoreSettings> {

    }
}
