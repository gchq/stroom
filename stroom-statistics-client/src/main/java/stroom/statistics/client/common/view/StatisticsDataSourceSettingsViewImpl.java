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

package stroom.statistics.client.common.view;

import java.util.List;

import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;

import stroom.cell.tickbox.shared.TickBoxState;
import stroom.item.client.ItemListBox;
import stroom.item.client.StringListBox;
import stroom.statistics.client.common.presenter.StatisticsDataSourceSettingsPresenter.StatisticsDataSourceSettingsView;
import stroom.statistics.client.common.presenter.StatisticsDataSourceSettingsUiHandlers;
import stroom.statistics.shared.common.EventStoreTimeIntervalEnum;
import stroom.statistics.shared.common.StatisticRollUpType;
import stroom.statistics.shared.StatisticStoreEntity;
import stroom.statistics.shared.StatisticType;
import stroom.widget.tickbox.client.view.TickBox;

public class StatisticsDataSourceSettingsViewImpl extends ViewWithUiHandlers<StatisticsDataSourceSettingsUiHandlers>
        implements StatisticsDataSourceSettingsView {
    public interface Binder extends UiBinder<Widget, StatisticsDataSourceSettingsViewImpl> {
    }

    private final Widget widget;

    @UiField
    TextArea description;
    @UiField
    StringListBox engineName;

    @UiField(provided = true)
    ItemListBox<StatisticType> statisticType;

    @UiField(provided = true)
    ItemListBox<EventStoreTimeIntervalEnum> precision;

    @UiField(provided = true)
    ItemListBox<StatisticRollUpType> rollUpType;

    @UiField(provided = true)
    TickBox enabled;

    @Inject
    public StatisticsDataSourceSettingsViewImpl(final Binder binder) {
        statisticType = new ItemListBox<StatisticType>();
        statisticType.addItem(StatisticType.COUNT);
        statisticType.addItem(StatisticType.VALUE);

        rollUpType = new ItemListBox<StatisticRollUpType>();
        rollUpType.addItem(StatisticRollUpType.NONE);
        rollUpType.addItem(StatisticRollUpType.ALL);
        rollUpType.addItem(StatisticRollUpType.CUSTOM);

        precision = new ItemListBox<EventStoreTimeIntervalEnum>();
        precision.addItem(EventStoreTimeIntervalEnum.SECOND);
        precision.addItem(EventStoreTimeIntervalEnum.MINUTE);
        precision.addItem(EventStoreTimeIntervalEnum.HOUR);
        precision.addItem(EventStoreTimeIntervalEnum.DAY);

        engineName = new StringListBox();
        engineName.addItem(StatisticStoreEntity.NOT_SET);

        enabled = new TickBox(TickBoxState.UNTICK, null);
        // default to not ticked so Stroom doesn't start recording stats while the
        // enity is being built up, i.e. fields
        // added.
        // enabled.setBooleanValue(Boolean.FALSE);

        widget = binder.createAndBindUi(this);

        // TODO need to implement validation on the precision field to ensure
        // the ms equivelent is one of the values
        // from EventStoreTimeItervalEnum

        statisticType.addSelectionHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChange();
            }
        });

        rollUpType.addSelectionHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChange();
            }
        });

        precision.addSelectionHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChange();
            }
        });

        engineName.addChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChange();
            }
        });

        enabled.addValueChangeHandler(event -> {
            if (getUiHandlers() != null) {
                getUiHandlers().onChange();
            }
        });
    }

    @Override
    public Widget asWidget() {
        return widget;
    }

    @Override
    public TextArea getDescription() {
        return description;
    }

    @Override
    public String getEngineName() {
        return engineName.getSelected();
    }

    @Override
    public TickBox getEnabled() {
        return enabled;
    }

    @Override
    public StatisticType getStatisticType() {
        return statisticType.getSelectedItem();
    }

    @Override
    public void setStatisticType(final StatisticType statisticType) {
        this.statisticType.setSelectedItem(statisticType);
    }

    @Override
    public void setEngineName(final String engine) {
        this.engineName.setSelected(engine);
    }

    @Override
    public void setEngineNames(final List<String> names) {
        this.engineName.addItems(names);
    }

    @Override
    public EventStoreTimeIntervalEnum getPrecision() {
        return precision.getSelectedItem();
    }

    @Override
    public void setPrecision(final EventStoreTimeIntervalEnum precision) {
        this.precision.setSelectedItem(precision);
    }

    @Override
    public StatisticRollUpType getRollUpType() {
        return rollUpType.getSelectedItem();
    }

    @Override
    public void setRollUpType(final StatisticRollUpType statisticRollUpType) {
        rollUpType.setSelectedItem(statisticRollUpType);
    }
}
