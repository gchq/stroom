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
import stroom.planb.shared.HoldingAreaSettings;
import stroom.util.shared.time.SimpleDuration;
import stroom.util.shared.time.TimeUnit;
import stroom.widget.valuespinner.client.ValueSpinner;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;

/**
 * The two settings that decide when a store's data can be queried: how wide each bucket is, and how
 * long an incomplete record is kept open before it is published with whatever it has.
 *
 * <p>Both stay editable once data exists. Neither decides where existing data lives — a change only
 * affects records published from then on, and buckets already written are read back using the layout
 * recorded in their own directory names.
 */
public class PublishingSettingsWidget
        extends AbstractSettingsWidget
        implements PublishingSettingsView {

    private final Widget widget;

    @UiField
    SelectionBox<ArchivalGranularity> granularity;
    @UiField
    ValueSpinner completionGrace;
    @UiField
    SelectionBox<TimeUnit> completionGraceTimeUnit;

    @Inject
    public PublishingSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        granularity.addItem(ArchivalGranularity.HOUR);
        granularity.addItem(ArchivalGranularity.DAY);
        granularity.addItem(ArchivalGranularity.WEEK);
        granularity.setValue(ArchivalGranularity.DAY);

        completionGrace.setMin(1);
        completionGrace.setMax(9999);

        completionGraceTimeUnit.addItem(TimeUnit.MINUTES);
        completionGraceTimeUnit.addItem(TimeUnit.HOURS);
        completionGraceTimeUnit.addItem(TimeUnit.DAYS);
        completionGraceTimeUnit.addItem(TimeUnit.WEEKS);
        completionGraceTimeUnit.addItem(TimeUnit.MONTHS);

        setCompletionGrace(HoldingAreaSettings.DEFAULT_COMPLETION_GRACE);
    }

    @Override
    Widget asWidget() {
        return widget;
    }

    @Override
    public ArchivalGranularity getGranularity() {
        return granularity.getValue();
    }

    @Override
    public void setGranularity(final ArchivalGranularity granularity) {
        this.granularity.setValue(granularity == null
                ? ArchivalGranularity.DAY
                : granularity);
    }

    @Override
    public SimpleDuration getCompletionGrace() {
        return SimpleDuration.builder()
                .time(completionGrace.getValue())
                .timeUnit(completionGraceTimeUnit.getValue())
                .build();
    }

    @Override
    public void setCompletionGrace(final SimpleDuration completionGrace) {
        final SimpleDuration grace = completionGrace == null
                ? HoldingAreaSettings.DEFAULT_COMPLETION_GRACE
                : completionGrace;
        this.completionGrace.setValue(grace.getTime());
        this.completionGraceTimeUnit.setValue(grace.getTimeUnit());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        final boolean editable = !readOnly;
        granularity.setEnabled(editable);
        completionGrace.setEnabled(editable);
        completionGraceTimeUnit.setEnabled(editable);
    }

    @UiHandler("granularity")
    public void onGranularity(final ValueChangeEvent<ArchivalGranularity> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("completionGrace")
    public void onCompletionGrace(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("completionGraceTimeUnit")
    public void onCompletionGraceTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, PublishingSettingsWidget> {

    }
}
