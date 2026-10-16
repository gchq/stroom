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
import stroom.planb.shared.BucketGranularity;
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
 * The two settings that decide when a store's data can be queried: how wide each bucket is, and the
 * longest an incomplete record waits before it is published with whatever it has.
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
    SelectionBox<BucketGranularity> granularity;
    @UiField
    ValueSpinner maxWaitForData;
    @UiField
    SelectionBox<TimeUnit> maxWaitForDataTimeUnit;

    @Inject
    public PublishingSettingsWidget(final Binder binder) {
        widget = binder.createAndBindUi(this);

        granularity.addItem(BucketGranularity.HOUR);
        granularity.addItem(BucketGranularity.DAY);
        granularity.addItem(BucketGranularity.WEEK);
        granularity.setValue(BucketGranularity.DAY);

        maxWaitForData.setMin(1);
        maxWaitForData.setMax(9999);

        maxWaitForDataTimeUnit.addItem(TimeUnit.MINUTES);
        maxWaitForDataTimeUnit.addItem(TimeUnit.HOURS);
        maxWaitForDataTimeUnit.addItem(TimeUnit.DAYS);
        maxWaitForDataTimeUnit.addItem(TimeUnit.WEEKS);
        maxWaitForDataTimeUnit.addItem(TimeUnit.MONTHS);

        setMaxWaitForData(HoldingAreaSettings.DEFAULT_MAX_WAIT_FOR_DATA);
    }

    @Override
    Widget asWidget() {
        return widget;
    }

    @Override
    public BucketGranularity getGranularity() {
        return granularity.getValue();
    }

    @Override
    public void setGranularity(final BucketGranularity granularity) {
        this.granularity.setValue(granularity == null
                ? BucketGranularity.DAY
                : granularity);
    }

    @Override
    public SimpleDuration getMaxWaitForData() {
        return SimpleDuration.builder()
                .time(maxWaitForData.getValue())
                .timeUnit(maxWaitForDataTimeUnit.getValue())
                .build();
    }

    @Override
    public void setMaxWaitForData(final SimpleDuration maxWaitForData) {
        final SimpleDuration maxWait = maxWaitForData == null
                ? HoldingAreaSettings.DEFAULT_MAX_WAIT_FOR_DATA
                : maxWaitForData;
        this.maxWaitForData.setValue(maxWait.getTime());
        this.maxWaitForDataTimeUnit.setValue(maxWait.getTimeUnit());
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        final boolean editable = !readOnly;
        granularity.setEnabled(editable);
        maxWaitForData.setEnabled(editable);
        maxWaitForDataTimeUnit.setEnabled(editable);
    }

    @UiHandler("granularity")
    public void onGranularity(final ValueChangeEvent<BucketGranularity> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("maxWaitForData")
    public void onMaxWaitForData(final ValueChangeEvent<Long> event) {
        getUiHandlers().onChange();
    }

    @UiHandler("maxWaitForDataTimeUnit")
    public void onMaxWaitForDataTimeUnit(final ValueChangeEvent<TimeUnit> event) {
        getUiHandlers().onChange();
    }

    public interface Binder extends UiBinder<Widget, PublishingSettingsWidget> {

    }
}
