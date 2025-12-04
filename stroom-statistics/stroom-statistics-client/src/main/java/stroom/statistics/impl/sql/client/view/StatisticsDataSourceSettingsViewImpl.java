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

package stroom.statistics.impl.sql.client.view;

import stroom.item.client.SelectionBox;
import stroom.statistics.impl.sql.client.presenter.StatisticsDataSourceSettingsPresenter.StatisticsDataSourceSettingsView;
import stroom.statistics.impl.sql.client.presenter.StatisticsDataSourceSettingsUiHandlers;
import stroom.statistics.impl.sql.shared.EventStoreTimeIntervalEnum;
import stroom.statistics.impl.sql.shared.StatisticRollUpType;
import stroom.statistics.impl.sql.shared.StatisticType;
import stroom.widget.tickbox.client.view.CustomCheckBox;

import com.google.gwt.event.logical.shared.ValueChangeEvent;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.uibinder.client.UiHandler;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

public class StatisticsDataSourceSettingsViewImpl extends ViewWithUiHandlers<StatisticsDataSourceSettingsUiHandlers>
        implements StatisticsDataSourceSettingsView {

    private final Widget widget;
    @UiField
    SelectionBox<StatisticType> statisticType;
    @UiField
    SelectionBox<EventStoreTimeIntervalEnum> precision;
    @UiField
    SelectionBox<StatisticRollUpType> rollUpType;
    @UiField
    CustomCheckBox enabled;

    @Inject
    public StatisticsDataSourceSettingsViewImpl(final Binder binder) {
        widget = binder.createAndBindUi(this);

        statisticType.addItem(StatisticType.COUNT);
        statisticType.addItem(StatisticType.VALUE);

        rollUpType.addItem(StatisticRollUpType.NONE);
        rollUpType.addItem(StatisticRollUpType.ALL);
        rollUpType.addItem(StatisticRollUpType.CUSTOM);

        precision.addItem(EventStoreTimeIntervalEnum.SECOND);
        precision.addItem(EventStoreTimeIntervalEnum.MINUTE);
        precision.addItem(EventStoreTimeIntervalEnum.HOUR);
        precision.addItem(EventStoreTimeIntervalEnum.DAY);

        // default to not ticked so Stroom doesn't start recording stats while the
        // enity is being built up, i.e. fields
        // added.
        // enabled.setBooleanValue(Boolean.FALSE);


        // TODO need to implement validation on the precision field to ensure
        // the ms equivelent is one of the values
        // from EventStoreTimeItervalEnum
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public CustomCheckBox getEnabled() {
        return enabled;
    }

    @Override
    public StatisticType getStatisticType() {
        return statisticType.getValue();
    }

    @Override
    public void setStatisticType(final StatisticType statisticType) {
        this.statisticType.setValue(statisticType);
    }

    @Override
    public EventStoreTimeIntervalEnum getPrecision() {
        return precision.getValue();
    }

    @Override
    public void setPrecision(final EventStoreTimeIntervalEnum precision) {
        this.precision.setValue(precision);
    }

    @Override
    public StatisticRollUpType getRollUpType() {
        return rollUpType.getValue();
    }

    @Override
    public void setRollUpType(final StatisticRollUpType statisticRollUpType) {
        rollUpType.setValue(statisticRollUpType);
    }

    @Override
    public void onReadOnly(final boolean readOnly) {
        statisticType.setEnabled(!readOnly);
        precision.setEnabled(!readOnly);
        rollUpType.setEnabled(!readOnly);
        enabled.setEnabled(!readOnly);
    }

    @UiHandler("statisticType")
    public void onStatisticTypeValueChange(final ValueChangeEvent<StatisticType> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("rollUpType")
    public void onRollUpTypeValueChange(final ValueChangeEvent<StatisticRollUpType> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("precision")
    public void onPrecisionValueChange(final ValueChangeEvent<EventStoreTimeIntervalEnum> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    @UiHandler("enabled")
    public void onEnabledValueChange(final ValueChangeEvent<Boolean> event) {
        if (getUiHandlers() != null) {
            getUiHandlers().onChange();
        }
    }

    public interface Binder extends UiBinder<Widget, StatisticsDataSourceSettingsViewImpl> {

    }
}
