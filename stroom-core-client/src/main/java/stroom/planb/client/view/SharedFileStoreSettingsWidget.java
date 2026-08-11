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

import stroom.item.client.SelectionBox;
import stroom.planb.shared.ArchivalGranularity;
import stroom.planb.shared.ArchivalSettings;
import stroom.planb.shared.SharedFileStoreSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.TextBox;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/**
 * The settings a Plan B store needs when it is backed by a shared file store: where the store lives,
 * how many shards it is partitioned into, and its archival policy.
 *
 * <p>Archival lives here rather than in a widget of its own because it is only meaningful for a shared
 * file store — archive shards are written under the shared path.
 *
 * <p>The path is mandatory. A store type that offers this widget has no other backing store to fall
 * back to, so there is nothing to switch off; an empty path is a save-blocking error rather than a
 * mode.
 */
public class SharedFileStoreSettingsWidget
        extends AbstractSettingsWidget
        implements SharedFileStoreSettingsView {

    private final Widget widget;

    @UiField
    TextBox sharedPath;
    @UiField
    ValueSpinner shardCount;
    @UiField
    ValueSpinner archivalCheckInterval;
    @UiField
    SelectionBox<TimeUnit> archivalCheckIntervalTimeUnit;
    @UiField
    ValueSpinner archivalAge;
    @UiField
    SelectionBox<TimeUnit> archivalTimeUnit;
    @UiField
    SelectionBox<ArchivalGranularity> archivalGranularity;

    private boolean readOnly;
    private boolean locked;

    @Inject
    public SharedFileStoreSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        shardCount.setMin(1);
        shardCount.setMax(999);
        shardCount.setValue(1);

        archivalAge.setMin(1);
        archivalAge.setMax(9999);
        archivalAge.setValue(12);

        archivalTimeUnit.addItem(TimeUnit.MINUTES);
        archivalTimeUnit.addItem(TimeUnit.HOURS);
        archivalTimeUnit.addItem(TimeUnit.DAYS);
        archivalTimeUnit.addItem(TimeUnit.WEEKS);
        archivalTimeUnit.addItem(TimeUnit.MONTHS);
        archivalTimeUnit.setValue(TimeUnit.HOURS);

        archivalCheckInterval.setMin(1);
        archivalCheckInterval.setMax(9999);
        archivalCheckInterval.setValue(1);

        archivalCheckIntervalTimeUnit.addItem(TimeUnit.MINUTES);
        archivalCheckIntervalTimeUnit.addItem(TimeUnit.HOURS);
        archivalCheckIntervalTimeUnit.addItem(TimeUnit.DAYS);
        archivalCheckIntervalTimeUnit.setValue(TimeUnit.HOURS);

        archivalGranularity.addItem(ArchivalGranularity.HOUR);
        archivalGranularity.addItem(ArchivalGranularity.DAY);
        archivalGranularity.addItem(ArchivalGranularity.WEEK);
        archivalGranularity.setValue(ArchivalGranularity.DAY);
    }

    @Override
    Widget asWidget() {
        return widget;
    }

    @Override
    public SharedFileStoreSettings getSharedFileStore() {
        return new SharedFileStoreSettings(
                shardCount.getIntValue(),
                sharedPath.getValue(),
                new ArchivalSettings.Builder()
                        .duration(SimpleDuration.builder()
                                .time(archivalAge.getValue())
                                .timeUnit(archivalTimeUnit.getValue())
                                .build())
                        .checkInterval(SimpleDuration.builder()
                                .time(archivalCheckInterval.getValue())
                                .timeUnit(archivalCheckIntervalTimeUnit.getValue())
                                .build())
                        .granularity(archivalGranularity.getValue())
                        .build());
    }

    @Override
    public void setSharedFileStore(final SharedFileStoreSettings settings) {
        final SharedFileStoreSettings sharedFileStore = settings != null
                ? settings
                : new SharedFileStoreSettings(1, null);
        sharedPath.setValue(sharedFileStore.getSharedPath() == null
                ? ""
                : sharedFileStore.getSharedPath());
        shardCount.setValue(Math.max(1, sharedFileStore.getShardCount()));
        setArchival(sharedFileStore.getArchival());
        updateStates();
    }

    private void setArchival(final ArchivalSettings archival) {
        final ArchivalSettings settings = archival != null
                ? archival
                : new ArchivalSettings.Builder().build();
        if (settings.getDuration() != null) {
            archivalAge.setValue(settings.getDuration().getTime());
            archivalTimeUnit.setValue(settings.getDuration().getTimeUnit());
        } else {
            archivalAge.setValue(12L);
            archivalTimeUnit.setValue(TimeUnit.HOURS);
        }
        if (settings.getCheckInterval() != null) {
            archivalCheckInterval.setValue(settings.getCheckInterval().getTime());
            archivalCheckIntervalTimeUnit.setValue(settings.getCheckInterval().getTimeUnit());
        } else {
            archivalCheckInterval.setValue(1L);
            archivalCheckIntervalTimeUnit.setValue(TimeUnit.HOURS);
        }
        archivalGranularity.setValue(settings.getGranularity() != null
                ? settings.getGranularity()
                : ArchivalGranularity.DAY);
    }

    @Override
    public void setSharedFileStoreLocked(final boolean locked) {
        this.locked = locked;
        updateStates();
    }

    // The path and shard count decide where written data already lives, so both are fixed once there is
    // any. Archival is only a policy over that data, so it stays editable.
    private void updateStates() {
        final boolean editable = !readOnly;
        sharedPath.setEnabled(editable && !locked);
        shardCount.setEnabled(editable && !locked);
        archivalCheckInterval.setEnabled(editable);
        archivalCheckIntervalTimeUnit.setEnabled(editable);
        archivalAge.setEnabled(editable);
        archivalTimeUnit.setEnabled(editable);
        archivalGranularity.setEnabled(editable);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        this.readOnly = readOnly;
        updateStates();
    }

    @UiHandler("sharedPath")
    public void onSharedPath(final ValueChangeEvent<String> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("shardCount")
    public void onShardCount(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalCheckInterval")
    public void onArchivalCheckInterval(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalCheckIntervalTimeUnit")
    public void onArchivalCheckIntervalTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalAge")
    public void onArchivalAge(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalTimeUnit")
    public void onArchivalTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("archivalGranularity")
    public void onArchivalGranularity(final ValueChangeEvent<ArchivalGranularity> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, SharedFileStoreSettingsWidget> {

    }
}
