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

package stroom.stats.client.view;

import com.google.gwt.event.logical.shared.SelectionEvent;
import com.google.gwt.event.logical.shared.SelectionHandler;
import com.google.gwt.uibinder.client.UiBinder;
import com.google.gwt.uibinder.client.UiField;
import com.google.gwt.user.client.ui.TextArea;
import com.google.gwt.user.client.ui.Widget;
import com.google.inject.Inject;
import com.gwtplatform.mvp.client.ViewWithUiHandlers;
import stroom.cell.tickbox.shared.TickBoxState;
import stroom.item.client.ItemListBox;
import stroom.statistics.shared.StatisticType;
import stroom.stats.client.presenter.StroomStatsStoreSettingsPresenter;
import stroom.stats.client.presenter.StroomStatsStoreSettingsUiHandlers;
import stroom.stats.shared.EventStoreTimeIntervalEnum;
import stroom.stats.shared.StatisticRollUpType;
import stroom.widget.tickbox.client.view.TickBox;

public class StroomStatsStoreSettingsViewImpl extends ViewWithUiHandlers<StroomStatsStoreSettingsUiHandlers>
        implements StroomStatsStoreSettingsPresenter.StroomStatsStoreSettingsView {
    private final Widget widget;
    @UiField
    TextArea description;
    @UiField(provided = true)
    ItemListBox<StatisticType> statisticType;
    @UiField(provided = true)
    ItemListBox<EventStoreTimeIntervalEnum> precision;
    @UiField(provided = true)
    ItemListBox<StatisticRollUpType> rollUpType;
    @UiField(provided = true)
    TickBox enabled;

    @Inject
    public StroomStatsStoreSettingsViewImpl(final Binder binder) {
        statisticType = new ItemListBox<>();
        for (StatisticType item : StatisticType.values()) {
            //assumes enum declaration order is sufficient
            statisticType.addItem(item);
        }

        rollUpType = new ItemListBox<>();
        for (StatisticRollUpType item : StatisticRollUpType.values()) {
            //assumes enum declaration order is sufficient
            rollUpType.addItem(item);
        }

        precision = new ItemListBox<>();
        for (EventStoreTimeIntervalEnum item : EventStoreTimeIntervalEnum.values()) {
            //assumes enum declaration order is sufficient
            precision.addItem(item);
        }

        enabled = new TickBox(TickBoxState.UNTICK, null);
        // default to not ticked so Stroom doesn't start recording stats while the
        // enity is being built up, i.e. fields
        // added.
        // enabled.setBooleanValue(Boolean.FALSE);

        widget = binder.createAndBindUi(this);

        // TODO need to implement validation on the precision field to ensure
        // the ms equivelent is one of the values
        // from EventStoreTimeItervalEnum

        statisticType.addSelectionHandler(new SelectionHandler<StatisticType>() {
            @Override
            public void onSelection(final SelectionEvent<StatisticType> event) {
                if (getUiHandlers() != null) {
                    getUiHandlers().onChange();
                }
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

    public interface Binder extends UiBinder<Widget, StroomStatsStoreSettingsViewImpl> {
    }
}
