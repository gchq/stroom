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

package stroom.floormap.client.view;

import stroom.document.client.event.DirtyUiHandlers;
import stroom.floormap.client.presenter.FloorMapEventStoreSettingsPresenter.FloorMapEventStoreSettingsView;
import stroom.item.client.SelectionBox;
import stroom.planb.client.view.CondenseSettingsWidget;
import stroom.planb.client.view.GeneralSettingsWidget;
import stroom.planb.client.view.RetentionSettingsWidget;
import stroom.planb.client.view.SettingsGroup;
import stroom.planb.shared.AbstractPlanBSettings;
import stroom.planb.shared.TemporalStateSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.FlowPanel;
import com.google.gwt.user.client.ui.Label;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

/// Composes the Plan B settings widgets this store needs, and adds the one setting that is ours.
///
/// Reuses [GeneralSettingsWidget], [CondenseSettingsWidget] and
/// [RetentionSettingsWidget] rather than reimplementing them, so a store's general settings look
/// and behave as they do everywhere else. The key and value schema widgets are simply not composed —
/// that is the whole mechanism by which they stop being editable here.
public class FloorMapEventStoreSettingsViewImpl
        extends ViewWithUiHandlers<DirtyUiHandlers>
        implements FloorMapEventStoreSettingsView {

    /// What turning condense on costs, in the one place a user can turn it on.
    ///
    /// Condense collapses a run of identical values to the earliest row of the run, so a
    /// stationary entity's latest effective time stops advancing and it expires while still
    /// emitting. That is a surprising interaction, and it is not visible from either setting alone.
    private static final String CONDENSE_WARNING =
            "This will cause repeating events to disappear from the map.";

    private final Widget widget;
    private final GeneralSettingsWidget generalSettingsWidget;

    /// The settings as read, so that writing back preserves what this tab does not show.
    ///
    /// A `TemporalStateSettings` carries more than the five fields edited here —
    /// `snapshotSettings` among them, which decides whether a query reads live data or a
    /// periodically refreshed copy. Building a fresh object from the widgets would reset every one of
    /// them on every save, silently.
    private TemporalStateSettings readSettings = new TemporalStateSettings.Builder().build();
    private final CondenseSettingsWidget condenseSettingsWidget;
    private final RetentionSettingsWidget retentionSettingsWidget;

    @UiField
    SettingsGroup generalPanel;
    @UiField
    SettingsGroup condensePanel;
    @UiField
    SettingsGroup retentionPanel;
    @UiField
    ValueSpinner expiryTime;
    @UiField
    SelectionBox<TimeUnit> expiryTimeUnit;

    @Inject
    public FloorMapEventStoreSettingsViewImpl(final Binder binder,
                                              final GeneralSettingsWidget generalSettingsWidget,
                                              final CondenseSettingsWidget condenseSettingsWidget,
                                              final RetentionSettingsWidget retentionSettingsWidget) {
        widget = binder.createAndBindUi(this);
        this.generalSettingsWidget = generalSettingsWidget;
        this.condenseSettingsWidget = condenseSettingsWidget;
        this.retentionSettingsWidget = retentionSettingsWidget;

        expiryTime.setMin(1);
        expiryTime.setMax(9999);
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

        generalPanel.add(generalSettingsWidget.asWidget());
        retentionPanel.add(retentionSettingsWidget.asWidget());

        // SettingsGroup holds one child, so the warning and the widget share a panel.
        final FlowPanel condenseContent = new FlowPanel();
        final Label warning = new Label(CONDENSE_WARNING);
        warning.addStyleName("floorMapEventStoreCondenseWarning");
        condenseContent.add(warning);
        condenseContent.add(condenseSettingsWidget.asWidget());
        condensePanel.add(condenseContent);
    }

    @Override
    public void setUiHandlers(final DirtyUiHandlers uiHandlers) {
        super.setUiHandlers(uiHandlers);
        generalSettingsWidget.setUiHandlers(uiHandlers::onDirty);
        condenseSettingsWidget.setUiHandlers(uiHandlers::onDirty);
        retentionSettingsWidget.setUiHandlers(uiHandlers::onDirty);
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public AbstractPlanBSettings getSettings() {
        // Built from what was read, so unedited fields survive; the schemas are deliberately absent
        // because the document applies its own on construction and would discard anything sent here.
        return new TemporalStateSettings.Builder(readSettings)
                .maxStoreSize(generalSettingsWidget.getMaxStoreSize())
                .synchroniseMerge(generalSettingsWidget.getSynchroniseMerge())
                .overwrite(generalSettingsWidget.getOverwrite())
                .condense(condenseSettingsWidget.getCondense())
                .retention(retentionSettingsWidget.getRetention())
                .build();
    }

    @Override
    public void setSettings(final AbstractPlanBSettings settings) {
        final TemporalStateSettings temporalStateSettings =
                settings instanceof final TemporalStateSettings existing
                        ? existing
                        : new TemporalStateSettings.Builder().build();
        readSettings = temporalStateSettings;
        generalSettingsWidget.setMaxStoreSize(temporalStateSettings.getMaxStoreSize());
        generalSettingsWidget.setSynchroniseMerge(temporalStateSettings.getSynchroniseMerge());
        generalSettingsWidget.setOverwrite(temporalStateSettings.getOverwrite());
        condenseSettingsWidget.setCondense(temporalStateSettings.getCondense());
        retentionSettingsWidget.setRetention(temporalStateSettings.getRetention());
    }

    @Override
    public SimpleDuration getEventExpiry() {
        return SimpleDuration
                .builder()
                .time(expiryTime.getValue())
                .timeUnit(expiryTimeUnit.getValue())
                .build();
    }

    @Override
    public void setEventExpiry(final SimpleDuration eventExpiry) {
        if (eventExpiry != null) {
            expiryTime.setValue(eventExpiry.getTime());
            if (eventExpiry.getTimeUnit() != null) {
                expiryTimeUnit.setValue(eventExpiry.getTimeUnit());
            }
        }
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        generalSettingsWidget.onReadOnly(readOnly);
        condenseSettingsWidget.onReadOnly(readOnly);
        retentionSettingsWidget.onReadOnly(readOnly);
        expiryTime.setEnabled(!readOnly);
        expiryTimeUnit.setEnabled(!readOnly);
    }

    @SuppressWarnings("unused")
    @UiHandler("expiryTime")
    public void onExpiryTime(final ValueChangeEvent<Long> event) {
        onDirty();
    }

    @SuppressWarnings("unused")
    @UiHandler("expiryTimeUnit")
    public void onExpiryTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        onDirty();
    }

    private void onDirty() {
        if (getUiHandlers() != null) {
            getUiHandlers().onDirty();
        }
    }

    public interface Binder extends UiBinder<Widget, FloorMapEventStoreSettingsViewImpl> {

    }
}
